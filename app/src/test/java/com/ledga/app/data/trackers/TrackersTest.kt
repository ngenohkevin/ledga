package com.ledga.app.data.trackers

import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.room.TxRow
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.txRow
import com.ledga.core.chart.Bucketing
import com.ledga.core.model.Categories
import com.ledga.core.model.TxKind
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R52: one set of tracker numbers, from the ledger, in Nairobi months. Synthetic KPLC payments (txRow's default). */
@RunWith(RobolectricTestRunner::class)
class TrackersTest {
    private val db = TestDb.inMemory()
    private val ledger = LedgerQueries(db)
    private val trackers = Trackers(db, ledger)
    private val now = Instant.parse("2026-10-06T06:00:00Z") // Tue 6 Oct 2026, 09:00 in Nairobi

    @After fun close() = db.close()

    private suspend fun put(vararg rows: TxRow) = db.transactionsDao().upsertAll(rows.toList())

    private fun kplc(code: String, at: String, cents: Long, line: Long? = 1, fee: Long = 0) =
        txRow(code = code, at = Instant.parse(at), amountCents = cents, feeCents = fee, lineId = line)

    private fun received(code: String, at: String, cents: Long, name: String = "JANE TESTER", hidden: Boolean = false) =
        txRow(code = code, kind = TxKind.RECEIVE, at = Instant.parse(at), amountCents = cents, name = name, phone = "0700000001", account = null,
            categoryKey = Categories.RECEIVED, hidden = hidden)

    private fun saved(code: String, at: String, cents: Long, fee: Long = 0, reversed: Boolean = false) =
        txRow(code = code, kind = TxKind.SAVINGS_OUT, at = Instant.parse(at), amountCents = cents, feeCents = fee, name = "M-SHWARI", account = null,
            categoryKey = Categories.SAVINGS, reversed = reversed)

    private fun shop(code: String, at: String, cents: Long, name: String, line: Long? = 1) =
        txRow(code = code, kind = TxKind.BUY_GOODS, at = Instant.parse(at), amountCents = cents, name = name, account = null,
            categoryKey = Categories.GROCERIES, lineId = line)

    private suspend fun electricity(lineId: Long? = null) = trackers.summaries(lineId, now).first().first { it.category.key == Categories.ELECTRICITY }

    @Test
    fun `a tracker's month equals Spending's category total, fees included`() = runTest {
        put(kplc("TJK4AB12PA", "2026-10-02T07:00:00Z", 100_000, fee = 2_300), kplc("TJK4AB12PB", "2026-10-03T07:00:00Z", 50_000))
        val spending = ledger.spentByCategory(Periods.liveRange(Periods.current(PeriodType.MONTH, now), now)).first()
            .first { it.categoryKey == Categories.ELECTRICITY }
        val tracker = electricity()
        assertEquals(spending.cents, tracker.thisMonth.total.cents)
        assertEquals(152_300, tracker.thisMonth.total.cents)
        assertEquals(2, tracker.thisMonth.count)
    }

    @Test
    fun `the average is of completed months from the first payment, the running month left out`() = runTest {
        put(
            kplc("TJK4AB12PC", "2026-07-10T07:00:00Z", 200_000),
            kplc("TJK4AB12PD", "2026-09-10T07:00:00Z", 100_000),
            kplc("TJK4AB12PE", "2026-10-02T07:00:00Z", 900_000),
        )
        val s = electricity()
        assertEquals(100_000, s.averageCents) // (2,000 + 0 + 1,000) / 3: July, August, September
        assertEquals(13, s.months.size)
        assertEquals(100_000, s.lastMonth.total.cents)
    }

    @Test
    fun `usually by the Nth needs a payment in each of the last three months`() = runTest {
        put(
            kplc("TJK4AB12PF", "2026-07-09T07:00:00Z", 100_000),
            kplc("TJK4AB12PG", "2026-08-11T07:00:00Z", 100_000),
            kplc("TJK4AB12PH", "2026-09-10T07:00:00Z", 100_000),
        )
        assertEquals(10, electricity().usualDay) // the median of the 9th, 10th and 11th
        db.transactionsDao().deleteCodes(listOf("TJK4AB12PG"))
        assertNull(electricity().usualDay, "August unpaid: not a monthly bill any more")
    }

    @Test
    fun `the chosen line narrows a tracker, and the newest payment names who was paid`() = runTest {
        put(kplc("TJK4AB12PJ", "2026-10-02T07:00:00Z", 100_000, line = 1), kplc("TJK4AB12PK", "2026-10-03T07:00:00Z", 40_000, line = 2))
        val business = electricity(lineId = 2)
        assertEquals(40_000, business.thisMonth.total.cents)
        assertEquals("TJK4AB12PK", business.last?.code)
        assertEquals("KPLC PREPAID", business.last?.name)
    }

    @Test
    fun `only tracked categories have trackers, in picker order`() = runTest {
        assertEquals(
            listOf(Categories.ELECTRICITY, Categories.WATER, Categories.FUEL, Categories.CAR_SERVICE),
            trackers.summaries(null, now).first().map { it.category.key },
        )
    }

    @Test
    fun `a category goes year by year after twelve months, sums this year, and lists its payments newest first`() = runTest {
        put(
            kplc("TJK4AB12PL", "2025-03-10T07:00:00Z", 100_000),
            kplc("TJK4AB12PM", "2026-02-10T07:00:00Z", 200_000),
            kplc("TJK4AB12PN", "2026-10-02T07:00:00Z", 300_000),
        )
        val d = trackers.category(Categories.ELECTRICITY, null, now).first()!!
        assertEquals(CategoryMeasure.SPENT, d.measure)
        assertEquals(listOf("2025", "2026"), Bucketing.allTime(d.allMonths).map { it.period.key })
        assertEquals(500_000, d.yearSoFarCents)
        assertEquals(listOf("TJK4AB12PN", "TJK4AB12PM", "TJK4AB12PL"), d.payments.map { it.code })
        assertNull(trackers.category("no_such_category", null, now).first())
    }

    @Test
    fun `a Money in category totals what came in, and Not spending what moved, reversed and hidden left out`() = runTest {
        put(
            received("TJK4AB12QA", "2026-10-02T07:00:00Z", 500_000),
            received("TJK4AB12QB", "2026-10-03T07:00:00Z", 250_000),
            received("TJK4AB12QC", "2026-10-04T07:00:00Z", 999_900, hidden = true),
            saved("TJK4AB12QD", "2026-10-02T08:00:00Z", 300_000, fee = 1_500),
            saved("TJK4AB12QE", "2026-10-03T08:00:00Z", 700_000, reversed = true),
        )
        val income = trackers.category(Categories.RECEIVED, null, now).first()!!
        assertEquals(CategoryMeasure.RECEIVED, income.measure)
        assertEquals(750_000, income.summary.thisMonth.total.cents)
        assertEquals(2, income.summary.thisMonth.count)
        val savings = trackers.category(Categories.SAVINGS, null, now).first()!!
        assertEquals(CategoryMeasure.MOVED, savings.measure)
        assertEquals(300_000, savings.summary.thisMonth.total.cents, "the amount moved, no fee, the reversed one left out")
        assertEquals(1, savings.summary.thisMonth.count)
    }

    @Test
    fun `top places are the five biggest over the last twelve months and this one, on the chosen line`() = runTest {
        put(
            shop("TJK4AB12RA", "2026-10-01T07:00:00Z", 90_000, "CORNER SHOP"),
            shop("TJK4AB12RB", "2026-09-01T07:00:00Z", 60_000, "CORNER SHOP"),
            shop("TJK4AB12RC", "2026-08-01T07:00:00Z", 80_000, "GREEN GROCER"),
            shop("TJK4AB12RD", "2025-09-30T07:00:00Z", 900_000, "OLD MARKET"), // before last October: left out
            shop("TJK4AB12RE", "2026-07-01T07:00:00Z", 10_000, "SAMPLE KIOSK A"),
            shop("TJK4AB12RF", "2026-07-02T07:00:00Z", 20_000, "SAMPLE KIOSK B"),
            shop("TJK4AB12RG", "2026-07-03T07:00:00Z", 30_000, "SAMPLE KIOSK C"),
            shop("TJK4AB12RH", "2026-07-04T07:00:00Z", 40_000, "SAMPLE KIOSK D", line = 2),
        )
        val all = trackers.category(Categories.GROCERIES, null, now).first()!!.topPlaces
        assertEquals(listOf("CORNER SHOP", "GREEN GROCER", "SAMPLE KIOSK D", "SAMPLE KIOSK C", "SAMPLE KIOSK B"), all.map { it.name })
        assertEquals(150_000L to 2, all.first().totalCents to all.first().count)
        val business = trackers.category(Categories.GROCERIES, 2, now).first()!!.topPlaces
        assertEquals(listOf("SAMPLE KIOSK D"), business.map { it.name })
    }

    @Test
    fun `this month's amount for every category is in its own measure`() = runTest {
        put(
            kplc("TJK4AB12SA", "2026-10-02T07:00:00Z", 100_000, fee = 2_300),
            kplc("TJK4AB12SB", "2026-09-02T07:00:00Z", 900_000), // last month: not this month's
            received("TJK4AB12SC", "2026-10-02T07:00:00Z", 500_000),
            saved("TJK4AB12SD", "2026-10-02T08:00:00Z", 300_000, fee = 1_500),
        )
        val totals = trackers.monthTotals(null, now).first()
        assertEquals(102_300, totals[Categories.ELECTRICITY])
        assertEquals(500_000, totals[Categories.RECEIVED])
        assertEquals(300_000, totals[Categories.SAVINGS])
        assertNull(totals[Categories.GROCERIES], "a category with nothing this month has no entry; the tab shows Ksh 0")
    }
}
