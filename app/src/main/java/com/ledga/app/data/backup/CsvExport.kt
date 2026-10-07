package com.ledga.app.data.backup

import com.ledga.app.data.room.TxRow
import com.ledga.app.ui.tx.TxText
import com.ledga.core.model.FlowKind
import com.ledga.core.time.Nairobi
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * An export's `transactions.csv` (spec §12.2, R124): RFC 4180 with CRLF line ends, the file starting with a byte-order
 * mark so spreadsheets read it as UTF-8. Dates and times are Nairobi's, amounts plain decimals ("1200.00"): spreadsheets
 * read both in every locale.
 */
object CsvExport {
    val BOM: Char = Char(0xFEFF)

    val HEADER = listOf("Date", "Time", "Code", "Type", "Flow", "Category", "Counterparty", "Phone", "Account", "Amount", "Fee", "Balance", "Line", "Note")

    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    /** One payment's cells; [category] and [line] are names (the key when the category is gone, blank with no line). */
    fun row(tx: TxRow, category: String?, line: String?): List<String> {
        val at = tx.occurredAt.atZone(Nairobi.ZONE)
        return listOf(
            DATE.format(at),
            TIME.format(at),
            tx.code,
            TxText.kindLabel(tx.kind),
            flowLabel(tx.flow),
            category ?: tx.categoryKey,
            text(tx.counterpartyName),
            tx.counterpartyPhone.orEmpty(),
            text(tx.counterpartyAccount),
            plain(tx.amountCents),
            plain(tx.feeCents),
            tx.balanceCents?.let(::plain).orEmpty(),
            line.orEmpty(),
            text(tx.note),
        )
    }

    /** One CSV line, CRLF-ended. */
    fun line(fields: List<String>): String = fields.joinToString(",", transform = ::escape) + "\r\n"

    /** RFC 4180: a cell with a comma, a quote or a line break is quoted, its quotes doubled. */
    fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\"" else field

    /** "1200.00": no thousands separator, two decimals. */
    fun plain(cents: Long): String = (if (cents < 0) "-" else "") + "${abs(cents) / 100}." + "%02d".format(abs(cents) % 100)

    fun flowLabel(flow: FlowKind): String = when (flow) {
        FlowKind.SPEND -> "Spending"
        FlowKind.INCOME -> "Money in"
        FlowKind.SAVINGS_OUT -> "To savings"
        FlowKind.SAVINGS_IN -> "From savings"
        FlowKind.OWN_OUT -> "To own account"
        FlowKind.OWN_IN -> "From own account"
        FlowKind.LOAN_REPAY -> "Fuliza repayment"
        FlowKind.REVERSAL_IN -> "Reversal"
    }

    /** Free text a spreadsheet would run as a formula (=, +, -, @ first) gets a leading apostrophe and shows as text. */
    private fun text(value: String?): String {
        val v = value.orEmpty()
        return if (v.firstOrNull() in FORMULA) "'$v" else v
    }

    private val FORMULA = setOf('=', '+', '-', '@')
}
