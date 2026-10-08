package com.ledga.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.derive.FulizaStatus
import com.ledga.app.data.derive.HomeBalance
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.lines.LineChoice
import com.ledga.app.data.lines.SelectedLine
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.TxRow
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.data.trackers.TrackerSummary
import com.ledga.app.data.trackers.Trackers
import com.ledga.app.data.update.InstallStart
import com.ledga.app.data.update.UpdateService
import com.ledga.app.data.update.WhatsNew
import com.ledga.app.startup.SmsAccess
import com.ledga.app.time.LiveClock
import com.ledga.app.ui.activity.ActivityLink
import com.ledga.app.ui.activity.ActivityLinks
import com.ledga.app.ui.onboarding.NotificationAccess
import com.ledga.app.ui.tx.TxText
import com.ledga.app.work.BackgroundWork
import com.ledga.app.work.HistoryProgress
import com.ledga.core.time.InstantRange
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
import com.ledga.core.update.NotesSection
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One line's balance under the All lines total: "Personal ··11 · Ksh 3,175.57 · 7:42 PM". */
data class BalanceLine(val label: String, val cents: Long, val at: Instant)

/** Everything Home shows (spec §10.4). */
data class HomeUi(
    val loaded: Boolean = false,
    val greeting: String = "",
    /** You's optional name (4d sets it): the avatar, and the bold line under the greeting (R55). */
    val name: String? = null,
    val line: LineChoice = LineChoice(),
    val balance: HomeBalance? = null,
    /**
     * Under All lines on a phone with two or more lines: each line's part of the total, in the lines' order (owner,
     * 2026-10-06; it replaces spec §10.4's "from <line>", which read as if the total were that line's).
     */
    val balanceLines: List<BalanceLine> = emptyList(),
    val fuliza: FulizaStatus? = null,
    val spending: SpendingCardUi = SpendingCardUi(),
    val trackers: List<TrackerSummary> = emptyList(),
    val recent: List<TxRow> = emptyList(),
    val categories: Map<String, CategoryRow> = emptyMap(),
    val today: LocalDate? = null,
    /** False on a phone with no payments at all yet. */
    val hasHistory: Boolean = false,
    val smsGranted: Boolean = true,
    /** Android won't show the SMS dialog again: the next "Allow" opens Settings (4a M3). */
    val smsToSettings: Boolean = false,
    val history: HistoryProgress? = null,
    val legacyImportFailed: Boolean = false,
    /** R59, R109: onboarded, Android won't let Ledga post (any version), and the person hasn't said "Not now". */
    val notificationsNudge: Boolean = false,
    /** R109: "Turn on" opens Android's notification settings (no dialog can ask). */
    val notificationsToSettings: Boolean = false,
    /** R71: alerts not yet read (the bell's badge). */
    val unreadAlerts: Int = 0,
    /** R148: the update banner; null when there is none to show. */
    val update: HomeUpdate? = null,
)

/** Home (spec §10.4): each card reads the ledger on the chosen line (R47) and moves on with the clock (spec §7.6). */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val ledger: LedgerQueries,
    private val db: LedgaDatabase,
    private val trackers: Trackers,
    private val line: SelectedLine,
    private val live: LiveClock,
    private val work: BackgroundWork,
    private val sms: SmsAccess,
    private val notifications: NotificationAccess,
    private val settings: SettingsStore,
    private val edits: TransactionEdits,
    private val links: ActivityLinks,
    private val home: HomeLinks,
    private val updates: UpdateService,
    private val notes: WhatsNew,
) : ViewModel() {

    private val period = MutableStateFlow(PeriodType.MONTH)
    private val smsGranted = MutableStateFlow(sms.granted())
    private val smsBlocked = MutableStateFlow(false)
    private val notifyAsk = MutableStateFlow(notifications.shouldAsk())
    private val notifyBlocked = MutableStateFlow(false)
    private val notifyEnabled = MutableStateFlow(notifications.enabled())

    /** When Home last came to the screen: the greeting's hour (R60). */
    private val resumedAt = MutableStateFlow(live.now())

    /** One timer for Home: the date and the greeting both move on from it, so a greeting changes while Home stays open. */
    private val hours = live.hours.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    private val today = hours.map { Periods.dateOf(it) }.distinctUntilChanged()

    private data class Frame(val today: LocalDate, val line: LineChoice, val type: PeriodType)

    private data class Content(
        val frame: Frame,
        val balance: HomeBalance?,
        val fuliza: FulizaStatus?,
        val spending: SpendingCardUi,
        val trackers: List<TrackerSummary>,
        val recent: List<TxRow>,
        val categories: Map<String, CategoryRow>,
    )

    private data class Access(
        val smsGranted: Boolean,
        val smsBlocked: Boolean,
        val notifyAsk: Boolean,
        val notifyBlocked: Boolean,
        val notifyEnabled: Boolean,
    )

    private data class Background(
        val history: HistoryProgress?,
        val legacyImportFailed: Boolean,
        val hasHistory: Boolean,
        val unreadAlerts: Int,
        val update: HomeUpdate?,
    )

    /** Re-read whenever the day, the line or the segment changes: the running period's start comes from the clock then. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val content: Flow<Content> = combine(today, line.choice, period) { t, ch, p -> Frame(t, ch, p) }
        .distinctUntilChanged()
        .flatMapLatest { f ->
            val now = live.now()
            val lineId = f.line.lineId
            combine(
                combine(ledger.balances(), ledger.fulizaReadings()) { b, fz -> HomeBalance.of(b, lineId) to FulizaStatus.forLine(fz, lineId) },
                spendingCard(f.type, lineId, now),
                trackers.summaries(lineId, now),
                ledger.recent(lineId),
                db.categoriesDao().observeAll(),
            ) { (balance, fuliza), card, tracked, recent, cats ->
                Content(f, balance, fuliza, card, tracked, recent, cats.associateBy { it.key })
            }
        }

    val ui: StateFlow<HomeUi> = combine(
        content,
        combine(smsGranted, smsBlocked, notifyAsk, notifyBlocked, notifyEnabled) { a, b, c, d, e -> Access(a, b, c, d, e) },
        settings.settings,
        combine(work.history, work.legacyImportFailed, db.transactionsDao().observeSpan(), db.alertsDao().observeUnread(), updates.state) { h, failed, span, unread, u ->
            Background(h, failed, span.count > 0, unread, HomeUpdate.of(u))
        },
        merge(hours, resumedAt),
    ) { c, a, s, bg, at ->
        val choice = c.frame.line
        HomeUi(
            loaded = true,
            greeting = HomeText.greeting(at),
            name = s.displayName,
            line = choice,
            balance = c.balance,
            balanceLines = balanceLines(c.balance, choice),
            fuliza = c.fuliza,
            spending = c.spending,
            trackers = c.trackers,
            recent = c.recent,
            categories = c.categories,
            today = c.frame.today,
            hasHistory = bg.hasHistory,
            smsGranted = a.smsGranted,
            smsToSettings = a.smsBlocked,
            history = bg.history,
            legacyImportFailed = bg.legacyImportFailed,
            notificationsNudge = !a.notifyEnabled && s.onboarded && !s.notificationNudgeDismissed,
            notificationsToSettings = !a.notifyEnabled && (a.notifyBlocked || !a.notifyAsk),
            unreadAlerts = bg.unreadAlerts,
            update = bg.update,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    /** R141: this build's notes until seen. */
    val whatsNew: StateFlow<List<NotesSection>?> = notes.pending.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val whatsNewVersion: String = notes.version

    fun whatsNewSeen(): Job = viewModelScope.launch { notes.seen() }

    /** R148: the banner's Update: a person's download, on any network (R136). */
    fun downloadUpdate(): Job = viewModelScope.launch { updates.download() }

    /** R148: the banner's Install; [needsPermission] opens Updates, which explains Android's switch (R138). */
    fun installUpdate(needsPermission: () -> Unit): Job = viewModelScope.launch {
        if (updates.install() == InstallStart.NEEDS_PERMISSION) needsPermission()
    }

    /** Spec §13.4: Later hides the banner for three days. */
    fun updateLater(): Job = viewModelScope.launch { updates.snooze() }

    /** R100: a Fuliza reminder's tap asks for the Fuliza sheet; `HomeRoute` takes it while Home is shown. */
    val fulizaAsked: StateFlow<FulizaRequest?> = home.fulizaAsked

    /**
     * The sheet is opening for [request]. When Home shows a different single line, it moves to the reminder's line first
     * (owner 2026-10-07), as if the person had picked it; All lines already includes it and stays.
     */
    fun fulizaShown(request: FulizaRequest) {
        home.fulizaShown()
        val lineId = request.lineId ?: return
        viewModelScope.launch {
            val choice = line.choice.first()
            if (choice.lineId != null && choice.lineId != lineId && choice.lines.any { it.id == lineId }) line.select(lineId)
        }
    }

    /**
     * On resume: access may have changed in Settings, and the greeting re-reads the clock (R60). SMS access that has
     * just arrived, from the dialog or from Settings, imports the whole inbox (once: the job is unique).
     */
    fun refresh() {
        val granted = sms.granted()
        if (granted && !smsGranted.value) work.importInbox()
        smsGranted.value = granted
        if (granted) smsBlocked.value = false
        notifyAsk.value = notifications.shouldAsk()
        notifyEnabled.value = notifications.enabled()
        resumedAt.value = live.now()
    }

    fun onSmsGranted() = refresh()

    /** Android declined. With no rationale to show it won't ask again, so the next tap opens Settings (4a M3). */
    fun onSmsDenied(showRationale: Boolean) {
        smsBlocked.value = !showRationale
    }

    fun onNotificationsResult(granted: Boolean, showRationale: Boolean) {
        notifyAsk.value = notifications.shouldAsk()
        notifyEnabled.value = notifications.enabled()
        if (!granted) notifyBlocked.value = !showRationale
    }

    /** R59: "Not now" hides the banner for good; You → Notifications (4d) keeps the explanation. */
    fun dismissNotifications() {
        viewModelScope.launch { settings.dismissNotificationNudge() }
    }

    fun setPeriod(type: PeriodType) {
        period.value = type
    }

    /** R47: the balance card's chip changes the one choice every summary follows. */
    fun selectLine(id: Long?) {
        viewModelScope.launch { line.select(id) }
    }

    /** The snackbar's Undo after Hide (spec §10.4). */
    fun undoHide(code: String): Job = viewModelScope.launch { edits.setHidden(code, false) }

    /** R61: the spending card opens Activity › Spending. */
    fun openSpending() = links.open(ActivityLink.Spending)

    /** R61: Recent's "See all" opens Transactions on the line Home shows. */
    fun openRecent() = links.open(ActivityLink.Transactions(TransactionFilter(lineId = ui.value.line.lineId)))

    /** R61: the search button opens Transactions with every filter cleared and the cursor in the search field. */
    fun openSearch() = links.open(ActivityLink.Transactions(focusSearch = true))

    private fun spendingCard(type: PeriodType, lineId: Long?, now: Instant): Flow<SpendingCardUi> {
        val periods = Periods.lastN(type, now, HomeSpending.BARS)
        val span = InstantRange(periods.first().startInstant, null)
        val totals = ledger.totals(Periods.liveRange(periods.last(), now), lineId)
        val before = ledger.spent(Periods.comparisonWindow(type, now), lineId)
        val sums = ledger.spentByPeriod(type, span, lineId)
        // Year's average needs the months too: a first year counts only the months since the first payment.
        if (type != PeriodType.YEAR) return combine(totals, before, sums) { t, b, s -> HomeSpending.card(type, now, t, b, s) }
        return combine(totals, before, sums, ledger.spentByPeriod(PeriodType.MONTH, span, lineId)) { t, b, s, m ->
            HomeSpending.card(type, now, t, b, s, m)
        }
    }
}

/** Each line's part of the All lines total, labelled and in the lines' order; empty with a line chosen or one line. */
private fun balanceLines(balance: HomeBalance?, choice: LineChoice): List<BalanceLine> {
    if (balance == null || !choice.showChip || choice.lineId != null) return emptyList()
    val parts = balance.lines.associateBy { it.lineId }
    return choice.lines.mapNotNull { line -> parts[line.id]?.let { BalanceLine(TxText.lineLabel(line), it.cents, it.updatedAt) } }
        .takeIf { it.size >= 2 }
        .orEmpty()
}
