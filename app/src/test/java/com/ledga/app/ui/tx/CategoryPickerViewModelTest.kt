package com.ledga.app.ui.tx

import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.edit.ApplyTo
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.SmsSource
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.TestViewModels
import com.ledga.core.derive.RuleField
import com.ledga.core.derive.RuleOrigin
import com.ledga.core.model.Categories
import com.ledga.core.model.CategoryGroup
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoryPickerViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = TestDb.inMemory()
    private val clock = Clock.fixed(Instant.parse("2026-04-03T06:00:00Z"), ZoneOffset.UTC)
    private val deriver = Deriver(db, clock)
    private val edits = TransactionEdits(db, deriver, clock)

    private val vms = TestViewModels()

    private fun vm() = vms.track(CategoryPickerViewModel(db, edits, deriver))

    @After fun close() {
        vms.stopAll()
        db.close()
    }

    private suspend fun ingest(vararg bodies: String) =
        SmsIngestor(db, deriver).ingestAll(bodies.map { RawSms("MPESA", it, clock.instant(), null, null, SmsSource.INBOX) })

    private val academy1024 = Sms.paybill("TJK4AB12JA", "SAMPLE ACADEMY", "ADM 1024", "5,000.00")
    private val academy1024Again = Sms.paybill("TJK4AB12JD", "SAMPLE ACADEMY", "ADM 1024", "5,000.00", "24/3/26 at 9:00 AM")
    private val academy2048 = Sms.paybill("TJK4AB12JB", "SAMPLE ACADEMY", "ADM 2048", "4,000.00", "23/3/26 at 9:00 AM")

    @Test
    fun `a payment out is offered only spending categories, grouped in order, with its own selected`() = runTest {
        ingest(Sms.KPLC)
        val vm = vm()
        vm.open("TJK4AB12FA")
        val s = vm.state.first { it.loaded }
        assertEquals(listOf(CategoryGroup.BILLS_UTILITIES, CategoryGroup.CAR, CategoryGroup.EVERYDAY, CategoryGroup.MONEY), s.groups.map { it.group })
        assertEquals(Categories.ELECTRICITY, s.selected)
        assertTrue(s.groups.first().items.single { it.key == Categories.ELECTRICITY }.tracked)
    }

    @Test
    fun `money in is offered only money-in categories`() = runTest {
        ingest(Sms.receive("TJK4AB12HC", "SAMPLE EMPLOYER LTD", "20,000.00"))
        val vm = vm()
        vm.open("TJK4AB12HC")
        assertEquals(listOf(CategoryGroup.MONEY_IN), vm.state.first { it.loaded }.groups.map { it.group })
    }

    @Test
    fun `apply to all defaults on for more than one, off for one, and is absent with no name`() = runTest {
        ingest(academy1024, academy2048, Sms.SEND, Sms.REPAY_FULL)

        val many = vm()
        many.open("TJK4AB12JA")
        many.state.first { it.loaded }
        many.select(Categories.SCHOOL)
        val m = many.state.first { it.selected == Categories.SCHOOL && it.counts.fromName == 2 }
        assertTrue(m.showApplyAll)
        assertTrue(m.applyAll, "two payments: on by default")
        assertEquals(2, m.applyCount)

        val one = vm()
        one.open("TJK4AB12FB")
        one.state.first { it.loaded }
        one.select(Categories.HEALTH)
        val o = one.state.first { it.selected == Categories.HEALTH && it.counts.fromName == 1 }
        assertTrue(o.showApplyAll, "a rule still helps future payments")
        assertFalse(o.applyAll, "one payment: off by default")

        val none = vm()
        none.open("TJK4AB12FF")
        assertFalse(none.state.first { it.loaded }.showApplyAll, "a Fuliza repayment names no one")
    }

    @Test
    fun `saving with apply to all off changes only this payment, and on writes the rule`() = runTest {
        ingest(academy1024, academy2048)
        val single = vm()
        single.open("TJK4AB12JA")
        single.state.first { it.loaded }
        single.select(Categories.SCHOOL)
        single.state.first { it.selected == Categories.SCHOOL && it.counts.fromName == 2 }
        single.setApplyAll(false)
        val saved = CompletableDeferred<Unit>()
        single.save { saved.complete(Unit) }
        saved.await()
        assertEquals(Categories.SCHOOL, db.transactionsDao().get("TJK4AB12JA")?.categoryKey)
        assertEquals(Categories.OTHER, db.transactionsDao().get("TJK4AB12JB")?.categoryKey, "only this one")
        assertTrue(db.rulesDao().all().none { it.origin == RuleOrigin.USER })

        val all = vm()
        all.open("TJK4AB12JB")
        all.state.first { it.loaded }
        all.select(Categories.HEALTH)
        all.state.first { it.selected == Categories.HEALTH && it.counts.fromName == 2 && it.applyAll }
        val savedAll = CompletableDeferred<Unit>()
        all.save { savedAll.complete(Unit) }
        savedAll.await()
        assertEquals(Categories.HEALTH, db.transactionsDao().get("TJK4AB12JA")?.categoryKey, "the hand-filed one joins")
        assertEquals(Categories.HEALTH, db.transactionsDao().get("TJK4AB12JB")?.categoryKey)
        assertEquals(RuleField.NAME_CONTAINS, db.rulesDao().all().single { it.origin == RuleOrigin.USER }.field)
    }

    @Test
    fun `only this account number counts that account's payments`() = runTest {
        ingest(academy1024, academy1024Again, academy2048)
        val vm = vm()
        vm.open("TJK4AB12JA")
        vm.state.first { it.loaded }
        vm.select(Categories.SCHOOL)
        val s = vm.state.first { it.selected == Categories.SCHOOL && it.counts.forAccount == 2 }
        assertEquals(3, s.counts.fromName)
        assertEquals("ADM 1024", s.account)
        assertTrue(s.showAccountOnly)
        vm.setAccountOnly(true)
        assertEquals(2, vm.state.value.applyCount)
        assertEquals(ApplyTo.THIS_ACCOUNT, vm.state.value.applyTo)
    }

    @Test
    fun `a new category appears in its group, selected`() = runTest {
        ingest(Sms.SEND)
        val vm = vm()
        vm.open("TJK4AB12FB")
        vm.state.first { it.loaded }
        vm.startNewCategory(CategoryGroup.EVERYDAY)
        vm.createCategory("Church")
        val s = vm.state.first { it.selected == "user_church" }
        assertNull(s.newCategoryIn)
        assertTrue(s.groups.single { it.group == CategoryGroup.EVERYDAY }.items.any { it.name == "Church" })
    }

    @Test
    fun `search narrows the categories by name`() = runTest {
        ingest(Sms.KPLC)
        val vm = vm()
        vm.open("TJK4AB12FA")
        vm.state.first { it.loaded }
        vm.setQuery("wat")
        assertEquals(
            listOf(PickerGroup(CategoryGroup.BILLS_UTILITIES, listOf(PickerItem(Categories.WATER, "Water", "fluent_droplet", tracked = true)))),
            vm.state.value.visibleGroups,
        )
    }

    @Test
    fun `saving the picker unchanged changes nothing - no rule, and a hand-filed payment keeps its category`() = runTest {
        ingest(
            Sms.send("TJK4AB12KA", "500.00", "21/3/26 at 1:30 PM"),
            Sms.send("TJK4AB12KB", "600.00", "22/3/26 at 1:30 PM"),
            Sms.send("TJK4AB12KC", "700.00", "23/3/26 at 1:30 PM"),
        )
        edits.setCategory("TJK4AB12KB", Categories.RENT, ApplyTo.THIS_ONE)
        val vm = vm()
        vm.open("TJK4AB12KA")
        val s = vm.state.first { it.loaded && it.counts.fromName > 0 }
        assertEquals(Categories.SENT_TO_PEOPLE, s.selected)
        assertFalse(s.applyAll, "the current category: apply to all starts off")
        val saved = CompletableDeferred<Unit>()
        vm.save { saved.complete(Unit) }
        saved.await()
        assertEquals(Categories.RENT, db.transactionsDao().get("TJK4AB12KB")?.categoryKey, "the hand-filed payment keeps its category")
        assertTrue(db.rulesDao().all().none { it.origin == RuleOrigin.USER }, "no rule")
        assertNull(db.overridesDao().get("TJK4AB12KA")?.categoryKey, "nothing pinned on the payment itself")
    }
}
