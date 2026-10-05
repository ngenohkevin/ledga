package com.ledga.app.data.derive

import androidx.paging.PagingSource
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.LineBalance
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.room.dao.CategoryTotal
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.core.derive.Override
import com.ledga.core.model.Categories
import com.ledga.core.money.Money
import com.ledga.core.time.InstantRange
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class LedgerQueriesTest {
    private val db = TestDb.inMemory()
    private val deriver = Deriver(db)
    private val queries = LedgerQueries(db)

    @After fun close() = db.close()

    private suspend fun ingest(vararg bodies: String, lineId: Long? = null) =
        SmsIngestor(db, deriver).ingestAll(bodies.map { RawSms("MPESA", it, Instant.parse("2026-04-02T06:00:00Z"), null, lineId, SmsSource.INBOX) })

    @Test
    fun `the Nairobi month owns 23-59 on its last day and the live range is open-ended`() = runTest {
        ingest(Sms.send("TJK4AB12HA", "100.00", "31/3/26 at 11:59 PM"), Sms.send("TJK4AB12HB", "200.00", "1/4/26 at 12:00 AM"))
        val march = Periods.of(PeriodType.MONTH, Instant.parse("2026-03-15T09:00:00Z"))
        assertEquals(Money(10_000 + 700), queries.spent(march.range()).first())
        val april = Periods.current(PeriodType.MONTH, Instant.parse("2026-04-01T06:00:00Z"))
        val live = Periods.liveRange(april, Instant.parse("2026-04-01T06:00:00Z"))
        assertNull(live.endExclusive)
        assertEquals(Money(20_000 + 700), queries.spent(live).first())
    }

    @Test
    fun `spending by category, money in and the line filter`() = runTest {
        ingest(Sms.KPLC, Sms.SEND, lineId = null)
        ingest(Sms.receive("TJK4AB12HC", "SAMPLE EMPLOYER LTD", "20,000.00"), lineId = null)
        val all = InstantRange(Instant.parse("2026-01-01T00:00:00Z"), null)
        assertEquals(
            listOf(
                CategoryTotal(Categories.ELECTRICITY, 100_000, 1),
                CategoryTotal(Categories.SENT_TO_PEOPLE, 50_700, 1),
            ),
            queries.spentByCategory(all).first(),
        )
        assertEquals(Money(2_000_000), queries.moneyIn(all).first())
        assertEquals(Money.ZERO, queries.spent(all, lineId = 9).first())
    }

    @Test
    fun `combined balance sums each line's latest and falls back to the latest overall`() {
        assertEquals(Money(300), LedgerQueries.combine(listOf(LineBalance(1, 100), LineBalance(2, 200), LineBalance(null, 999))))
        assertEquals(Money(999), LedgerQueries.combine(listOf(LineBalance(null, 999))))
        assertNull(LedgerQueries.combine(emptyList()))
    }

    @Test
    fun `combined balance from the database`() = runTest {
        ingest(Sms.KPLC, Sms.SEND) // both unattributed: the later by occurredAt (KPLC, balance 2,000.00) wins
        assertEquals(Money(200_000), queries.combinedBalance())
    }

    @Test
    fun `the transactions list searches names, codes and amount spellings and hides hidden rows`() = runTest {
        ingest(Sms.KPLC, Sms.SEND, Sms.buyGoods("TJK4AB12HD", "SAMPLE 50% SHOP", "120.00"))
        deriver.saveOverride(Override("TJK4AB12FB", hidden = true))
        suspend fun codes(q: String?): List<String> {
            val page = queries.transactions(null, q).load(PagingSource.LoadParams.Refresh(null, 50, false)) as PagingSource.LoadResult.Page
            return page.data.map { it.code }
        }
        assertEquals(listOf("TJK4AB12HD", "TJK4AB12FA"), codes(null), "newest first; the hidden send is absent")
        assertEquals(listOf("TJK4AB12FA"), codes("  KPLC "))
        assertEquals(listOf("TJK4AB12FA"), codes("1,000"))
        assertEquals(listOf("TJK4AB12FA"), codes("1000.00"))
        assertEquals(listOf("TJK4AB12FA"), codes("tjk4ab12fa"))
        assertEquals(listOf("TJK4AB12HD"), codes("50%"), "% is literal, not a wildcard")
    }
}
