package com.ledga.app.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.backup.BackupDirs
import com.ledga.app.data.backup.BackupFileError
import com.ledga.app.data.backup.BackupFiles
import com.ledga.app.data.backup.BackupOrigin
import com.ledga.app.data.backup.Documents
import com.ledga.app.data.backup.Exporter
import com.ledga.app.data.backup.Incoming
import com.ledga.app.data.backup.LineQuestion
import com.ledga.app.data.backup.RestoreMode
import com.ledga.app.data.backup.RestoreSource
import com.ledga.app.data.backup.Restorer
import com.ledga.app.data.backup.Snapshots
import com.ledga.app.data.lines.PhoneAccess
import com.ledga.app.work.BackgroundWork
import com.ledga.app.work.RestoreProgress
import com.ledga.app.work.RestoreRequest
import com.ledga.core.time.Periods
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ExportState {
    data object Idle : ExportState

    data object Working : ExportState

    data class Saved(val payments: Int) : ExportState

    /** A file for the share sheet; the screen hands it over once ([BackupViewModel.shared]). */
    data class Share(val file: File) : ExportState

    data object Failed : ExportState
}

/** A backup read and waiting for the person's go (R121, R115). [answers]: line id → SIM, null = its own line. */
data class RestoreDraft(
    val file: File,
    val deleteAfter: Boolean,
    val origin: BackupOrigin,
    val writtenAt: Instant,
    val payments: Int,
    val mode: RestoreMode = RestoreMode.MERGE,
    val questions: List<LineQuestion> = emptyList(),
    val answers: Map<Long, Int?> = emptyMap(),
    val simsUnreadable: Boolean = false,
) {
    /** Every line question answered. */
    val ready: Boolean get() = questions.all { it.line.id in answers }
}

data class BackupUi(
    val loaded: Boolean = false,
    val today: LocalDate? = null,
    val savedAt: Instant? = null,
    val sources: List<RestoreSource> = emptyList(),
    val export: ExportState = ExportState.Idle,
    val reading: Boolean = false,
    val draft: RestoreDraft? = null,
    val error: String? = null,
    /** A restore running now, or the result of one started on this screen (as You's Rescan, R77). */
    val restore: RestoreProgress = RestoreProgress.Idle,
    val phoneAccess: Boolean = true,
)

/** You → Export & restore (spec §12, R124, R125). */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val snapshots: Snapshots,
    private val exporter: Exporter,
    private val restorer: Restorer,
    private val work: BackgroundWork,
    private val dirs: BackupDirs,
    private val documents: Documents,
    private val phone: PhoneAccess,
    private val clock: Clock,
) : ViewModel() {
    private val state = MutableStateFlow(BackupUi())
    val ui: StateFlow<BackupUi> = state.asStateFlow()

    /** The backup behind [BackupUi.draft], kept so a change of mode re-plans without reading the file again. */
    private var incoming: Incoming? = null
    private var startedHere = false
    private var sawRunning = false

    init {
        refresh()
        viewModelScope.launch {
            work.restoreProgress.collect { p ->
                if (p is RestoreProgress.Running) sawRunning = true
                val ours = startedHere && sawRunning
                val shown = when (p) {
                    is RestoreProgress.Running -> p
                    is RestoreProgress.Done, is RestoreProgress.Failed -> if (ours) p else RestoreProgress.Idle
                    RestoreProgress.Idle -> RestoreProgress.Idle
                }
                state.update { it.copy(restore = shown) }
                if (p is RestoreProgress.Done && ours) refresh()
            }
        }
    }

    /** On open, on resume and after a restore: the snapshot's date, the copies to restore, phone access. */
    fun refresh() {
        viewModelScope.launch {
            val sources = snapshots.sources()
            state.update {
                it.copy(loaded = true, today = Periods.dateOf(clock.instant()), savedAt = snapshots.savedAt(), sources = sources, phoneAccess = phone.granted())
            }
        }
    }

    /** R124: the name Android's file picker suggests. */
    fun exportName(): String = Exporter.fileName(Periods.dateOf(clock.instant()))

    /** "Save to a file": into the document the person created. */
    fun saveTo(uri: Uri) = export { ExportState.Saved(exporter.write(documents.write(uri)).payments) }

    /** "Share": one file in the cache's exports/ (older shares cleared), handed to the share sheet. */
    fun share() = export {
        dirs.exports.mkdirs()
        dirs.exports.listFiles()?.forEach { it.delete() }
        val file = File(dirs.exports, exportName())
        exporter.write(file.outputStream())
        ExportState.Share(file)
    }

    fun shared() = state.update { s -> if (s.export is ExportState.Share) s.copy(export = ExportState.Idle) else s }

    private fun export(block: suspend () -> ExportState) {
        if (state.value.export == ExportState.Working) return
        state.update { it.copy(export = ExportState.Working) }
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExportState.Failed
            }
            state.update { it.copy(export = result) }
        }
    }

    /** "Choose a file": copied into no-backup storage first (R122), then read. */
    fun pick(uri: Uri) = read(deleteAfter = true) { copyIn { documents.read(uri) } }

    /**
     * A copy on this phone (R120, R121), restored from a copy of it: "Before your last restore" is the file a restore
     * rewrites first, so read in place a retried job would restore the phone's own state (final review C1). The
     * original is never deleted.
     */
    fun pickSource(source: RestoreSource) = read(deleteAfter = true) { copyIn { source.info.file.inputStream() } }

    private fun copyIn(open: () -> java.io.InputStream): File {
        dirs.incoming.mkdirs()
        val copy = File(dirs.incoming, "incoming-${clock.millis()}.ledga")
        open().use { input -> copy.outputStream().use { input.copyTo(it) } }
        return copy
    }

    private fun read(deleteAfter: Boolean, open: suspend () -> File) {
        state.update { it.copy(reading = true, error = null) }
        viewModelScope.launch {
            var file: File? = null
            try {
                val draft = withContext(Dispatchers.IO) {
                    val f = open().also { file = it }
                    val backup = BackupFiles.read(f)
                    incoming = backup
                    val plan = restorer.plan(backup, RestoreMode.MERGE)
                    RestoreDraft(
                        f, deleteAfter, backup.origin, backup.writtenAt, backup.data.counts.payments,
                        questions = plan.questions, simsUnreadable = plan.simsUnreadable,
                    )
                }
                state.update { it.copy(reading = false, draft = draft) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (deleteAfter) file?.delete()
                state.update { it.copy(reading = false, error = (e as? BackupFileError)?.message ?: BackupText.READ_FAILED) }
            }
        }
    }

    fun setMode(mode: RestoreMode) = replan { it.copy(mode = mode) }

    fun answer(lineId: Long, subscriptionId: Int?) = state.update { s ->
        s.copy(draft = s.draft?.let { d -> d.copy(answers = d.answers + (lineId to subscriptionId)) })
    }

    /** After Android's phone-access dialog: the SIMs may be readable now, so the lines are matched again. */
    fun onPhoneResult() {
        refresh()
        replan { it }
    }

    private fun replan(change: (RestoreDraft) -> RestoreDraft) {
        val d = state.value.draft ?: return
        val backup = incoming ?: return
        val next = change(d)
        viewModelScope.launch {
            val plan = withContext(Dispatchers.IO) { restorer.plan(backup, next.mode) }
            val asked = plan.questions.map { it.line.id }.toSet()
            state.update { s ->
                if (s.draft?.file != next.file) {
                    s
                } else {
                    s.copy(draft = next.copy(questions = plan.questions, simsUnreadable = plan.simsUnreadable, answers = next.answers.filterKeys { it in asked }))
                }
            }
        }
    }

    /** The person's go (the screen confirms Replace first). Replace brings the backup's settings too (R121). */
    fun start() {
        val d = state.value.draft ?: return
        if (!d.ready || state.value.restore is RestoreProgress.Running) return
        startedHere = true
        sawRunning = false
        work.restore(RestoreRequest(d.file, d.mode, d.answers, applySettings = d.mode == RestoreMode.REPLACE, deleteAfter = d.deleteAfter))
        incoming = null
        state.update { it.copy(draft = null) }
    }

    fun dismiss() {
        val d = state.value.draft ?: return
        if (d.deleteAfter) d.file.delete()
        incoming = null
        state.update { it.copy(draft = null) }
    }

    fun clearError() = state.update { it.copy(error = null) }
}
