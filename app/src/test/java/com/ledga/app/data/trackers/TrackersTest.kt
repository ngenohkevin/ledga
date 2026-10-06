package com.ledga.app.data.trackers

import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.room.RuleRow
import com.ledga.app.data.room.TxRow
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.txRow
import com.ledga.core.chart.Bucketing
import com.ledga.core.derive.RuleAction
import com.ledga.core.derive.RuleField
import com.ledga.core.derive.RuleOrigin
import com.ledga.core.model.Categories
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
    fun `detail goes year by year after twelve months, sums this year, and lists the rules, the person's first`() = runTest {
        put(
            kplc("TJK4AB12PL", "2025-03-10T07:00:00Z", 100_000),
            kplc("TJK4AB12PM", "2026-02-10T07:00:00Z", 200_000),
            kplc("TJK4AB12PN", "2026-10-02T07:00:00Z", 300_000),
        )
        db.rulesDao().insert(
            RuleRow(field = RuleField.NAME_CONTAINS, pattern = "SAMPLE POWER", action = RuleAction.SET_CATEGORY, categoryKey = Categories.ELECTRICITY, origin = RuleOrigin.USER, priority = 0, createdAt = now),
        )
        val d = trackers.detail(Categories.ELECTRICITY, null, now).first()!!
        assertEquals(listOf("2025", "2026"), Bucketing.allTime(d.allMonths).map { it.period.key })
        assertEquals(500_000, d.yearSoFarCents)
        assertEquals("SAMPLE POWER", d.rules.first().pattern)
        assertTrue(d.rules.size > 1 && d.rules.drop(1).all { it.origin == RuleOrigin.SYSTEM }, "the built-in KPLC rules follow")
        assertEquals(listOf("TJK4AB12PN", "TJK4AB12PM", "TJK4AB12PL"), d.payments.map { it.code })
        assertNull(trackers.detail("no_such_category", null, now).first())
    }
}
