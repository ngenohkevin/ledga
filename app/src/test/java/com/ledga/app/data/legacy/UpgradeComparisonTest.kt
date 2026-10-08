package com.ledga.app.data.legacy

import com.ledga.app.data.room.LedgerRow
import com.ledga.app.testing.CategoryCompare
import com.ledga.app.testing.MonthCompare
import com.ledga.app.testing.UpgradeComparison
import com.ledga.app.testing.V1ExportTx
import com.ledga.core.model.Categories
import com.ledga.core.model.FlowKind
import com.ledga.core.model.TxKind
import java.time.Instant
import java.time.YearMonth
import kotlin.test.assertEquals
import org.junit.Test

/** R160: the v1 → v2 comparison the rehearsal prints. Synthetic rows only. */
class UpgradeComparisonTest {
    private val sep = Instant.parse("2026-09-15T09:00:00Z")
    private val oct = Instant.parse("2026-10-05T09:00:00Z")

    private fun v1(code: String, type: String, amount: Double, categoryId: Long?, at: Instant = sep, direction: String = "OUTFLOW", reverses: String? = null) =
        V1ExportTx(code, type, direction, amount, categoryId, null, null, reverses, "synthetic", at)

    private fun v2(code: String, kind: TxKind, spend: Long, fee: Long = 0, key: String = Categories.OTHER, flow: FlowKind = FlowKind.SPEND, at: Instant = sep) =
        LedgerRow(code, null, at, kind, flow, key, null, spend + fee, spend, 0, fee)

    @Test
    fun `v1's spending rule leaves out repayments, transfers and reversed payments`() {
        val rows = listOf(
            v1("TJK4AB12HA", "SEND", 100.0, 6),
            v1("TJK4AB12HB", "FULIZA_REPAYMENT", 50.0, null),
            v1("TJK4AB12HC", "SEND", 30.0, 14),
            v1("TJK4AB12HD", "SEND", 20.0, 6),
            v1("TJK4AB12HE", "REVERSAL", 20.0, null, direction = "INFLOW", reverses = "TJK4AB12HD"),
        )
        assertEquals(listOf("TJK4AB12HA"), UpgradeComparison.v1Counted(rows).map { it.code })
    }

    @Test
    fun `each month is compared payment by payment`() {
        val v1 = listOf(v1("TJK4AB12HA", "SEND", 100.0, 6), v1("TJK4AB12HF", "BUY_GOODS", 200.0, 1, at = oct))
        val v2 = listOf(
            v2("TJK4AB12HA", TxKind.SEND, 10_000),
            v2("TJK4AB12HG", TxKind.FULIZA_ONLY, 4_000, fee = 40),
            v2("TJK4AB12HF", TxKind.BUY_GOODS, 19_950, at = oct),
        )
        val (s, o) = UpgradeComparison.months(v1, v2)
        assertEquals(
            MonthCompare(YearMonth.of(2026, 9), 10_000, 14_000, 40, both = 1, sameAmount = 1, v1Only = emptyMap(), v2Only = mapOf("FULIZA_ONLY" to 1)),
            s,
        )
        assertEquals(1, o.both)
        assertEquals(0, o.sameAmount)
        assertEquals("+40.0%", UpgradeComparison.percent(s.v1Cents, s.v2SpendCents))
        assertEquals("n/a", UpgradeComparison.percent(0, 5))
    }

    @Test
    fun `categories agree where v1's map says, Bills in any bills category, My Accounts as own account`() {
        val v1 = listOf(
            v1("TJK4AB12HA", "SEND", 100.0, 6),
            v1("TJK4AB12HF", "BUY_GOODS", 200.0, 1),
            v1("TJK4AB12HC", "SEND", 30.0, 14),
            v1("TJK4AB12HH", "SEND", 900.0, 3),
        )
        val v2 = listOf(
            v2("TJK4AB12HA", TxKind.SEND, 10_000, key = Categories.SENT_TO_PEOPLE),
            v2("TJK4AB12HF", TxKind.BUY_GOODS, 20_000, key = Categories.FOOD),
            v2("TJK4AB12HC", TxKind.SEND, 0, flow = FlowKind.OWN_OUT),
            v2("TJK4AB12HH", TxKind.PAYBILL, 90_000, key = Categories.ELECTRICITY),
        )
        assertEquals(
            listOf(
                CategoryCompare("Groceries", 1, 0, mapOf(Categories.FOOD to 1)),
                CategoryCompare("Bills & Utilities", 1, 1, emptyMap()),
                CategoryCompare("Send Money", 1, 1, emptyMap()),
                CategoryCompare("My Accounts", 1, 1, emptyMap()),
            ),
            UpgradeComparison.categories(v1, v2),
        )
    }
}
