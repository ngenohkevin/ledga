package com.ledga.app.ui.backup

import com.ledga.app.data.backup.BackupOrigin
import com.ledga.app.data.backup.LineEntry
import com.ledga.app.data.backup.LineQuestion
import com.ledga.app.data.backup.RestoreSource
import com.ledga.app.data.backup.RestoreSourceKind
import com.ledga.app.data.backup.SnapshotInfo
import com.ledga.app.data.lines.Sim
import com.ledga.app.testing.snapScreen
import com.ledga.app.testing.snapScreenLandscape
import com.ledga.app.ui.app.ShellFrame
import com.ledga.app.ui.design.components.SheetScaffold
import java.io.File
import java.time.Instant
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Spec §15.2: Export & restore and its restore sheet (not mocked; R124, R125). Synthetic values. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class BackupScreensTest {
    private val ui = BackupUi(
        loaded = true,
        today = LocalDate.parse("2026-10-07"),
        savedAt = Instant.parse("2026-10-07T17:12:00Z"),
        sources = listOf(
            RestoreSource(RestoreSourceKind.BEFORE_RESTORE, SnapshotInfo(File("b"), Instant.parse("2026-10-05T06:00:00Z"), 1_240)),
            RestoreSource(RestoreSourceKind.EARLIER, SnapshotInfo(File("e"), Instant.parse("2026-09-28T06:00:00Z"), 1_198)),
        ),
        export = ExportState.Saved(1_240),
    )

    private val draft = RestoreDraft(
        File("x"), deleteAfter = true, origin = BackupOrigin.LEDGA, writtenAt = Instant.parse("2026-10-02T06:00:00Z"), payments = 1_240,
        questions = listOf(
            LineQuestion(LineEntry(2, 2, null, "Business", "#1E7FD8", false, 0), listOf(Sim(5, "SIM 1", "0712345111"), Sim(6, "eSIM 1", null))),
        ),
        answers = mapOf(2L to 6),
    )

    @Test
    fun backup() = snapScreen("backup") { ShellFrame(null, onSelect = {}) { BackupContent(ui, BackupActions()) } }

    @Test
    fun backupLandscape() = snapScreenLandscape("backup") { ShellFrame(null, onSelect = {}) { BackupContent(ui, BackupActions()) } }

    @Test
    fun restoreSheet() = snapScreen("backup_restore") {
        SheetScaffold(title = "Restore") { RestoreDraftContent(draft, phoneAccess = true, actions = RestoreDraftActions()) }
    }
}
