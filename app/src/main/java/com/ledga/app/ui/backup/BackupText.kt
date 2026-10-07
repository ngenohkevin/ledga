package com.ledga.app.ui.backup

import com.ledga.app.data.backup.BackupOrigin
import com.ledga.app.data.backup.RestoreSourceKind
import com.ledga.app.data.backup.SnapshotInfo
import com.ledga.app.data.lines.Sim
import com.ledga.app.ui.app.grouped
import com.ledga.app.ui.design.format.DateLabels
import com.ledga.app.work.RestoreProgress
import java.time.Instant
import java.time.LocalDate

/** Everything Export & restore says (R124, R125). */
object BackupText {
    private val DOTS = Char(0x2026)

    const val EXPORT = "A full copy of your history, to keep or to move to another phone, with a spreadsheet of your payments."
    const val PRIVACY = "The file holds your M-Pesa messages: anyone who opens it can read your payments and balances. Keep it somewhere private."
    const val ANDROID_BACKUP = "Ledga keeps a copy of your history on this phone. When backup is on in Android's settings, Android saves it " +
        "to your Google account, so a new phone can restore it."
    const val READ_FAILED = "Ledga couldn't open that file."
    const val KEEP_OWN = "Keep it as its own line"
    const val REPLACE_TITLE = "Replace everything on this phone?"
    const val REPLACE_BODY = "Your payments, notes, rules and lines here are replaced by the backup's. Ledga keeps a copy first: restoring " +
        "\"Before your last restore\" brings them back."

    /** R125: "Snapshot saved today, 8:12 PM", "… yesterday, 9:00 AM", "… 2 Oct", "… 2 Oct 2025". */
    fun savedLine(at: Instant?, today: LocalDate): String {
        at ?: return "No snapshot yet"
        val day = DateLabels.nairobiDate(at)
        return "Snapshot saved " + when (day) {
            today -> "today, ${DateLabels.clock(at)}"
            today.minusDays(1) -> "yesterday, ${DateLabels.clock(at)}"
            else -> if (day.year == today.year) DateLabels.dayMonth(day) else DateLabels.date(day)
        }
    }

    fun sourceTitle(kind: RestoreSourceKind): String = when (kind) {
        RestoreSourceKind.BEFORE_RESTORE -> "Before your last restore"
        RestoreSourceKind.EARLIER -> "Earlier backup"
    }

    fun sourceLine(info: SnapshotInfo): String = "${DateLabels.date(DateLabels.nairobiDate(info.writtenAt))} · ${payments(info.payments)}"

    fun payments(n: Int): String = "${grouped(n)} ${if (n == 1) "payment" else "payments"}"

    fun draftLine(draft: RestoreDraft): String {
        val date = DateLabels.date(DateLabels.nairobiDate(draft.writtenAt))
        return when (draft.origin) {
            BackupOrigin.V1 -> "A Ledga 1 export from $date · ${payments(draft.payments)}. It holds payments only: no lines, rules or notes."
            else -> "Backup from $date · ${payments(draft.payments)}"
        }
    }

    fun exportLine(state: ExportState): String? = when (state) {
        ExportState.Working -> "Writing the file$DOTS"
        is ExportState.Saved -> "Saved · ${payments(state.payments)}"
        ExportState.Failed -> "The export didn't finish. Try again."
        ExportState.Idle, is ExportState.Share -> null
    }

    fun restoredLine(done: RestoreProgress.Done): String =
        if (done.added == 0) "Restored · nothing new" else "Restored · ${grouped(done.added)} new ${if (done.added == 1) "payment" else "payments"}"

    /** "SIM 1 ··11": the phone's name for the SIM, and its number's last two digits when Android shares it. */
    fun simLabel(sim: Sim): String {
        val name = sim.displayName ?: "SIM ${sim.subscriptionId}"
        val tail = sim.number?.filter(Char::isDigit)?.takeLast(2)?.takeIf { it.length == 2 }
        return if (tail == null) name else "$name ··$tail"
    }
}
