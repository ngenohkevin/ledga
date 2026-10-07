package com.ledga.app.ui.categories

import androidx.lifecycle.SavedStateHandle
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.data.edit.CategoryLooks
import com.ledga.app.data.edit.RemovedRule
import com.ledga.app.data.edit.RulePreview
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.trackers.CategoryMeasure
import com.ledga.app.data.trackers.Trackers
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.TestViewModels
import com.ledga.app.testing.selectedLine
import com.ledga.app.testing.twoLines
import com.ledga.app.testing.txRow
import com.ledga.app.time.LiveClock
import com.ledga.app.ui.activity.ActivityLink
import com.ledga.app.ui.activity.ActivityLinks
import com.ledga.core.derive.RuleOrigin
import com.ledga.core.model.CategoryGroup
import com.ledga.core.model.Categories
import com.ledga.core.model.TxKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 4e spec §3.2, D1–D4, D6, R89, R94: a category's page. Synthetic payments. */
@RunWith(RobolectricTestRunner::class)
class CategoryPageViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-10-06T06:00:00Z")) // Tue 6 Oct 2026, 09:00 in Nairobi
    private val deriver = Deriver(db, clock)
    private val edits = TransactionEdits(db, deriver, clock)
    private val links = ActivityLinks()
    private val prefs = FakePrefsStore()
    private val vms = TestViewModels()

    private fun vm(key: String = Categories.ELECTRICITY, month: String? = null) = vms.track(
        CategoryPageViewModel(
            SavedStateHandle(mapOf("categoryKey" to key, "month" to month)), Trackers(db, LedgerQueries(db)), db, selectedLine(db, prefs),
            LiveClock(clock) { awaitCancellation() }, edits, links,
        ),
    )

    @After fun close() {
        vms.stopAll()
        db.close()
    }

    private suspend fun ingest(vararg bodies: String) =
        SmsIngestor(db, deriver).ingestAll(bodies.map { RawSms("MPESA", it, clock.instant(), null, null, SmsSource.INBOX) })

    private fun shop(code: String, at: String, cents: Long, name: String) =
        txRow(code = code, kind = TxKind.BUY_GOODS, at = Instant.parse(at), amountCents = cents, name = name, account = null, categoryKey = Categories.GROCERIES)

    @Test
    fun `twelve months by default, the running one selected, with the tiles, the rules and the payments`() = runTest {
        db.transactionsDao().upsertAll(
            listOf(
                txRow(code = "TJK4AB12UA", at = Instant.parse("2026-09-10T07:00:00Z"), amountCents = 185_000),
                txRow(code = "TJK4AB12UB", at = Instant.parse("2026-10-02T07:00:00Z"), amountCents = 90_000),
            ),
        )
        val ui = vm().ui.first { it.loaded && it.thisMonthCents == 90_000L }
        assertEquals(CategoryMeasure.SPENT, ui.measure)
        assertEquals(12, ui.bars.size)
        assertEquals(11, ui.selectedIndex)
        assertTrue(ui.bars.last().inProgress)
        assertEquals(185_000, ui.lastMonthCents)
        assertEquals(185_000, ui.monthlyAverageCents)
        assertEquals(275_000, ui.yearSoFarCents)
        assertTrue(ui.rules.any { it.label == "Name has KPLC" && it.builtIn })
        assertEquals(listOf("TJK4AB12UB", "TJK4AB12UA"), ui.payments.map { it.code })
        assertEquals(2, ui.paymentCount)
        assertFalse(ui.empty)
    }

    @Test
    fun `All goes year by year after twelve months, with an average per completed year`() = runTest {
        db.transactionsDao().upsertAll(
            listOf(
                txRow(code = "TJK4AB12UC", at = Instant.parse("2024-06-10T07:00:00Z"), amountCents = 100_000),
                txRow(code = "TJK4AB12UD", at = Instant.parse("2025-06-10T07:00:00Z"), amountCents = 300_000),
                txRow(code = "TJK4AB12UE", at = Instant.parse("2026-06-10T07:00:00Z"), amountCents = 500_000),
            ),
        )
        val vm = vm()
        vm.ui.first { it.loaded && it.bars.isNotEmpty() }
        vm.setRange(DetailRange.ALL)
        val all = vm.ui.first { it.range == DetailRange.ALL }
        assertEquals(listOf("2024", "2025", "2026"), all.bars.map { it.label })
        // 2024's history starts in June: 7 months, so it's 7/12 of a year. Ksh 4,000 over 19 months is Ksh 2,526.32 a year.
        assertEquals(252_632, all.averageCents, "the completed years, a first year counted for its months only")
        assertEquals(2, all.selectedIndex)
    }

    @Test
    fun `a month from Spending starts selected, in All when it is older than 12 months`() = runTest {
        db.transactionsDao().upsertAll(
            listOf(
                txRow(code = "TJK4AB12VA", at = Instant.parse("2025-03-10T07:00:00Z"), amountCents = 100_000),
                txRow(code = "TJK4AB12VB", at = Instant.parse("2026-07-10T07:00:00Z"), amountCents = 200_000),
                txRow(code = "TJK4AB12VC", at = Instant.parse("2026-10-02T07:00:00Z"), amountCents = 300_000),
            ),
        )
        val july = vm(month = "2026-07").ui.first { it.loaded && it.bars.isNotEmpty() }
        assertEquals(DetailRange.TWELVE_MONTHS, july.range)
        assertEquals("JUL", july.bars[july.selectedIndex!!].label)
        val march = vm(month = "2025-03").ui.first { it.loaded && it.bars.isNotEmpty() }
        assertEquals(DetailRange.ALL, march.range)
        assertEquals("2025", march.bars[march.selectedIndex!!].label, "All shows years after twelve months: March 2025's year")
        val now = vm(month = "2026-10").ui.first { it.loaded && it.bars.isNotEmpty() }
        assertEquals(11, now.selectedIndex)
    }

    @Test
    fun `an empty month from Spending is selected at Ksh 0`() = runTest {
        db.transactionsDao().upsertAll(
            listOf(
                txRow(code = "TJK4AB12VD", at = Instant.parse("2026-07-10T07:00:00Z"), amountCents = 200_000),
                txRow(code = "TJK4AB12VE", at = Instant.parse("2026-09-10T07:00:00Z"), amountCents = 200_000),
            ),
        )
        val ui = vm(month = "2026-08").ui.first { it.loaded && it.bars.isNotEmpty() }
        val picked = ui.buckets[ui.selectedIndex!!]
        assertEquals("2026-08", picked.period.key)
        assertEquals(0, picked.total.cents)
    }

    @Test
    fun `picking a bar or a range lets go of the month from Spending`() = runTest {
        db.transactionsDao().upsertAll(listOf(txRow(code = "TJK4AB12VF", at = Instant.parse("2026-07-10T07:00:00Z"), amountCents = 200_000)))
        val vm = vm(month = "2026-07")
        vm.ui.first { it.loaded && it.bars.isNotEmpty() }
        vm.setRange(DetailRange.SIX_MONTHS)
        assertEquals(5, vm.ui.first { it.range == DetailRange.SIX_MONTHS }.selectedIndex, "the running month again")
    }

    @Test
    fun `a Money in category's page counts what came in`() = runTest {
        db.transactionsDao().upsertAll(
            listOf(txRow(code = "TJK4AB12VG", kind = TxKind.RECEIVE, at = Instant.parse("2026-10-02T07:00:00Z"), amountCents = 500_000,
                name = "JANE TESTER", phone = "0700000001", account = null, categoryKey = Categories.RECEIVED)),
        )
        val ui = vm(Categories.RECEIVED).ui.first { it.loaded && it.thisMonthCents > 0 }
        assertEquals(CategoryMeasure.RECEIVED, ui.measure)
        assertEquals(500_000, ui.thisMonthCents)
        assertFalse(ui.canTrack, "Money in can't be tracked (R50)")
        assertEquals(listOf("Jane Tester"), ui.topPlaces.map { it.name })
    }

    @Test
    fun `top places open Transactions narrowed to the category and that payee, on the chosen line`() = runTest {
        twoLines(db)
        db.transactionsDao().upsertAll(listOf(shop("TJK4AB12VH", "2026-10-02T07:00:00Z", 90_000, "CORNER SHOP"), shop("TJK4AB12VJ", "2026-10-03T07:00:00Z", 60_000, "GREEN GROCER")))
        val vm = vm(Categories.GROCERIES)
        val corner = vm.ui.first { it.topPlaces.size == 2 }.topPlaces.first()
        assertEquals("Corner Shop", corner.name)
        vm.selectLine(1)
        vm.ui.first { it.line.lineId == 1L }
        vm.openPlace(corner.counterpartyKey)
        assertEquals(
            ActivityLink.Transactions(TransactionFilter(categoryKeys = setOf(Categories.GROCERIES), counterpartyKey = corner.counterpartyKey, lineId = 1)),
            links.requests.value,
        )
    }

    @Test
    fun `See all opens Transactions filtered to the category on the chosen line`() = runTest {
        twoLines(db)
        val vm = vm()
        vm.selectLine(2)
        vm.ui.first { it.line.lineId == 2L }
        vm.openAll()
        assertEquals(ActivityLink.Transactions(TransactionFilter(categoryKeys = setOf(Categories.ELECTRICITY), lineId = 2)), links.requests.value)
    }

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
    fun `a rule can be added after a preview, deleted, and put back with Undo (R48, R73)`() = runTest {
        ingest(Sms.paybill("TJK4AB12UF", "SAMPLE WATER CO", "ACC 501", "1,250.00"))
        val vm = vm(Categories.WATER)
        vm.ui.first { it.loaded }
        vm.previewRule("sample water", "")
        assertEquals(RulePreview(1, 1, 0), vm.preview.first { it?.name == "sample water" }?.preview)
        val added = CompletableDeferred<Unit>()
        vm.addRule("sample water", "") { added.complete(Unit) }
        added.await() // the callback comes after the edit has finished, on Room's thread
        val yours = vm.ui.first { ui -> ui.rules.any { it.label == "Name has Sample Water" } }.rules.first { it.label == "Name has Sample Water" }
        assertFalse(yours.builtIn)
        val removed = CompletableDeferred<RemovedRule>()
        vm.deleteRule(yours.id) { removed.complete(it) }
        vm.ui.first { ui -> ui.rules.none { it.label == "Name has Sample Water" } }
        vm.restoreRule(removed.await())
        vm.ui.first { ui -> ui.rules.any { it.label == "Name has Sample Water" } }
    }

    @Test
    fun `Save does nothing until the count on screen is for the text typed (R48)`() = runTest {
        ingest(Sms.paybill("TJK4AB12UH", "SAMPLE WATER CO", "ACC 501", "1,250.00"))
        val vm = vm(Categories.WATER)
        vm.ui.first { it.loaded }
        vm.previewRule("sample wate", "")
        vm.preview.first { it?.name == "sample wate" }
        vm.addRule("sample water", "") {}
        vm.previewRule("sample water co", "")
        vm.preview.first { it?.name == "sample water co" }
        val added = CompletableDeferred<Unit>()
        vm.addRule("sample water co", "") { added.complete(Unit) }
        added.await()
        assertEquals(listOf("SAMPLE WATER CO"), db.rulesDao().all().filter { it.origin == RuleOrigin.USER }.map { it.pattern })
    }

    @Test
    fun `a hidden payment leaves the page, and Undo brings it back`() = runTest {
        ingest(Sms.paybill("TJK4AB12UG", "KPLC PREPAID", "37100000001", "900.00", "2/10/26 at 9:00 AM"))
        val vm = vm()
        assertEquals(90_000, vm.ui.first { it.loaded && it.thisMonthCents > 0 }.thisMonthCents)
        edits.setHidden("TJK4AB12UG", true)
        val hidden = vm.ui.first { it.thisMonthCents == 0L }
        assertTrue(hidden.payments.isEmpty())
        assertTrue(hidden.empty, "no payment shows any more")
        vm.undoHide("TJK4AB12UG")
        assertEquals(90_000, vm.ui.first { it.thisMonthCents == 90_000L }.thisMonthCents)
    }

    @Test
    fun `stopping tracking keeps the page, a rename shows at once, and a missing category says so (R89)`() = runTest {
        val vm = vm()
        vm.ui.first { it.loaded }
        val renamed = CompletableDeferred<Boolean>()
        vm.rename("Power") { renamed.complete(it) }
        assertTrue(renamed.await())
        assertEquals("Power", vm.ui.first { it.category?.name == "Power" }.category?.name)
        vm.setTracked(false)
        assertFalse(vm.ui.first { it.category?.tracked == false }.missing, "the page stays")
        vm.setTracked(true)
        assertTrue(vm.ui.first { it.category?.tracked == true }.category!!.tracked)
        assertTrue(vm("no_such_category").ui.first { it.loaded }.missing)
    }

    @Test
    fun `any category takes an icon and a colour, and Reset to default shows once a built-in one changes (D6)`() = runTest {
        val vm = vm(Categories.GROCERIES)
        assertFalse(vm.ui.first { it.loaded }.canReset)
        vm.setIcon("fluent_teapot")
        vm.setColor(CategoryLooks.SWATCHES.first { it.name == "Sky" })
        val changed = vm.ui.first { it.category?.icon3d == "fluent_teapot" && it.category.color != null }
        assertTrue(changed.canReset)
        vm.resetLooks()
        val reset = vm.ui.first { it.category?.icon3d == Categories.seed(Categories.GROCERIES)!!.icon3d && it.category.color == null }
        assertFalse(reset.canReset)
        val pets = edits.createCategory("Pets", CategoryGroup.EVERYDAY)
        val own = vm(pets).ui.first { it.loaded }
        assertTrue(own.own && own.canTrack && own.canAddRule)
        assertFalse(own.canReset, "your own category has no seed")
        assertFalse(vm(Categories.OWN_ACCOUNTS).ui.first { it.loaded }.canAddRule, "own-account rules come from a payment's switch")
    }

    @Test
    fun `archiving your category marks it archived and stops tracking it, and Bring back undoes it (R72)`() = runTest {
        val pets = edits.createCategory("Pets", CategoryGroup.EVERYDAY)
        edits.setTracked(pets, true)
        val vm = vm(pets)
        vm.ui.first { it.category?.tracked == true }
        vm.setArchived(true)
        val archived = vm.ui.first { it.category?.archived == true }
        assertFalse(archived.category!!.tracked)
        assertFalse(archived.canTrack)
        vm.setArchived(false)
        assertFalse(vm.ui.first { it.category?.archived == false }.category!!.archived)
    }
}
