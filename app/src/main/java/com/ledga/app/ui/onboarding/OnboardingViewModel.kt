package com.ledga.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.capture.InboxSource
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.work.BackgroundWork
import com.ledga.app.work.ImportProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

enum class Step { WELCOME, SMS, IMPORT, LINES, NOTIFICATIONS }

/** What the inbox holds, shown before anything is imported (spec §10.4 step 3). */
data class InboxPreview(val count: Int, val oldest: Instant?, val newest: Instant?)

/** A detected line to name (step 4): [label] says which SIM it is, [name] is what the user types. */
data class LineName(val id: Long, val label: String, val name: String)

data class OnboardingState(
    val step: Step = Step.WELCOME,
    /** The steps this phone will see, for the progress dots (LINES joins only when two lines turn up). */
    val steps: List<Step> = listOf(Step.WELCOME, Step.SMS, Step.IMPORT),
    val name: String = "",
    val preview: InboxPreview? = null,
    val import: ImportProgress = ImportProgress.Idle,
    val lines: List<LineName> = emptyList(),
    val finished: Boolean = false,
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
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState(steps = stepsFor(lineCount = 0)))
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        viewModelScope.launch { work.inboxImport.collect { p -> _state.update { it.copy(import = p) } } }
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
                Step.IMPORT -> afterImport()
                Step.LINES -> saveLines()
                Step.SMS, Step.NOTIFICATIONS -> finish()
            }
        }
    }

    /** The SMS dialog's answer. A denial is "Not now": Home then explains how to allow it (spec §10.4). */
    fun onSmsResult(granted: Boolean) {
        viewModelScope.launch {
            if (!granted) return@launch finish()
            val found = withContext(Dispatchers.IO) { inbox.read(0) }
            _state.update {
                it.copy(preview = InboxPreview(found.size, found.minOfOrNull { s -> s.receivedAt }, found.maxOfOrNull { s -> s.receivedAt }))
            }
            go(Step.IMPORT)
        }
    }

    fun startImport() = work.importInbox()

    /** "Not now" (on SMS or notifications), and the notification dialog's answer, whatever it was. */
    fun done() {
        viewModelScope.launch { finish() }
    }

    private suspend fun afterImport() {
        val found = lines.all()
        if (found.size >= 2) {
            _state.update { s ->
                s.copy(
                    lines = found.mapIndexed { i, l -> LineName(l.id, l.phoneNumber ?: "SIM ${i + 1}", l.displayName) },
                    steps = stepsFor(found.size),
                )
            }
            go(Step.LINES)
        } else {
            afterLines()
        }
    }

    private suspend fun saveLines() {
        _state.value.lines.filter { it.name.isNotBlank() }.forEach { lines.rename(it.id, it.name) }
        afterLines()
    }

    private suspend fun afterLines() {
        if (notifications.shouldAsk()) go(Step.NOTIFICATIONS) else finish()
    }

    private suspend fun finish() {
        settings.setOnboarded()
        // R107, R108: the 6-hourly check and the notifications start now, not at the next start.
        work.keepSyncing()
        work.scheduleNotifications(settings.current(), replace = false)
        _state.update { it.copy(finished = true) }
    }

    private fun go(step: Step) = _state.update { it.copy(step = step) }

    private fun stepsFor(lineCount: Int): List<Step> = buildList {
        add(Step.WELCOME)
        add(Step.SMS)
        add(Step.IMPORT)
        if (lineCount >= 2) add(Step.LINES)
        if (notifications.shouldAsk()) add(Step.NOTIFICATIONS)
    }

    private companion object {
        const val MAX_NAME = 40
    }
}
