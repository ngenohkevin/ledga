package com.ledga.app.ui.you

import com.ledga.app.data.room.LineRow
import com.ledga.app.data.settings.TextSize
import com.ledga.app.ui.app.grouped
import com.ledga.app.ui.tx.TxText
import com.ledga.app.ui.design.theme.Appearance
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What You and its subscreens say (spec §10.4). */
object YouText {
    fun themeLabel(a: Appearance): String = when (a) {
        Appearance.SYSTEM -> "System"
        Appearance.LIGHT -> "Light"
        Appearance.DARK -> "Dark"
    }

    fun textSizeLabel(t: TextSize): String = when (t) {
        TextSize.SYSTEM -> "Default"
        TextSize.SMALL -> "Small"
        TextSize.MEDIUM -> "Medium"
        TextSize.LARGE -> "Large"
        TextSize.EXTRA_LARGE -> "Extra large"
    }

    /** You's row (mockup `you`): "System theme · Default text". */
    fun appearanceLine(a: Appearance, t: TextSize): String = "${themeLabel(a)} theme · ${textSizeLabel(t)} text"

    private val MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    private val DOTS = Char(0x2026)

    /** R81: "6,385 payments · since Mar 2024 · all on this phone". */
    fun profileLine(payments: Int, since: LocalDate?): String = when {
        payments == 0 || since == null -> "No payments yet · all on this phone"
        else -> "${grouped(payments)} ${if (payments == 1) "payment" else "payments"} · since ${MONTH.format(since)} · all on this phone"
    }

    fun linesLine(lines: List<LineRow>): String =
        lines.joinToString(" · ") { TxText.lineLabel(it) }.ifEmpty { "No lines yet" }

    fun categoriesLine(tracked: Int, rules: Int): String = "$tracked tracked · $rules ${if (rules == 1) "rule" else "rules"}"

    fun unreadableLine(n: Int): String = when (n) {
        0 -> "None"
        1 -> "1 message"
        else -> "${grouped(n)} messages"
    }

    /** R77. */
    fun rescanLine(s: RescanState): String = when (s) {
        RescanState.Idle -> "Recovers anything missed"
        is RescanState.Running -> if (s.total > 0) "Reading ${grouped(s.done)} of ${grouped(s.total)}$DOTS" else "Starting$DOTS"
        is RescanState.Done -> if (s.added == 0) "Done · nothing new" else "Done · ${grouped(s.added)} new"
        RescanState.Failed -> "Didn't finish. Try again."
    }
}
