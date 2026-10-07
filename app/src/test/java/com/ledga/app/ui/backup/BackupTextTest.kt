package com.ledga.app.ui.backup

import com.ledga.app.data.backup.BackupOrigin
import com.ledga.app.data.backup.RestoreSourceKind
import com.ledga.app.data.lines.Sim
import com.ledga.app.work.RestoreProgress
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Test

/** R124, R125: what Export & restore says. */
class BackupTextTest {
    private val today = LocalDate.parse("2026-10-07")

    @Test
    fun `the snapshot line says when, in Nairobi time`() {
        assertEquals("No snapshot yet", BackupText.savedLine(null, today))
        assertEquals("Snapshot saved today, 8:12 PM", BackupText.savedLine(Instant.parse("2026-10-07T17:12:00Z"), today))
        assertEquals("Snapshot saved yesterday, 9:00 AM", BackupText.savedLine(Instant.parse("2026-10-06T06:00:00Z"), today))
        assertEquals("Snapshot saved 2 Oct", BackupText.savedLine(Instant.parse("2026-10-02T06:00:00Z"), today))
        assertEquals("Snapshot saved 2 Oct 2025", BackupText.savedLine(Instant.parse("2025-10-02T06:00:00Z"), today))
    }

    @Test
    fun `sources, drafts and results read as plain sentences`() {
        assertEquals("Before your last restore", BackupText.sourceTitle(RestoreSourceKind.BEFORE_RESTORE))
        assertEquals("Earlier backup", BackupText.sourceTitle(RestoreSourceKind.EARLIER))
        assertEquals("1 payment", BackupText.payments(1))
        assertEquals("1,240 payments", BackupText.payments(1_240))
        val draft = RestoreDraft(File("x"), false, BackupOrigin.LEDGA, Instant.parse("2026-10-02T06:00:00Z"), 1_240)
        assertEquals("Backup from 2 Oct 2026 · 1,240 payments", BackupText.draftLine(draft))
        assertEquals(
            "A Ledga 1 export from 2 Oct 2026 · 1,240 payments. It holds payments only: no lines, rules or notes.",
            BackupText.draftLine(draft.copy(origin = BackupOrigin.V1)),
        )
        assertEquals("Restored · 12 new payments", BackupText.restoredLine(RestoreProgress.Done(12, 1_252)))
        assertEquals("Restored · nothing new", BackupText.restoredLine(RestoreProgress.Done(0, 1_240)))
        assertEquals("Saved · 1,240 payments", BackupText.exportLine(ExportState.Saved(1_240)))
        assertEquals("SIM 1 ··11", BackupText.simLabel(Sim(5, "SIM 1", "0712345111")))
        assertEquals("eSIM 1", BackupText.simLabel(Sim(6, "eSIM 1", null)))
        assertEquals("SIM 7", BackupText.simLabel(Sim(7, null, null)))
    }
}
