package com.ledga.app.ui.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.BuildConfig
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.LineRow
import com.ledga.app.data.room.TxSpan
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.startup.SmsAccess
import com.ledga.app.ui.activity.ActivityLink
import com.ledga.app.ui.activity.ActivityLinks
import com.ledga.app.ui.onboarding.NotificationAccess
import com.ledga.app.work.BackgroundWork
import com.ledga.app.work.ImportProgress
import com.ledga.core.time.Periods
import dagger.hilt.android.lifecycle.HiltViewModel
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
)

/** You (spec §10.4). Phase 5 adds Export & restore and Android backup, Phase 6 Updates and Version history (R66). */
@HiltViewModel
class YouViewModel @Inject constructor(
    private val settings: SettingsStore,
    db: LedgaDatabase,
    lines: LinesRepository,
    private val work: BackgroundWork,
    private val sms: SmsAccess,
    private val notifications: NotificationAccess,
    private val links: ActivityLinks,
) : ViewModel() {
    private val smsGranted = MutableStateFlow(sms.granted())
    private val smsBlocked = MutableStateFlow(false)
    private val notifyAllowed = MutableStateFlow(!notifications.shouldAsk())
    private val rescan = MutableStateFlow<RescanState>(RescanState.Idle)

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

    private data class Access(val sms: Boolean, val smsBlocked: Boolean, val notify: Boolean)

    val ui: StateFlow<YouUi> = combine(
        settings.settings,
        lines.observe(),
        combine(db.transactionsDao().observeSpan(), db.smsDao().observeUnreadableCount()) { span, unreadable ->
            Counts(span, unreadable)
        },
        rescan,
        combine(smsGranted, smsBlocked, notifyAllowed) { a, b, c -> Access(a, b, c) },
    ) { s, ls, counts, r, access ->
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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YouUi())

    /** On resume: access may have changed in Android's settings. */
    fun refresh() {
        smsGranted.value = sms.granted()
        if (smsGranted.value) smsBlocked.value = false
        notifyAllowed.value = !notifications.shouldAsk()
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
