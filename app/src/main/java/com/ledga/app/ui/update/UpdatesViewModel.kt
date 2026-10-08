package com.ledga.app.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.update.InstallStart
import com.ledga.app.data.update.UpdateService
import com.ledga.app.data.update.UpdateState
import com.ledga.app.work.DownloadProgress
import com.ledga.core.time.Periods
import com.ledga.core.update.NotesSection
import com.ledga.core.update.ReleaseNotes
import com.ledga.core.update.UpdateChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where the newest release stands on this phone (spec §13.4). */
sealed interface UpdateStatus {
    data object UpToDate : UpdateStatus

    data class Available(val version: String, val size: String?, val skipped: Boolean) : UpdateStatus

    data class Downloading(val version: String, val fraction: Float?) : UpdateStatus

    data class Ready(val version: String) : UpdateStatus

    /** A person's download that didn't finish (R136); a quiet one's failure just offers the download again. */
    data class Failed(val version: String, val message: String) : UpdateStatus
}

/** Everything You → Updates shows. */
data class UpdatesUi(
    val loaded: Boolean = false,
    val installed: String = "",
    val status: UpdateStatus = UpdateStatus.UpToDate,
    val notes: List<NotesSection> = emptyList(),
    val pageUrl: String? = null,
    val checkedLine: String = "",
    val failureLine: String? = null,
    val checking: Boolean = false,
    val beta: Boolean = false,
    val betaLine: String = "",
    /** Android's "Install unknown apps" switch for Ledga (R138). */
    val canInstall: Boolean = true,
    val installFailure: String? = null,
    val offersUpdates: Boolean = true,
) {
    companion object {
        fun of(s: UpdateState, today: LocalDate, canInstall: Boolean): UpdatesUi {
            val newest = s.newest
            val version = newest?.version?.toString()
            val download = s.download
            val status = when {
                newest == null || version == null -> UpdateStatus.UpToDate
                download is DownloadProgress.Running -> UpdateStatus.Downloading(version, download.fraction)
                s.ready -> UpdateStatus.Ready(version)
                download is DownloadProgress.Failed && download.user && download.version == version -> UpdateStatus.Failed(version, download.message)
                else -> UpdateStatus.Available(version, newest.apk?.size?.let(UpdateText::size), s.skipped)
            }
            return UpdatesUi(
                loaded = true,
                installed = s.installed.toString(),
                status = status,
                notes = newest?.let { ReleaseNotes.parse(it.notes) }.orEmpty(),
                pageUrl = newest?.pageUrl,
                checkedLine = UpdateText.checkedLine(s.checkedAt, today),
                failureLine = s.failure?.let(UpdateText::failureLine),
                checking = s.checking,
                beta = s.channel == UpdateChannel.BETA,
                betaLine = UpdateText.betaLine(s),
                canInstall = canInstall,
                installFailure = s.installFailure,
                offersUpdates = s.offersUpdates,
            )
        }
    }
}

/** You → About → Updates (spec §13.4). */
@HiltViewModel
class UpdatesViewModel @Inject constructor(private val updates: UpdateService, private val clock: Clock) : ViewModel() {
    private val canInstall = MutableStateFlow(updates.canInstall())

    val ui: StateFlow<UpdatesUi> = combine(updates.state, canInstall) { s, can -> UpdatesUi.of(s, Periods.dateOf(clock.instant()), can) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdatesUi())

    init {
        // R134: opening the screen checks when one is due.
        viewModelScope.launch { updates.check(force = false) }
    }

    /** On resume: the person may be back from Android's install switch. */
    fun refresh() {
        canInstall.value = updates.canInstall()
    }

    fun checkNow(): Job = viewModelScope.launch { updates.check(force = true) }

    fun download(): Job = viewModelScope.launch { updates.download() }

    fun skip(): Job = viewModelScope.launch { updates.skip() }

    fun setBeta(on: Boolean): Job = viewModelScope.launch { updates.setChannel(if (on) UpdateChannel.BETA else UpdateChannel.STABLE) }

    /** R138: [needsPermission] opens Android's switch when installing apps is off for Ledga. */
    fun install(needsPermission: () -> Unit): Job = viewModelScope.launch {
        if (updates.install() == InstallStart.NEEDS_PERMISSION) {
            canInstall.value = false
            needsPermission()
        }
    }
}
