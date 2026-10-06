package com.ledga.app.data.edit

import androidx.paging.PagingSource
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.LineRow
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.room.SmsStatus
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.core.derive.RuleAction
import com.ledga.core.derive.RuleField
import com.ledga.core.derive.RuleOrigin
import com.ledga.core.model.Categories
import com.ledga.core.model.CategoryGroup
import com.ledga.core.model.FlowKind
import com.ledga.core.money.Money
import com.ledga.core.time.InstantRange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Spec §7.4: what a person changes on a transaction, and exactly what each change touches (R35–R37, R43, R46). */
@RunWith(RobolectricTestRunner::class)
class TransactionEditsTest {
    private val db = TestDb.inMemory()
    private val clock = Clock.fixed(Instant.parse("2026-04-03T06:00:00Z"), ZoneOffset.UTC)
    private val deriver = Deriver(db, clock)
    private val edits = TransactionEdits(db, deriver, clock)
    private val ledger = LedgerQueries(db)
    private val everything = InstantRange(Instant.parse("2026-01-01T00:00:00Z"), null)

    @After fun close() = db.close()

    private suspend fun ingest(vararg bodies: String) =
        SmsIngestor(db, deriver).ingestAll(bodies.map { RawSms("MPESA", it, clock.instant(), null, null, SmsSource.INBOX) })

    private suspend fun tx(code: String) = db.transactionsDao().get(code)!!

    private suspend fun userRules() = db.rulesDao().all().filter { it.origin == RuleOrigin.USER }

    private val academy1024 = Sms.paybill("TJK4AB12JA", "SAMPLE ACADEMY", "ADM 1024", "5,000.00")
    private val academy2048 = Sms.paybill("TJK4AB12JB", "SAMPLE ACADEMY", "ADM 2048", "4,000.00", "23/3/26 at 9:00 AM")
    private val clinic1024 = Sms.paybill("TJK4AB12JC", "SAMPLE CLINIC", "ADM 1024", "1,500.00")

    @Test
    fun `apply to all from a name files every payment from them, and the next one too`() = runTest {
        ingest(academy1024, academy2048, clinic1024)
        assertEquals(ApplyCounts(fromName = 2, forAccount = 1), edits.categoryCounts("TJK4AB12JA", Categories.SCHOOL))
        edits.setCategory("TJK4AB12JA", Categories.SCHOOL, ApplyTo.ALL_FROM_NAME)
        assertEquals(Categories.SCHOOL, tx("TJK4AB12JA").categoryKey)
        assertEquals(Categories.SCHOOL, tx("TJK4AB12JB").categoryKey)
        assertEquals(Categories.OTHER, tx("TJK4AB12JC").categoryKey, "another business keeps its own")
        ingest(Sms.paybill("TJK4AB12JD", "SAMPLE ACADEMY", "ADM 1024", "5,000.00", "24/3/26 at 9:00 AM"))
        assertEquals(Categories.SCHOOL, tx("TJK4AB12JD").categoryKey, "future payments follow the rule")
        val rule = userRules().single()
        assertEquals(RuleField.NAME_CONTAINS, rule.field)
        assertEquals("SAMPLE ACADEMY", rule.pattern)
    }

    @Test
    fun `the count is exactly what changes - a hand-filed payment joins, an own-account move and a receipt keep theirs`() = runTest {
        ingest(
            Sms.send("TJK4AB12KA", "500.00", "21/3/26 at 1:30 PM"),
            Sms.send("TJK4AB12KB", "600.00", "22/3/26 at 1:30 PM"),
            Sms.send("TJK4AB12KC", "700.00", "23/3/26 at 1:30 PM"),
            Sms.receive("TJK4AB12KD", "JANE TESTER 0712345111", "900.00"),
        )
        edits.setCategory("TJK4AB12KB", Categories.RENT, ApplyTo.THIS_ONE)
        edits.setOwnAccount("TJK4AB12KC", own = true, allFromName = false)
        assertEquals(2, edits.categoryCounts("TJK4AB12KA", Categories.HEALTH).fromName)
        edits.setCategory("TJK4AB12KA", Categories.HEALTH, ApplyTo.ALL_FROM_NAME)
        assertEquals(Categories.HEALTH, tx("TJK4AB12KA").categoryKey)
        assertEquals(Categories.HEALTH, tx("TJK4AB12KB").categoryKey, "apply to all means all of them")
        assertEquals(Categories.OWN_ACCOUNTS, tx("TJK4AB12KC").categoryKey, "an own-account move stays out of spending")
        assertEquals(Categories.RECEIVED, tx("TJK4AB12KD").categoryKey, "money in never takes a spending category")
    }

    @Test
    fun `only this account number keeps the rule to that business and that account`() = runTest {
        ingest(academy1024, academy2048, clinic1024)
        edits.setCategory("TJK4AB12JA", Categories.SCHOOL, ApplyTo.THIS_ACCOUNT)
        assertEquals(Categories.SCHOOL, tx("TJK4AB12JA").categoryKey)
        assertEquals(Categories.OTHER, tx("TJK4AB12JB").categoryKey, "another account at the same school")
        assertEquals(Categories.OTHER, tx("TJK4AB12JC").categoryKey, "the same account number at a clinic")
        assertEquals(RuleField.NAME_AND_ACCOUNT, userRules().single().field)
    }

    @Test
    fun `a second apply-all for the same name replaces the first rule`() = runTest {
        ingest(academy1024, academy2048)
        edits.setCategory("TJK4AB12JA", Categories.SCHOOL, ApplyTo.ALL_FROM_NAME)
        edits.setCategory("TJK4AB12JA", Categories.HEALTH, ApplyTo.ALL_FROM_NAME)
        assertEquals(listOf(Categories.HEALTH), userRules().map { it.categoryKey })
        assertEquals(Categories.HEALTH, tx("TJK4AB12JB").categoryKey)
    }

    @Test
    fun `my own account for all from a bank app takes its transfers out of money in`() = runTest {
        ingest(Sms.BANK_APP, Sms.receive("TJK4AB12LA", "EXAMPLE BANK LIMITED- APP", "3,000.00"))
        assertEquals(2, edits.ownAccountCount("TJK4AB12FC", own = true))
        edits.setOwnAccount("TJK4AB12FC", own = true, allFromName = true)
        assertEquals(FlowKind.OWN_IN, tx("TJK4AB12FC").flow)
        assertEquals(FlowKind.OWN_IN, tx("TJK4AB12LA").flow)
        assertEquals(Money.ZERO, ledger.moneyIn(everything).first())
        assertEquals(RuleAction.MARK_OWN_ACCOUNT, userRules().single().action)
    }

    @Test
    fun `turning own-account off for all removes the rule, and just this one leaves an exception`() = runTest {
        ingest(Sms.BANK_APP, Sms.receive("TJK4AB12LA", "EXAMPLE BANK LIMITED- APP", "3,000.00"))
        edits.setOwnAccount("TJK4AB12FC", own = true, allFromName = true)
        edits.setOwnAccount("TJK4AB12LA", own = false, allFromName = false)
        assertEquals(FlowKind.INCOME, tx("TJK4AB12LA").flow)
        assertEquals(FlowKind.OWN_IN, tx("TJK4AB12FC").flow)
        edits.setOwnAccount("TJK4AB12FC", own = false, allFromName = true)
        assertEquals(FlowKind.INCOME, tx("TJK4AB12FC").flow)
        assertEquals(FlowKind.INCOME, tx("TJK4AB12LA").flow)
        assertTrue(userRules().isEmpty())
    }

    @Test
    fun `hide and undo - a hidden payment leaves the list and the totals, and its SMS stays`() = runTest {
        ingest(Sms.KPLC, Sms.SEND)
        val before = ledger.spent(everything).first()
        edits.setHidden("TJK4AB12FA", true)
        assertTrue(tx("TJK4AB12FA").isHidden)
        assertEquals(before - Money(100_000), ledger.spent(everything).first())
        assertEquals(2, db.smsDao().countByStatus(SmsStatus.PARSED), "hiding never touches the SMS")
        edits.setHidden("TJK4AB12FA", false)
        assertEquals(before, ledger.spent(everything).first())
    }

    @Test
    fun `notes are tidied, a blank one clears it, and a note is searchable`() = runTest {
        ingest(Sms.SEND)
        edits.setNote("TJK4AB12FB", "  school   fees\n march ")
        assertEquals("school fees march", tx("TJK4AB12FB").note)
        val page = ledger.transactions(TransactionFilter(query = "fees")).load(PagingSource.LoadParams.Refresh(null, 10, false)) as PagingSource.LoadResult.Page
        assertEquals(listOf("TJK4AB12FB"), page.data.map { it.code })
        edits.setNote("TJK4AB12FB", "   ")
        assertNull(tx("TJK4AB12FB").note)
    }

    @Test
    fun `a new category is made once per name in its group`() = runTest {
        val key = edits.createCategory("  Church  ", CategoryGroup.EVERYDAY)
        assertEquals("user_church", key)
        assertEquals(key, edits.createCategory("church", CategoryGroup.EVERYDAY), "the same name in the same group")
        assertEquals("user_church_2", edits.createCategory("Church", CategoryGroup.MONEY_IN))
        val row = db.categoriesDao().all().single { it.key == key }
        assertEquals("Church", row.name)
        assertEquals(CategoryOrigin.USER, row.origin)
        assertEquals(TransactionEdits.NEW_CATEGORY_ICON, row.icon3d)
        assertFalse(row.tracked)
    }

    @Test
    fun `a payment can move to another line and back to its own`() = runTest {
        val personal = db.linesDao().insert(LineRow(subscriptionId = 1, phoneNumber = null, displayName = "Personal", color = "#0E9F6E", isPrimary = true, createdAt = clock.instant()))
        val business = db.linesDao().insert(LineRow(subscriptionId = 2, phoneNumber = null, displayName = "Business", color = "#1E7FD8", isPrimary = false, createdAt = clock.instant()))
        ingest(Sms.SEND)
        edits.setLine("TJK4AB12FB", business)
        assertEquals(business, tx("TJK4AB12FB").lineId)
        edits.setLine("TJK4AB12FB", personal)
        assertEquals(personal, tx("TJK4AB12FB").lineId)
        edits.setLine("TJK4AB12FB", null)
        assertNull(tx("TJK4AB12FB").lineId, "null goes back to the line its SMS arrived on (none here)")
    }
}
