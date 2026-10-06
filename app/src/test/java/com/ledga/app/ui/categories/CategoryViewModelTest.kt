package com.ledga.app.ui.categories

import androidx.lifecycle.SavedStateHandle
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.data.edit.RemovedRule
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.SmsSource
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.TestViewModels
import com.ledga.app.ui.activity.ActivityLink
import com.ledga.app.ui.activity.ActivityLinks
import com.ledga.core.derive.RuleOrigin
import com.ledga.core.model.Categories
import com.ledga.core.model.CategoryGroup
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R67, R72–R74: one category's screen. Synthetic payments. */
@RunWith(RobolectricTestRunner::class)
class CategoryViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-03-25T06:00:00Z"))
    private val deriver = Deriver(db, clock)
    private val edits = TransactionEdits(db, deriver, clock)
    private val links = ActivityLinks()
    private val vms = TestViewModels()

    private fun vm(key: String) = vms.track(CategoryViewModel(SavedStateHandle(mapOf("categoryKey" to key)), db, edits, links))

    @After fun close() {
        vms.stopAll()
        db.close()
    }

    private suspend fun ingest(vararg bodies: String) =
        SmsIngestor(db, deriver).ingestAll(bodies.map { RawSms("MPESA", it, clock.instant(), null, null, SmsSource.INBOX) })

    @Test
    fun `a built-in category lists every rule filing into it, a switched-off one too, and switches it back on`() = runTest {
        val vm = vm(Categories.ELECTRICITY)
        val kplc = vm.ui.first { it.loaded }.rules.first { it.label == "Name has KPLC" }
        assertTrue(kplc.builtIn)
        vm.setRuleEnabled(kplc.id, false)
        assertFalse(vm.ui.first { ui -> ui.rules.any { it.id == kplc.id && !it.enabled } }.rules.first { it.id == kplc.id }.enabled)
        vm.setRuleEnabled(kplc.id, true)
        assertTrue(vm.ui.first { ui -> ui.rules.any { it.id == kplc.id && it.enabled } }.rules.first { it.id == kplc.id }.enabled)
    }

    @Test
    fun `deleting your rule comes with Undo that files its payments back (R73)`() = runTest {
        ingest(Sms.paybill("TJK4AB12RC", "SAMPLE ACADEMY", "ADM 1024", "5,000.00"))
        edits.addRule(Categories.SCHOOL, "SAMPLE ACADEMY", null)
        edits.setTracked(Categories.SCHOOL, true)
        val vm = vm(Categories.SCHOOL)
        val yours = vm.ui.first { it.rules.isNotEmpty() }.rules.single { !it.builtIn }
        val removed = CompletableDeferred<RemovedRule>()
        vm.deleteRule(yours.id) { removed.complete(it) }
        removed.await()
        assertEquals(Categories.OTHER, db.transactionsDao().get("TJK4AB12RC")!!.categoryKey)
        assertTrue(vm.ui.first { ui -> ui.rules.none { !it.builtIn } }.category!!.tracked, "the last rule gone, the tracker stays")
        vm.restoreRule(removed.await())
        assertEquals(Categories.SCHOOL, db.transactionsDao().observe("TJK4AB12RC").first { it?.categoryKey == Categories.SCHOOL }!!.categoryKey)
    }

    @Test
    fun `what each kind of category offers`() = runTest {
        val wedding = edits.createCategory("Wedding", CategoryGroup.EVERYDAY)
        val own = vm(wedding).ui.first { it.loaded }
        assertTrue(own.own && own.canTrack && own.canAddRule)
        val received = vm(Categories.RECEIVED).ui.first { it.loaded }
        assertFalse(received.own)
        assertFalse(received.canTrack, "Money in always spends 0 (R50)")
        assertFalse(vm(Categories.OWN_ACCOUNTS).ui.first { it.loaded }.canAddRule, "own-account rules come from a payment's switch")
        assertTrue(vm("no_such_category").ui.first { it.loaded }.missing)
    }

    @Test
    fun `See payments opens Transactions on this category`() = runTest {
        vm(Categories.FUEL).openPayments()
        assertEquals(ActivityLink.Transactions(TransactionFilter(categoryKeys = setOf(Categories.FUEL))), links.requests.value)
    }
}
