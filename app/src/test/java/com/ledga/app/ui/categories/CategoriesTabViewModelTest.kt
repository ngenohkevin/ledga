package com.ledga.app.ui.categories

import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.trackers.CategoryMeasure
import com.ledga.app.data.trackers.Trackers
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.TestViewModels
import com.ledga.app.testing.selectedLine
import com.ledga.app.testing.twoLines
import com.ledga.app.testing.txRow
import com.ledga.app.time.LiveClock
import com.ledga.core.model.Categories
import com.ledga.core.model.CategoryGroup
import com.ledga.core.model.TxKind
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 4e D2, R50, R53, R92, R95, R98: the Categories tab. Synthetic rows. */
@RunWith(RobolectricTestRunner::class)
class CategoriesTabViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-10-06T06:00:00Z")) // Tue 6 Oct 2026, 09:00 in Nairobi
    private val deriver = Deriver(db, clock)
    private val edits = TransactionEdits(db, deriver, clock)
    private val vms = TestViewModels()

    private fun vm() = vms.track(
        CategoriesTabViewModel(Trackers(db, LedgerQueries(db)), db, selectedLine(db, FakePrefsStore()), LiveClock(clock) { awaitCancellation() }, edits),
    )

    @After fun close() {
        vms.stopAll()
        db.close()
    }

    @Test
    fun `the chart stacks every tracker month by month, and the big number is the average of completed months`() = runTest {
        db.transactionsDao().upsertAll(
            listOf(
                txRow(code = "TJK4AB12TA", at = Instant.parse("2026-08-10T07:00:00Z"), amountCents = 200_000),
                txRow(code = "TJK4AB12TB", at = Instant.parse("2026-09-10T07:00:00Z"), amountCents = 100_000),
                txRow(
                    code = "TJK4AB12TC", kind = TxKind.BUY_GOODS, name = "SAMPLE FUEL STATION", account = null, categoryKey = Categories.FUEL,
                    at = Instant.parse("2026-09-12T07:00:00Z"), amountCents = 300_000,
                ),
                txRow(code = "TJK4AB12TD", at = Instant.parse("2026-10-02T07:00:00Z"), amountCents = 50_000),
            ),
        )
        val ui = vm().ui.first { it.loaded && it.thisMonthCents == 50_000L }
        assertEquals(listOf("MAY", "JUN", "JUL", "AUG", "SEP", "OCT"), ui.bars.map { it.label })
        assertEquals(listOf(100_000L, 0L, 300_000L, 0L), ui.bars[4].segments, "September: Electricity, Water, Fuel, Car service")
        assertEquals(300_000, ui.averageCents) // August 2,000 and September 4,000: the completed months from the first payment
        assertEquals("All trackers · last 6 months", ui.heading)
    }

    @Test
    fun `Year runs from January, and in January the big number falls back to this month`() = runTest {
        clock.instant = Instant.parse("2026-01-10T06:00:00Z")
        db.transactionsDao().upsertAll(listOf(txRow(code = "TJK4AB12TE", at = Instant.parse("2026-01-05T07:00:00Z"), amountCents = 70_000)))
        val vm = vm()
        vm.setRange(TrackerRange.YEAR)
        val ui = vm.ui.first { it.loaded && it.range == TrackerRange.YEAR && it.thisMonthCents == 70_000L }
        assertEquals(listOf("JAN"), ui.bars.map { it.label })
        assertNull(ui.averageCents)
        assertEquals("All trackers · 2026", ui.heading)
    }

    @Test
    fun `only untracked spending categories can be tracked, and tracking one adds its tracker`() = runTest {
        val vm = vm()
        val before = vm.ui.first { it.loaded }
        assertTrue(before.untracked.none { it.groupKey == CategoryGroup.MONEY_IN || it.groupKey == CategoryGroup.NOT_SPENDING || it.tracked })
        assertTrue(before.untracked.any { it.key == Categories.SCHOOL })
        vm.track(Categories.SCHOOL)
        val after = vm.ui.first { ui -> ui.trackers.any { it.category.key == Categories.SCHOOL } }
        assertTrue(after.untracked.none { it.key == Categories.SCHOOL })
    }

    @Test
    fun `the chosen line narrows the trackers`() = runTest {
        twoLines(db)
        db.transactionsDao().upsertAll(
            listOf(
                txRow(code = "TJK4AB12TF", lineId = 1, amountCents = 100_000, at = Instant.parse("2026-10-02T07:00:00Z")),
                txRow(code = "TJK4AB12TG", lineId = 2, amountCents = 40_000, at = Instant.parse("2026-10-03T07:00:00Z")),
            ),
        )
        val vm = vm()
        vm.ui.first { it.loaded && it.thisMonthCents == 140_000L }
        vm.selectLine(2)
        assertEquals(40_000, vm.ui.first { it.line.lineId == 2L }.thisMonthCents)
    }

    @Test
    fun `All categories lists the untracked ones by group with this month's amount, archived ones apart (R95)`() = runTest {
        db.transactionsDao().upsertAll(
            listOf(
                txRow(code = "TJK4AB12XA", kind = TxKind.BUY_GOODS, name = "CORNER SHOP", account = null, categoryKey = Categories.GROCERIES,
                    at = Instant.parse("2026-10-02T07:00:00Z"), amountCents = 64_000, feeCents = 0),
                txRow(code = "TJK4AB12XB", kind = TxKind.RECEIVE, name = "JANE TESTER", phone = "0700000001", account = null, categoryKey = Categories.RECEIVED,
                    at = Instant.parse("2026-10-03T07:00:00Z"), amountCents = 500_000),
            ),
        )
        val wedding = edits.createCategory("Wedding", CategoryGroup.EVERYDAY)
        edits.setArchived(wedding, true)
        val ui = vm().ui.first { ui -> ui.loaded && ui.groups.flatMap { it.rows }.any { it.monthCents == 64_000L } }
        val everyday = ui.groups.first { it.group == CategoryGroup.EVERYDAY }.rows
        assertEquals(64_000, everyday.first { it.category.key == Categories.GROCERIES }.monthCents)
        assertEquals(0, everyday.first { it.category.key == Categories.SCHOOL }.monthCents)
        assertTrue(ui.groups.flatMap { it.rows }.none { it.category.tracked }, "tracked ones are in Trackers above")
        assertTrue(ui.groups.flatMap { it.rows }.none { it.category.archived })
        val received = ui.groups.first { it.group == CategoryGroup.MONEY_IN }.rows.first { it.category.key == Categories.RECEIVED }
        assertEquals(500_000L to CategoryMeasure.RECEIVED, received.monthCents to received.measure)
        assertEquals(listOf(wedding), ui.archived.map { it.category.key })
        assertEquals(CategoryGroup.entries.filter { g -> ui.groups.any { it.group == g } }, ui.groups.map { it.group }, "picker order")
    }

    @Test
    fun `search ignores case, spaces and accents, and finds archived categories, marked (R98)`() = runTest {
        val cafe = edits.createCategory("Café club", CategoryGroup.EVERYDAY)
        val wedding = edits.createCategory("Wedding", CategoryGroup.EVERYDAY)
        edits.setArchived(wedding, true)
        val vm = vm()
        vm.ui.first { it.loaded }
        vm.setQuery("  CAFE ")
        assertEquals(listOf(cafe), vm.ui.first { it.query == "  CAFE " }.results.map { it.category.key })
        vm.setQuery("wed")
        val archived = vm.ui.first { it.query == "wed" }.results.single()
        assertTrue(archived.category.archived)
        vm.setQuery("zzz")
        assertTrue(vm.ui.first { it.query == "zzz" }.let { it.searching && it.results.isEmpty() })
        vm.setQuery("e")
        val many = vm.ui.first { it.query == "e" }.results.map { it.category }
        assertEquals(many.sortedWith(compareBy({ it.groupKey.ordinal }, { it.sortOrder })), many, "picker order")
    }

    @Test
    fun `a new category is made in the group chosen and handed back, and a blank name makes nothing (R92)`() = runTest {
        val vm = vm()
        vm.ui.first { it.loaded }
        vm.create("   ", CategoryGroup.EVERYDAY) { error("a blank name made a category") }
        val made = CompletableDeferred<String>()
        vm.create("Pets", CategoryGroup.EVERYDAY) { made.complete(it) }
        val key = made.await()
        val row = db.categoriesDao().get(key)!!
        assertEquals("Pets" to CategoryGroup.EVERYDAY, row.name to row.groupKey)
    }
}
