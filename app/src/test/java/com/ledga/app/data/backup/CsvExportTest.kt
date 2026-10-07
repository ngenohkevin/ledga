package com.ledga.app.data.backup

import com.ledga.app.testing.txRow
import com.ledga.core.model.FlowKind
import com.ledga.core.model.TxKind
import kotlin.test.assertEquals
import org.junit.Test

/** R124: the spreadsheet's columns and cells (spec §12.2). Synthetic payments. */
class CsvExportTest {
    @Test
    fun `the columns are exactly the spec's`() {
        assertEquals(
            "Date,Time,Code,Type,Flow,Category,Counterparty,Phone,Account,Amount,Fee,Balance,Line,Note\r\n",
            CsvExport.line(CsvExport.HEADER),
        )
    }

    @Test
    fun `a payment reads as Nairobi date and time, words for its kind and flow, and plain decimals`() {
        // 11:15 UTC on 5 Oct 2026 is 2:15 PM in Nairobi.
        assertEquals(
            listOf("2026-10-05", "14:15", "TJK4AB12FA", "Paybill", "Spending", "Electricity", "KPLC PREPAID", "", "37100000001", "1000.00", "0.00", "23150.75", "Personal", ""),
            CsvExport.row(txRow(), "Electricity", "Personal"),
        )
        val receipt = txRow(code = "TJK4AB12FC", kind = TxKind.RECEIVE, name = "JANE TESTER", phone = "0712***111", account = null, amountCents = 120_005, balanceCents = null, lineId = null, note = "Lunch")
        assertEquals(
            listOf("2026-10-05", "14:15", "TJK4AB12FC", "Received", "Money in", "Received", "JANE TESTER", "0712***111", "", "1200.05", "0.00", "", "", "Lunch"),
            CsvExport.row(receipt, "Received", null),
        )
    }

    @Test
    fun `cells with commas, quotes or line breaks are quoted the RFC 4180 way`() {
        assertEquals("plain", CsvExport.escape("plain"))
        assertEquals("\"CORNER SHOP, \"\"DOWNTOWN\"\"\"", CsvExport.escape("CORNER SHOP, \"DOWNTOWN\""))
        assertEquals("\"two\nlines\"", CsvExport.escape("two\nlines"))
    }

    @Test
    fun `a text cell that a spreadsheet would run as a formula is shown as text`() {
        val tx = txRow(name = "=CORNER SHOP", account = "+1", note = "@home")
        val cells = CsvExport.row(tx, "Groceries", null)
        assertEquals("'=CORNER SHOP", cells[6])
        assertEquals("'+1", cells[8])
        assertEquals("'@home", cells[13])
    }

    @Test
    fun `every flow has a word`() {
        assertEquals(
            listOf("Spending", "Money in", "To savings", "From savings", "To own account", "From own account", "Fuliza repayment", "Reversal"),
            FlowKind.entries.map(CsvExport::flowLabel),
        )
    }
}
