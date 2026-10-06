package com.ledga.app.data.derive

import androidx.paging.PagingSource
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.LineRow
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.room.dao.DayTotal
import com.ledga.app.data.room.dao.MonthTotal
import com.ledga.app.data.room.dao.PeriodTotals
import com.ledga.app.data.room.dao.PersonSummary
import com.ledga.app.data.room.dao.PersonTotal
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.core.derive.Override
import com.ledga.core.model.Categories
import com.ledga.core.parse.Counterparty
import com.ledga.core.time.InstantRange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals

/** Activity's reads (spec §7.5): the list and its day totals (R38), People (R42), Spending's buckets (R45). */
@RunWith(RobolectricTestRunner::class)
class ActivityQueriesTest {
    private val db = TestDb.inMemory()
    private val deriver = Deriver(db)
    private val queries = LedgerQueries(db)
    private val received = Instant.parse("2026-07-01T06:00:00Z")

    /** March 2026 in Nairobi: 1 March 00:00 to 1 April 00:00, UTC+3 (closed: the test's "today" is in July). */
    private val march = DateFilter.Month(YearMonth.of(2026, 3))
    private val today = LocalDate.parse("2026-07-01")

    @After fun close() = db.close()

    private suspend fun ingest(vararg bodies: String, lineId: Long? = null) =
        SmsIngestor(db, deriver).ingestAll(bodies.map { RawSms("MPESA", it, received, null, lineId, SmsSource.INBOX) })

    private suspend fun line(name: String, subscriptionId: Int): Long = db.linesDao().insert(
        LineRow(subscriptionId = subscriptionId, phoneNumber = null, displayName = name, color = "#0E9F6E", isPrimary = subscriptionId == 1, createdAt = received),
    )

    private suspend fun codes(filter: TransactionFilter): List<String> {
        val page = queries.transactions(filter, today).load(PagingSource.LoadParams.Refresh(null, 100, false)) as PagingSource.LoadResult.Page
        return page.data.map { it.code }
    }

    @Test
    fun `the list follows the chips, the line, categories, dates, the minimum and hidden`() = runTest {
        val personal = line("Personal", 1)
        val business = line("Business", 2)
        ingest(
            Sms.KPLC,
            Sms.receive("TJK4AB12HC", "SAMPLE EMPLOYER LTD", "20,000.00"),
            Sms.send("TJK4AB12HE", "50.00", "22/3/26 at 9:00 AM"),
            Sms.PURCHASE,
            Sms.COMPANION,
            lineId = personal,
        )
        ingest(Sms.SEND, lineId = business)
        deriver.saveOverride(Override("TJK4AB12HE", hidden = true))
        val all = TransactionFilter()
        assertEquals(listOf("TJK4AB12EA", "TJK4AB12HC", "TJK4AB12FA", "TJK4AB12FB"), codes(all), "newest first; the hidden send is absent")
        assertEquals(listOf("TJK4AB12EA", "TJK4AB12FA", "TJK4AB12FB"), codes(all.copy(flow = FlowFilter.OUT)))
        assertEquals(listOf("TJK4AB12HC"), codes(all.copy(flow = FlowFilter.IN)))
        assertEquals(listOf("TJK4AB12EA"), codes(all.copy(flow = FlowFilter.FULIZA)), "the payment Fuliza helped with")
        assertEquals(listOf("TJK4AB12FB"), codes(all.copy(lineId = business)))
        assertEquals(listOf("TJK4AB12FA"), codes(all.copy(categoryKeys = setOf(Categories.ELECTRICITY))))
        assertEquals(listOf("TJK4AB12HC", "TJK4AB12FA", "TJK4AB12FB"), codes(all.copy(dates = march)))
        assertEquals(listOf("TJK4AB12EA", "TJK4AB12HC", "TJK4AB12FA"), codes(all.copy(minAmountCents = 100_000)))
        assertEquals(
            listOf("TJK4AB12EA", "TJK4AB12HC", "TJK4AB12HE", "TJK4AB12FA", "TJK4AB12FB"),
            codes(all.copy(includeHidden = true)),
            "Show hidden payments lists them",
        )
    }

    @Test
    fun `day totals sum the shown rows by Nairobi day, with 23-59 and 00-00 on different days`() = runTest {
        ingest(
            Sms.send("TJK4AB12HA", "500.00", "21/3/26 at 11:59 PM"),
            Sms.send("TJK4AB12HB", "200.00", "22/3/26 at 12:00 AM"),
            Sms.receive("TJK4AB12HC", "SAMPLE EMPLOYER LTD", "20,000.00", "21/3/26 at 9:00 AM"),
        )
        val d21 = LocalDate.parse("2026-03-21")
        val d22 = LocalDate.parse("2026-03-22")
        assertEquals(
            mapOf(d21 to DayTotal(d21.toEpochDay(), 50_700, 2_000_000), d22 to DayTotal(d22.toEpochDay(), 20_700, 0)),
            queries.dayTotals(TransactionFilter()).first(),
            "Out is spent including fees; In is money in",
        )
        val moneyIn = queries.dayTotals(TransactionFilter(flow = FlowFilter.IN)).first()
        assertEquals(setOf(d21), moneyIn.keys, "a header follows the list's own filter")
        assertEquals(2_000_000, moneyIn.getValue(d21).inCents)
    }

    @Test
    fun `people group sends and receipts by person, and a reversed send drops out`() = runTest {
        ingest(
            Sms.SEND,
            Sms.send("TJK4AB12HF", "300.00", "23/3/26 at 9:00 AM"),
            Sms.REVERSAL,
            Sms.KPLC,
            Sms.receive("TJK4AB12HC", "SAMPLE EMPLOYER LTD", "20,000.00"),
        )
        val jane = Counterparty("JANE TESTER", "0712345111", null, null).key!!
        assertEquals(
            listOf(PersonTotal(jane, "JANE TESTER", "0712345111", 1, 30_000, Instant.parse("2026-03-23T06:00:00Z"))),
            queries.people(PeopleDirection.SENT).first(),
            "the reversed 500 is gone, and a paybill isn't a person",
        )
        assertEquals(listOf("SAMPLE EMPLOYER LTD"), queries.people(PeopleDirection.RECEIVED).first().map { it.name })
        assertEquals(PersonSummary(sentCents = 30_000, sentCount = 1, receivedCents = 0, receivedCount = 0), queries.personSummary(jane).first())
    }

    @Test
    fun `monthly spending buckets by the Nairobi month, and period totals split out fees and money in`() = runTest {
        ingest(
            Sms.send("TJK4AB12HA", "100.00", "31/3/26 at 11:59 PM"),
            Sms.send("TJK4AB12HB", "200.00", "1/4/26 at 12:00 AM"),
            Sms.receive("TJK4AB12HC", "SAMPLE EMPLOYER LTD", "20,000.00"),
        )
        assertEquals(
            mapOf("2026-03" to MonthTotal("2026-03", 10_700, 1), "2026-04" to MonthTotal("2026-04", 20_700, 1)),
            queries.spentByMonth(InstantRange(Instant.parse("2026-01-01T00:00:00Z"), null)).first(),
        )
        assertEquals(PeriodTotals(spentCents = 10_700, feeCents = 700, inCents = 2_000_000), queries.totals(march.range(today)).first())
    }
}
