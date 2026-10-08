package com.ledga.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.backup.BackupFileError
import com.ledga.app.data.backup.BackupFiles
import com.ledga.app.data.backup.LineQuestion
import com.ledga.app.data.backup.RestoreMode
import com.ledga.app.data.backup.Restorer
import com.ledga.app.data.backup.Snapshots
import com.ledga.app.data.capture.InboxSource
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.lines.PhoneAccess
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.data.update.WhatsNew
import com.ledga.app.work.BackgroundWork
import com.ledga.app.work.ImportProgress
import com.ledga.app.work.RestoreProgress
import com.ledga.app.work.RestoreRequest
import com.ledga.app.work.UpdateWork
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Step { WELCOME, SMS, IMPORT, NOTIFICATIONS }

/** What the inbox holds, shown before anything is imported (spec §10.4 step 3). */
data class InboxPreview(val count: Int, val oldest: Instant?, val newest: Instant?)

/** R126: the backup a fresh install found, offered before the import. [answers]: line id → SIM, null = its own line. */
data class RestoreOffer(
    val file: File,
    val writtenAt: Instant,
    val payments: Int,
    val questions: List<LineQuestion> = emptyList(),
    val answers: Map<Long, Int?> = emptyMap(),
    val simsUnreadable: Boolean = false,
) {
    val ready: Boolean get() = questions.all { it.line.id in answers }
}

/** A detected line to name (R127: on the import's end): [label] says which SIM it is, [name] is what the user types. */
data class LineName(val id: Long, val label: String, val name: String)

data class OnboardingState(
    val step: Step = Step.WELCOME,
    /** The steps this phone will see, fixed when onboarding starts (R127). */
    val steps: List<Step> = listOf(Step.WELCOME, Step.SMS, Step.IMPORT),
    val name: String = "",
    val preview: InboxPreview? = null,
    val import: ImportProgress = ImportProgress.Idle,
    val lines: List<LineName> = emptyList(),
    val finished: Boolean = false,
    val offer: RestoreOffer? = null,
    val restore: RestoreProgress = RestoreProgress.Idle,
    /** The backup came back; the inbox import follows. */
    val restored: Boolean = false,
    val phoneAccess: Boolean = true,
)

/**
 * Whether Ledga's notifications can reach the person (spec §11, R109). [shouldAsk]: Android 13+ and POST_NOTIFICATIONS
 * not granted, so Android's dialog can still ask; only then is the onboarding step shown. [enabled]: Android lets Ledga
 * post at all, on every version. On Android 8–12 notifications can be off with no dialog to ask, so "Turn on" opens
 * Android's settings there. Tests pass a lambda for [shouldAsk]; [enabled] then follows it.
 */
fun interface NotificationAccess {
    fun shouldAsk(): Boolean

    fun enabled(): Boolean = !shouldAsk()
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val inbox: InboxSource,
    private val work: BackgroundWork,
    private val lines: LinesRepository,
    private val notifications: NotificationAccess,
    private val snapshots: Snapshots,
    private val restorer: Restorer,
    private val db: LedgaDatabase,
    private val phone: PhoneAccess,
    private val updates: UpdateWork,
    private val whatsNew: WhatsNew,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState(steps = steps(), phoneAccess = phone.granted()))
    val state: StateFlow<OnboardingState> = _state.asStateFlow()
    private var restoreStarted = false

    init {
        viewModelScope.launch { work.inboxImport.collect { p -> onImport(p) } }
        viewModelScope.launch {
            work.restoreProgress.collect { p ->
                if (!restoreStarted) return@collect
                when (p) {
                    is RestoreProgress.Done -> _state.update { it.copy(restore = p, restored = true, offer = null) }
                    else -> _state.update { it.copy(restore = p) }
                }
            }
        }
    }

    /** R127: the import's end lists the lines to name when there are two or more. */
    private suspend fun onImport(p: ImportProgress) {
        val named = if (p is ImportProgress.Done) {
            lines.all().takeIf { it.size >= 2 }?.mapIndexed { i, l -> LineName(l.id, l.phoneNumber ?: "SIM ${i + 1}", l.displayName) }
        } else {
            null
        }
        _state.update { it.copy(import = p, lines = named ?: it.lines) }
    }

    fun onName(name: String) = _state.update { it.copy(name = name.take(MAX_NAME)) }

    fun onLineName(id: Long, name: String) = _state.update { s ->
        s.copy(lines = s.lines.map { if (it.id == id) it.copy(name = name.take(MAX_NAME)) else it })
    }

    /** The main action on Welcome, Import (once it is done, or with nothing to import) and Name your lines. */
    fun next() {
        viewModelScope.launch {
            when (_state.value.step) {
                Step.WELCOME -> {
                    settings.setDisplayName(_state.value.name)
                    go(Step.SMS)
                }
                Step.IMPORT -> saveLines()
                Step.SMS, Step.NOTIFICATIONS -> finish()
            }
        }
    }

    /** The SMS dialog's answer. A denial is "Not now": Home then explains how to allow it (spec §10.4). */
    fun onSmsResult(granted: Boolean) {
        viewModelScope.launch {
            if (!granted) return@launch finish()
            val found = withContext(Dispatchers.IO) { inbox.read(0) }
            val offer = withContext(Dispatchers.IO) { findOffer() }
            _state.update {
                it.copy(
                    preview = InboxPreview(found.size, found.minOfOrNull { s -> s.receivedAt }, found.maxOfOrNull { s -> s.receivedAt }),
                    offer = offer,
                )
            }
            go(Step.IMPORT)
        }
    }

    /** R126: a snapshot, on a phone with no messages yet; null when it can't be read. */
    private suspend fun findOffer(): RestoreOffer? {
        if (db.smsDao().count() > 0) return null
        val info = snapshots.found() ?: return null
        return try {
            val plan = restorer.plan(BackupFiles.read(info.file), RestoreMode.MERGE)
            RestoreOffer(info.file, info.writtenAt, info.payments, plan.questions, simsUnreadable = plan.simsUnreadable)
        } catch (e: BackupFileError) {
            null
        }
    }

    fun answer(lineId: Long, subscriptionId: Int?) = _state.update { s ->
        s.copy(offer = s.offer?.let { o -> o.copy(answers = o.answers + (lineId to subscriptionId)) })
    }

    /** After Android's phone-access dialog: the SIMs may be readable now, so the lines are matched again. */
    fun onPhoneResult() {
        viewModelScope.launch {
            val o = _state.value.offer
            val plan = o?.let { withContext(Dispatchers.IO) { restorer.plan(BackupFiles.read(it.file), RestoreMode.MERGE) } }
            _state.update { s ->
                val asked = plan?.questions?.map { it.line.id }?.toSet().orEmpty()
                s.copy(
                    phoneAccess = phone.granted(),
                    offer = if (plan == null) {
                        s.offer
                    } else {
                        s.offer?.let { o -> o.copy(questions = plan.questions, simsUnreadable = plan.simsUnreadable, answers = o.answers.filterKeys { it in asked }) }
                    },
                )
            }
        }
    }

    /** R126: a merge with the backup's settings; the snapshot stays where it is. */
    fun restore() {
        val o = _state.value.offer ?: return
        if (!o.ready) return
        restoreStarted = true
        _state.update { it.copy(restore = RestoreProgress.Running(0, 0)) }
        work.restore(RestoreRequest(o.file, RestoreMode.MERGE, o.answers, applySettings = true, deleteAfter = false))
    }

    /** R120: the backup isn't wanted now; it stays as the earlier backup, restorable from You. */
    fun startFresh() {
        viewModelScope.launch {
            runCatching { snapshots.keepAsEarlier() }
            _state.update { it.copy(offer = null, restore = RestoreProgress.Idle) }
        }
    }

    fun startImport() = work.importInbox()

    /** "Not now" (on SMS or notifications), and the notification dialog's answer, whatever it was. */
    fun done() {
        viewModelScope.launch { finish() }
    }

    private suspend fun saveLines() {
        _state.value.lines.filter { it.name.isNotBlank() }.forEach { lines.rename(it.id, it.name) }
        afterLines()
    }

    private suspend fun afterLines() {
        if (notifications.shouldAsk()) go(Step.NOTIFICATIONS) else finish()
    }

    private suspend fun finish() {
        // R120: a backup found on this fresh install and not restored is kept as the earlier backup.
        if (!_state.value.restored) runCatching { snapshots.keepAsEarlier() }
        settings.setOnboarded()
        // R107, R108: the 6-hourly check and the notifications start now, not at the next start.
        work.keepSyncing()
        work.scheduleNotifications(settings.current(), replace = false)
        // R134: update checks start now; R141: this version's notes are filed as seen, not shown, on a fresh install.
        updates.keepChecking()
        updates.checkSoon()
        runCatching { whatsNew.seen() }
        _state.update { it.copy(finished = true) }
    }

    private fun go(step: Step) = _state.update { it.copy(step = step) }

    /** R127: fixed at the start, so "Step 3 of 4" never turns into "Step 4 of 5". */
    private fun steps(): List<Step> = buildList {
        add(Step.WELCOME)
        add(Step.SMS)
        add(Step.IMPORT)
        if (notifications.shouldAsk()) add(Step.NOTIFICATIONS)
    }

    private companion object {
        const val MAX_NAME = 40
    }
}
