package com.ledga.app.data.edit

import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.paging.PagingSource
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.LineRow
import com.ledga.app.data.room.RuleRow
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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

    @Test
    fun `the own-account count is how many change, not how many end up`() = runTest {
        ingest(
            Sms.send("TJK4AB12KA", "500.00", "21/3/26 at 1:30 PM"),
            Sms.send("TJK4AB12KB", "600.00", "22/3/26 at 1:30 PM"),
            Sms.send("TJK4AB12KC", "700.00", "23/3/26 at 1:30 PM"),
        )
        edits.setOwnAccount("TJK4AB12KA", own = true, allFromName = false)
        assertEquals(1, edits.ownAccountCount("TJK4AB12KA", own = false), "switching this one back off changes only it")
        assertEquals(2, edits.ownAccountCount("TJK4AB12KB", own = true), "the one already own doesn't change")
    }

    @Test
    fun `own account off for all from a name leaves other names and the person's broader rule alone`() = runTest {
        ingest(
            Sms.BANK_APP,
            Sms.receive("TJK4AB12LA", "EXAMPLE BANK LIMITED- APP", "3,000.00"),
            Sms.receive("TJK4AB12LB", "EXAMPLE SACCO", "2,000.00"),
        )
        // A broader own-account rule the person already had (as v1's imports bring).
        db.rulesDao().insert(RuleRow(field = RuleField.NAME_CONTAINS, pattern = "EXAMPLE", action = RuleAction.MARK_OWN_ACCOUNT, categoryKey = null, origin = RuleOrigin.USER, priority = 0, createdAt = clock.instant()))
        deriver.reclassifyAll()
        assertEquals(FlowKind.OWN_IN, tx("TJK4AB12LB").flow)
        assertEquals(2, edits.ownAccountCount("TJK4AB12FC", own = false))
        edits.setOwnAccount("TJK4AB12FC", own = false, allFromName = true)
        assertEquals(FlowKind.INCOME, tx("TJK4AB12FC").flow)
        assertEquals(FlowKind.INCOME, tx("TJK4AB12LA").flow)
        assertEquals(FlowKind.OWN_IN, tx("TJK4AB12LB").flow, "another name keeps its own-account state")
        assertEquals(listOf("EXAMPLE"), userRules().map { it.pattern }, "the person's own rule stays")
    }

    private val water501 = Sms.paybill("TJK4AB12WA", "SAMPLE WATER CO", "ACC 501", "1,250.00")
    private val water502 = Sms.paybill("TJK4AB12WB", "SAMPLE WATER CO", "ACC 502", "1,375.00", "23/3/26 at 9:00 AM")
    private val water501Again = Sms.paybill("TJK4AB12WC", "SAMPLE WATER CO", "ACC 501", "1,125.00", "24/3/26 at 9:00 AM")

    @Test
    fun `tracking a category changes no payment`() = runTest {
        ingest(academy1024)
        val before = db.transactionsDao().all()
        edits.setTracked(Categories.SCHOOL, true)
        edits.setTracked(Categories.ELECTRICITY, false)
        val categories = db.categoriesDao().all().associateBy { it.key }
        assertTrue(categories.getValue(Categories.SCHOOL).tracked)
        assertFalse(categories.getValue(Categories.ELECTRICITY).tracked)
        assertEquals(before, db.transactionsDao().all())
    }

    @Test
    fun `a rename shows everywhere and refuses a blank or taken name`() = runTest {
        assertTrue(edits.renameCategory(Categories.ELECTRICITY, "  Power   tokens "))
        assertEquals("Power tokens", db.categoriesDao().all().first { it.key == Categories.ELECTRICITY }.name)
        assertFalse(edits.renameCategory(Categories.ELECTRICITY, "   "))
        assertFalse(edits.renameCategory(Categories.ELECTRICITY, "water"), "Water is already in Bills & utilities")
        assertTrue(edits.renameCategory(Categories.FUEL, "Water"), "another group may use the name")
        assertFalse(edits.renameCategory("no_such_category", "Anything"))
    }

    @Test
    fun `the add-rule preview counts what moves and the hand-filed ones among them, and saving moves exactly those`() = runTest {
        ingest(water501, water502, water501Again)
        edits.setCategory("TJK4AB12WB", Categories.RENT, ApplyTo.THIS_ONE) // filed by hand
        assertEquals(RulePreview(matches = 3, moving = 3, handFiled = 1), edits.rulePreview(Categories.WATER, "sample water", null))
        assertNull(edits.rulePreview(Categories.WATER, "s", null), "one letter would match almost anything")
        assertEquals(RulePreview(0, 0, 0), edits.rulePreview(Categories.WATER, "NOBODY YET", null), "a rule for future payments")
        assertTrue(edits.addRule(Categories.WATER, "sample water", null))
        assertEquals(List(3) { Categories.WATER }, listOf("TJK4AB12WA", "TJK4AB12WB", "TJK4AB12WC").map { tx(it).categoryKey })
        assertNull(db.overridesDao().get("TJK4AB12WB")?.categoryKey, "the hand-filed choice was cleared, as the preview said")
        val rule = userRules().single()
        assertEquals(RuleField.NAME_CONTAINS, rule.field)
        assertEquals("SAMPLE WATER", rule.pattern, "stored like M-Pesa's names")
        assertEquals(RulePreview(3, 0, 0), edits.rulePreview(Categories.WATER, "SAMPLE WATER", null), "nothing left to move")
        assertFalse(edits.addRule(Categories.WATER, " x ", null))
    }

    @Test
    fun `an account narrows the new rule to that business's account`() = runTest {
        ingest(water501, water502, water501Again)
        assertEquals(RulePreview(2, 2, 0), edits.rulePreview(Categories.WATER, "SAMPLE WATER CO", "ACC 501"))
        edits.addRule(Categories.WATER, "SAMPLE WATER CO", "ACC 501")
        assertEquals(Categories.WATER, tx("TJK4AB12WC").categoryKey)
        assertEquals(Categories.OTHER, tx("TJK4AB12WB").categoryKey, "another account at the same business")
        assertEquals(RuleField.NAME_AND_ACCOUNT, userRules().single().field)
    }

    @Test
    fun `removing a rule deletes the person's own and switches a built-in one off, and Undo puts each back`() = runTest {
        ingest(Sms.KPLC, water501)
        assertEquals(Categories.ELECTRICITY, tx("TJK4AB12FA").categoryKey)
        val kplc = db.rulesDao().all().first { it.origin == RuleOrigin.SYSTEM && it.pattern == "KPLC" }
        val off = edits.removeRule(kplc.id)!!
        assertFalse(db.rulesDao().get(kplc.id)!!.enabled, "switched off, not deleted (spec §7.1)")
        assertEquals(Categories.OTHER, tx("TJK4AB12FA").categoryKey)
        edits.restoreRule(off)
        assertTrue(db.rulesDao().get(kplc.id)!!.enabled)
        assertEquals(Categories.ELECTRICITY, tx("TJK4AB12FA").categoryKey)

        edits.addRule(Categories.WATER, "SAMPLE WATER", null)
        val removed = edits.removeRule(userRules().single().id)!!
        assertTrue(userRules().isEmpty())
        assertEquals(Categories.OTHER, tx("TJK4AB12WA").categoryKey)
        edits.restoreRule(removed)
        assertEquals("SAMPLE WATER", userRules().single().pattern)
        assertEquals(Categories.WATER, tx("TJK4AB12WA").categoryKey)
        assertNull(edits.removeRule(Long.MAX_VALUE))
    }

    @Test
    fun `a second edit waits until the first has finished`() = runBlocking {
        // Real threads: Robolectric fakes System.nanoTime, so coroutine timeouts never fire here; Thread.sleep is real.
        ingest(academy1024, academy2048)
        val inside = CountDownLatch(1)
        val release = CountDownLatch(1)
        // The first time an edit reads the clock (building its rule), it pauses there, holding whatever the edit holds.
        val pausing = object : Clock() {
            @Volatile var first = true
            override fun instant(): Instant {
                if (first) {
                    first = false
                    inside.countDown()
                    release.await()
                }
                return clock.instant()
            }
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
        }
        val slow = TransactionEdits(db, deriver, pausing)
        // The paused edit gets a thread of its own, so it can't starve the pool the second edit and Room run on.
        val own = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {
            val first = launch(own) { slow.setCategory("TJK4AB12JA", Categories.SCHOOL, ApplyTo.ALL_FROM_NAME) }
            inside.await()
            val second = async(Dispatchers.IO) { slow.setNote("TJK4AB12JB", "later") }
            Thread.sleep(1_000)
            val finishedEarly = second.isCompleted
            release.countDown()
            assertFalse(finishedEarly, "the note waits while the category edit is half done (R63)")
            first.join()
            second.await()
            assertEquals("later", tx("TJK4AB12JB").note)
            assertEquals(Categories.SCHOOL, tx("TJK4AB12JB").categoryKey)
        } finally {
            release.countDown() // a failed assertion must not leave the first edit parked
            own.close()
        }
    }

    @Test
    fun `an edit finishes even when the screen that asked for it closes partway (R63)`() = runBlocking {
        // Leaving Tracker detail cancels its ViewModel's scope; a rule written without its re-classify would leave the
        // payments filed by the old rules, with no Undo offered. Real threads, as above.
        ingest(water501, water502, water501Again)
        val inside = CountDownLatch(1)
        val release = CountDownLatch(1)
        // The second clock read is inside the rule's own write, after the rule is built: the edit pauses there.
        val pausing = object : Clock() {
            private val reads = AtomicInteger()
            override fun instant(): Instant {
                if (reads.incrementAndGet() == 2) {
                    inside.countDown()
                    release.await()
                }
                return clock.instant()
            }
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
        }
        val slow = TransactionEdits(db, deriver, pausing)
        val own = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {
            val edit = launch(own) { slow.addRule(Categories.WATER, "sample water", null) }
            inside.await()
            edit.cancel()
            release.countDown()
            edit.join()
            assertEquals("SAMPLE WATER", userRules().single().pattern)
            assertEquals(
                List(3) { Categories.WATER },
                listOf("TJK4AB12WA", "TJK4AB12WB", "TJK4AB12WC").map { tx(it).categoryKey },
                "the rule's payments are filed by it, not left half done",
            )
        } finally {
            release.countDown()
            own.close()
        }
    }
}
