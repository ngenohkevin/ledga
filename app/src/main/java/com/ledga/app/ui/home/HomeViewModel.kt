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
import com.ledga.app.startup.SmsAccess
import com.ledga.app.time.LiveClock
import com.ledga.app.ui.activity.ActivityLink
import com.ledga.app.ui.activity.ActivityLinks
import com.ledga.app.ui.trackers.StoppedTracking
import com.ledga.app.ui.trackers.StoppedTrackers
import com.ledga.app.ui.onboarding.NotificationAccess
import com.ledga.app.ui.tx.TxText
import com.ledga.app.work.BackgroundWork
import com.ledga.app.work.HistoryProgress
import com.ledga.core.time.InstantRange
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Everything Home shows (spec §10.4). */
data class HomeUi(
    val loaded: Boolean = false,
    val greeting: String = "",
    /** You's optional name (4d sets it): the avatar, and the bold line under the greeting (R55). */
    val name: String? = null,
    val line: LineChoice = LineChoice(),
    val balance: HomeBalance? = null,
    /** "Personal ··11": the newest reading's line, only under All lines on a two-line phone (spec §10.4 "from <line>"). */
    val balanceFrom: String? = null,
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
    /** R59: Android 13+, onboarded, notifications not allowed, and the person hasn't said "Not now". */
    val notificationsNudge: Boolean = false,
    val notificationsToSettings: Boolean = false,
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
    private val stopped: StoppedTrackers,
) : ViewModel() {
    /** "Stopped tracking …" from a tracker opened on Home (R51). */
    val stoppedTracking: StateFlow<StoppedTracking?> = stopped.latest

    fun stoppedShown(note: StoppedTracking) = stopped.taken(note)

    fun undoStop(note: StoppedTracking) {
        viewModelScope.launch { stopped.undo(note) }
    }

    private val period = MutableStateFlow(PeriodType.MONTH)
    private val smsGranted = MutableStateFlow(sms.granted())
    private val smsBlocked = MutableStateFlow(false)
    private val notifyAsk = MutableStateFlow(notifications.shouldAsk())
    private val notifyBlocked = MutableStateFlow(false)

    /** When Home last came to the screen: the greeting's hour (R60). */
    private val resumedAt = MutableStateFlow(live.now())

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

    private data class Access(val smsGranted: Boolean, val smsBlocked: Boolean, val notifyAsk: Boolean, val notifyBlocked: Boolean)

    private data class Background(val history: HistoryProgress?, val legacyImportFailed: Boolean, val hasHistory: Boolean)

    /** Re-read whenever the day, the line or the segment changes: the running period's start comes from the clock then. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val content: Flow<Content> = combine(live.today, line.choice, period) { t, ch, p -> Frame(t, ch, p) }
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
        combine(smsGranted, smsBlocked, notifyAsk, notifyBlocked) { a, b, c, d -> Access(a, b, c, d) },
        settings.settings,
        combine(work.history, work.legacyImportFailed, db.transactionsDao().observeSpan()) { h, failed, span -> Background(h, failed, span.count > 0) },
        resumedAt,
    ) { c, a, s, bg, at ->
        val choice = c.frame.line
        HomeUi(
            loaded = true,
            greeting = HomeText.greeting(at),
            name = s.displayName,
            line = choice,
            balance = c.balance,
            balanceFrom = c.balance?.fromLineId
                ?.takeIf { choice.showChip && choice.lineId == null }
                ?.let { id -> choice.lines.firstOrNull { it.id == id } }
                ?.let(TxText::lineLabel),
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
            notificationsNudge = a.notifyAsk && s.onboarded && !s.notificationNudgeDismissed,
            notificationsToSettings = a.notifyBlocked,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

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
        resumedAt.value = live.now()
    }

    fun onSmsGranted() = refresh()

    /** Android declined. With no rationale to show it won't ask again, so the next tap opens Settings (4a M3). */
    fun onSmsDenied(showRationale: Boolean) {
        smsBlocked.value = !showRationale
    }

    fun onNotificationsResult(granted: Boolean, showRationale: Boolean) {
        notifyAsk.value = notifications.shouldAsk()
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
        return combine(
            ledger.totals(Periods.liveRange(periods.last(), now), lineId),
            ledger.spent(Periods.comparisonWindow(type, now), lineId),
            ledger.spentByPeriod(type, InstantRange(periods.first().startInstant, null), lineId),
        ) { totals, before, sums -> HomeSpending.card(type, now, totals, before, sums) }
    }
}
