package com.ledga.app.ui.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.BuildConfig
import com.ledga.app.data.backup.BackupStatus
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.LineRow
import com.ledga.app.data.room.TxSpan
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.data.update.UpdateService
import com.ledga.app.startup.SmsAccess
import com.ledga.app.ui.activity.ActivityLink
import com.ledga.app.ui.activity.ActivityLinks
import com.ledga.app.ui.backup.BackupText
import com.ledga.app.ui.onboarding.NotificationAccess
import com.ledga.app.ui.update.UpdateText
import com.ledga.app.work.BackgroundWork
import com.ledga.app.work.ImportProgress
import com.ledga.core.time.Periods
import com.ledga.core.update.UpdateChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** You's "Rescan SMS inbox" row (R77). */
sealed interface RescanState {
    data object Idle : RescanState

    data class Running(val done: Int, val total: Int) : RescanState

    data class Done(val added: Int) : RescanState

    data object Failed : RescanState
}

/** Everything You shows (spec §10.4, mockup `you`). */
data class YouUi(
    val loaded: Boolean = false,
    val name: String? = null,
    /** Payments that show, and the Nairobi day of the first. */
    val payments: Int = 0,
    val since: LocalDate? = null,
    val lines: List<LineRow> = emptyList(),
    val notifications: String = "",
    val appearance: String = "",
    val unreadable: Int = 0,
    val rescan: RescanState = RescanState.Idle,
    val smsGranted: Boolean = true,
    /** Android won't show the SMS dialog again: Rescan's ask opens Settings (4a M3). */
    val smsToSettings: Boolean = false,
    val version: String = "",
    /** R125: "Snapshot saved today, 8:12 PM" / "No snapshot yet". */
    val backup: String = "",
    /** R149: "v2.0.0-beta.1 · up to date", "2.0.1 available"… */
    val updates: String = "",
    /** R149: the Updates row's BETA badge. */
    val beta: Boolean = false,
)

/** You (spec §10.4). 5b added Export & restore and Android backup (R125); Phase 6 added Updates and Version history (R149). */
@HiltViewModel
class YouViewModel @Inject constructor(
    private val settings: SettingsStore,
    db: LedgaDatabase,
    lines: LinesRepository,
    private val work: BackgroundWork,
    private val sms: SmsAccess,
    private val notifications: NotificationAccess,
    private val links: ActivityLinks,
    private val backup: BackupStatus,
    private val clock: Clock,
    private val updates: UpdateService,
) : ViewModel() {
    private val smsGranted = MutableStateFlow(sms.granted())
    private val smsBlocked = MutableStateFlow(false)
    private val notifyAllowed = MutableStateFlow(notifications.enabled())
    private val rescan = MutableStateFlow<RescanState>(RescanState.Idle)
    private val savedAt = MutableStateFlow(backup.savedAt())

    /** R77: a result shows only for a rescan started here, once it has been seen running. */
    private var startedHere = false
    private var sawRunning = false

    init {
        viewModelScope.launch {
            work.inboxImport.collect { p ->
                if (p is ImportProgress.Running) sawRunning = true
                val ours = startedHere && sawRunning
                rescan.value = when (p) {
                    is ImportProgress.Running -> RescanState.Running(p.done, p.total)
                    is ImportProgress.Done -> if (ours) RescanState.Done(p.inserted) else RescanState.Idle
                    ImportProgress.Failed -> if (ours) RescanState.Failed else RescanState.Idle
                    ImportProgress.Idle -> RescanState.Idle
                }
            }
        }
    }

    private data class Counts(val span: TxSpan, val unreadable: Int)

    private data class Access(val sms: Boolean, val smsBlocked: Boolean, val notify: Boolean, val savedAt: Instant?)

    val ui: StateFlow<YouUi> = combine(
        settings.settings,
        lines.observe(),
        combine(db.transactionsDao().observeSpan(), db.smsDao().observeUnreadableCount()) { span, unreadable ->
            Counts(span, unreadable)
        },
        combine(rescan, updates.state) { r, u -> r to u },
        combine(smsGranted, smsBlocked, notifyAllowed, savedAt) { a, b, c, d -> Access(a, b, c, d) },
    ) { s, ls, counts, (r, u), access ->
        YouUi(
            loaded = true,
            name = s.displayName,
            payments = counts.span.count,
            since = counts.span.firstAt?.let(Periods::dateOf),
            lines = ls,
            notifications = NotificationText.summary(s, access.notify),
            appearance = YouText.appearanceLine(s.appearance, s.textSize),
            unreadable = counts.unreadable,
            rescan = r,
            smsGranted = access.sms,
            smsToSettings = access.smsBlocked,
            version = BuildConfig.VERSION_NAME,
            backup = BackupText.savedLine(access.savedAt, Periods.dateOf(clock.instant())),
            updates = UpdateText.youLine(u),
            beta = u.channel == UpdateChannel.BETA,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YouUi())

    /** On resume: access may have changed in Android's settings. */
    fun refresh() {
        smsGranted.value = sms.granted()
        if (smsGranted.value) smsBlocked.value = false
        notifyAllowed.value = notifications.enabled()
        savedAt.value = backup.savedAt()
    }

    /** After the SMS dialog: granted starts the rescan the person asked for. */
    fun onSmsResult(granted: Boolean, showRationale: Boolean) {
        refresh()
        if (granted) rescan() else smsBlocked.value = !showRationale
    }

    /** R77: false without SMS access (the screen asks for it); otherwise the whole inbox, once (the job is unique). */
    fun rescan(): Boolean {
        if (!sms.granted()) return false
        startedHere = true
        sawRunning = false
        work.importInbox()
        return true
    }

    /** R81: trimmed, spaces collapsed, cut to [NAME_MAX]; blank clears it. */
    fun setName(name: String) {
        viewModelScope.launch { settings.setDisplayName(name.replace(WS, " ").trim().take(NAME_MAX)) }
    }

    /** R82. */
    fun openPeople() = links.open(ActivityLink.People)

    companion object {
        const val NAME_MAX = 30
        private val WS = Regex("\\s+")
    }
}
