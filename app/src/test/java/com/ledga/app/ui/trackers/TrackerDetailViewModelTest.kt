package com.ledga.app.ui.trackers

import kotlinx.coroutines.CompletableDeferred
import androidx.lifecycle.SavedStateHandle
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.data.edit.RemovedRule
import com.ledga.app.data.edit.RulePreview
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.SmsSource
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
import com.ledga.core.model.Categories
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec §10.4 Tracker detail (R48, R49, R51, R54). Synthetic payments. */
@RunWith(RobolectricTestRunner::class)
class TrackerDetailViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-10-06T06:00:00Z")) // Tue 6 Oct 2026, 09:00 in Nairobi
    private val deriver = Deriver(db, clock)
    private val links = ActivityLinks()
    private val prefs = FakePrefsStore()
    private val vms = TestViewModels()
    private val stopped = StoppedTrackers(TransactionEdits(db, deriver, clock))

    private fun vm(key: String = Categories.ELECTRICITY) = vms.track(
        TrackerDetailViewModel(
            SavedStateHandle(mapOf("categoryKey" to key)), Trackers(db, LedgerQueries(db)), selectedLine(db, prefs),
            LiveClock(clock) { awaitCancellation() }, TransactionEdits(db, deriver, clock), links, stopped,
        ),
    )

    @After fun close() {
        vms.stopAll()
        db.close()
    }

    private suspend fun ingest(vararg bodies: String) =
        SmsIngestor(db, deriver).ingestAll(bodies.map { RawSms("MPESA", it, clock.instant(), null, null, SmsSource.INBOX) })

    @Test
    fun `twelve months by default, the running one selected, with the stat tiles, the rules and the payments`() = runTest {
        db.transactionsDao().upsertAll(
            listOf(
                txRow(code = "TJK4AB12UA", at = Instant.parse("2026-09-10T07:00:00Z"), amountCents = 185_000),
                txRow(code = "TJK4AB12UB", at = Instant.parse("2026-10-02T07:00:00Z"), amountCents = 90_000),
            ),
        )
        val ui = vm().ui.first { it.loaded && it.thisMonthCents == 90_000L }
        assertEquals(12, ui.bars.size)
        assertEquals(11, ui.selectedIndex)
        assertTrue(ui.bars.last().inProgress)
        assertEquals(185_000, ui.lastMonthCents)
        assertEquals(185_000, ui.monthlyAverageCents)
        assertEquals(275_000, ui.yearSoFarCents)
        assertTrue(ui.rules.any { it.label == "Name has KPLC" })
        assertEquals(listOf("TJK4AB12UB", "TJK4AB12UA"), ui.payments.map { it.code })
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
    fun `a rule can be added after a preview, removed in edit mode, and put back with Undo`() = runTest {
        ingest(Sms.paybill("TJK4AB12UF", "SAMPLE WATER CO", "ACC 501", "1,250.00"))
        val vm = vm(Categories.WATER)
        vm.ui.first { it.loaded }
        vm.previewRule("sample water", "")
        assertEquals(RulePreview(1, 1, 0), vm.preview.first { it?.name == "sample water" }?.preview)
        val added = CompletableDeferred<Unit>()
        vm.addRule("sample water", "") { added.complete(Unit) }
        added.await() // the callback comes after the edit has finished, on Room's thread
        val withRule = vm.ui.first { ui -> ui.rules.any { it.label == "Name has Sample Water" } }
        vm.toggleEditing()
        assertTrue(vm.ui.first { it.editing }.editing)
        val removed = CompletableDeferred<RemovedRule>()
        vm.removeRule(withRule.rules.first { it.label == "Name has Sample Water" }.id) { removed.complete(it) }
        val undo = removed.await()
        vm.ui.first { ui -> ui.rules.none { it.label == "Name has Sample Water" } }
        vm.restoreRule(undo)
        vm.ui.first { ui -> ui.rules.any { it.label == "Name has Sample Water" } }
    }

    @Test
    fun `Save does nothing until the count on screen is for the text typed (R48)`() = runTest {
        ingest(Sms.paybill("TJK4AB12UH", "SAMPLE WATER CO", "ACC 501", "1,250.00"))
        val vm = vm(Categories.WATER)
        vm.ui.first { it.loaded }
        vm.previewRule("sample wate", "")
        vm.preview.first { it?.name == "sample wate" }
        // One more letter, and Save before that text was counted: the person never saw what it would move.
        vm.addRule("sample water", "") {}
        // Then a rule saved the right way. Edits run one at a time, so an early Save would have finished first.
        vm.previewRule("sample water co", "")
        vm.preview.first { it?.name == "sample water co" }
        val added = CompletableDeferred<Unit>()
        vm.addRule("sample water co", "") { added.complete(Unit) }
        added.await()
        assertEquals(listOf("SAMPLE WATER CO"), db.rulesDao().all().filter { it.origin == RuleOrigin.USER }.map { it.pattern })
    }

    @Test
    fun `removing the last rule ends edit mode, so the next rule doesn't arrive with a remove button`() = runTest {
        ingest(Sms.paybill("TJK4AB12UJ", "SAMPLE LANDLORD", "HSE 4", "9,000.00"))
        val edits = TransactionEdits(db, deriver, clock)
        assertTrue(edits.addRule(Categories.RENT, "sample landlord", null))
        val vm = vm(Categories.RENT)
        val only = vm.ui.first { it.rules.size == 1 }.rules.single()
        vm.toggleEditing()
        vm.ui.first { it.editing }
        val removed = CompletableDeferred<RemovedRule>()
        vm.removeRule(only.id) { removed.complete(it) }
        removed.await()
        vm.ui.first { it.rules.isEmpty() }
        assertTrue(edits.addRule(Categories.RENT, "sample landlord", null))
        assertFalse(vm.ui.first { it.rules.size == 1 }.editing, "edit mode ended with the last rule")
    }

    @Test
    fun `a hidden payment leaves the tracker, and Undo brings it back`() = runTest {
        ingest(Sms.paybill("TJK4AB12UG", "KPLC PREPAID", "37100000001", "900.00", "2/10/26 at 9:00 AM"))
        val vm = vm()
        assertEquals(90_000, vm.ui.first { it.loaded && it.thisMonthCents > 0 }.thisMonthCents)
        TransactionEdits(db, deriver, clock).setHidden("TJK4AB12UG", true)
        assertTrue(vm.ui.first { it.thisMonthCents == 0L }.payments.isEmpty())
        vm.undoHide("TJK4AB12UG")
        assertEquals(90_000, vm.ui.first { it.thisMonthCents == 90_000L }.thisMonthCents)
    }

    @Test
    fun `stopping tracking closes the detail, a rename shows at once, and a missing category says so`() = runTest {
        val vm = vm()
        vm.ui.first { it.loaded }
        val renamed = CompletableDeferred<Boolean>()
        vm.rename("Power") { renamed.complete(it) }
        assertTrue(renamed.await())
        assertEquals("Power", vm.ui.first { it.category?.name == "Power" }.category?.name)
        val closed = CompletableDeferred<Unit>()
        vm.stopTracking { closed.complete(Unit) }
        closed.await()
        assertFalse(db.categoriesDao().all().first { it.key == Categories.ELECTRICITY }.tracked)
        assertTrue(vm("no_such_category").ui.first { it.loaded }.missing)
    }

    @Test
    fun `stopping tracking leaves a note with Undo for the screen the detail returns to (R51)`() = runTest {
        val vm = vm()
        vm.ui.first { it.loaded }
        val closed = CompletableDeferred<Unit>()
        vm.stopTracking { closed.complete(Unit) }
        closed.await()
        val note = assertNotNull(stopped.latest.value)
        assertEquals(StoppedTracking(Categories.ELECTRICITY, "Electricity"), note)
        stopped.undo(note)
        assertTrue(db.categoriesDao().all().first { it.key == Categories.ELECTRICITY }.tracked)
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
}
