package com.ledga.app.ui.activity

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.derive.FlowFilter
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.data.edit.ApplyTo
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.room.TxRow
import com.ledga.app.testing.FakeSims
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.TestViewModels
import com.ledga.app.testing.selectedLine
import com.ledga.app.testing.txRow
import com.ledga.app.time.LiveClock
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.home.FulizaSheetContent
import com.ledga.app.ui.home.FulizaSheetViewModel
import com.ledga.core.model.Categories
import com.ledga.core.model.TxKind
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Owner report (2026-10-08): after setting a category on a payment deep in Activity › Transactions, the list was no
 * longer where he left it, so categorising his history meant scrolling back down after every payment. Each change
 * reloads the list from the database; it has to come back showing the same payments. A new filter or search, though,
 * starts at the top of its results (owner call, same day).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ActivityScrollTest {
    @get:Rule val compose = createComposeRule()
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-10-31T09:00:00Z"))
    private val deriver = Deriver(db, clock)
    private val edits = TransactionEdits(db, deriver, clock)
    private val live = LiveClock(clock) { awaitCancellation() }
    private val vms = TestViewModels()
    private lateinit var items: LazyPagingItems<ActivityItem>

    @After fun close() {
        vms.stopAllOnMainLooper()
        db.close()
    }

    private fun code(i: Int) = "TJK5%04dZA".format(i)

    /**
     * [count] invented payments, one a day back from 30 Oct 2026 (Shop 000 is the newest); buy goods unless [body] says
     * otherwise. One a day gives every payment its own day card, as a long history has hundreds of them above any deep
     * point in the list.
     */
    private fun ingest(count: Int, body: (Int, String) -> String = { i, at -> Sms.buyGoods(code(i), "SHOP %03d".format(i), "100.00", at) }) = runBlocking {
        val newest = LocalDate.parse("2026-10-30")
        val bodies = (0 until count).map { i ->
            val d = newest.minusDays(i.toLong())
            body(i, "${d.dayOfMonth}/${d.monthValue}/${d.year % 100} at 1:00 PM")
        }
        SmsIngestor(db, deriver).ingestAll(bodies.map { RawSms("MPESA", it, clock.instant(), null, null, SmsSource.INBOX) })
    }

    private fun vm() = vms.track(ActivityViewModel(LedgerQueries(db), db, LinesRepository(db.linesDao(), FakeSims(), clock), live, edits, ActivityLinks()))

    private fun show(): ActivityViewModel {
        val vm = vm()
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                items = vm.items.collectAsLazyPagingItems()
                TransactionsPane(vm.ui.collectAsState().value, items, TransactionsActions())
            }
        }
        settle { items.itemCount > 0 }
        return vm
    }

    /**
     * Lets the list work until [done]: Room loads on its own threads, which `waitUntil` doesn't always give time to on
     * a first load. [require] = false only where nothing may change (the test then checks what is on screen).
     */
    private fun settle(require: Boolean = true, done: () -> Boolean) {
        repeat(200) {
            if (done()) return compose.waitForIdle()
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
            Thread.sleep(20)
        }
        check(!require) { "the list never got there" }
        compose.waitForIdle()
    }

    private fun loaded(): List<Pair<String, String>> =
        items.itemSnapshotList.filterIsInstance<ActivityItem.Tx>().map { it.row.code to it.row.categoryKey }

    /** Scrolls [list] down until [isLoaded], then brings [key] to the top; [last] is its last index. */
    private fun scrollTo(list: SemanticsNodeInteraction, key: String, last: () -> Int, isLoaded: () -> Boolean) {
        repeat(100) {
            if (isLoaded()) {
                list.performScrollToKey(key)
                compose.waitForIdle()
                return
            }
            list.performScrollToIndex(last())
            compose.waitForIdle()
        }
        error("$key never loaded")
    }

    private fun scrollTo(code: String) =
        scrollTo(compose.onNode(hasScrollToIndexAction()), code, { items.itemCount - 1 }) { loaded().any { it.first == code } }

    /** The payments on screen, by shop number, top to bottom. */
    private fun onScreen(): List<Int> = numbersOnScreen(Regex("(?i)shop (\\d{3})")) { it }

    /** What [pattern]'s first group reads on screen, through [index], in order (each once). */
    private fun numbersOnScreen(pattern: Regex, index: (Int) -> Int): List<Int> =
        compose.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { n ->
                n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                    n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            }
            .mapNotNull { pattern.find(it)?.groupValues?.get(1)?.filter(Char::isDigit)?.toInt()?.let(index) }
            .distinct()
            .sorted()

    /** The category picker's choice, then waits for the list to reload with it. */
    private fun setCategory(code: String, applyTo: ApplyTo = ApplyTo.THIS_ONE) {
        runBlocking { edits.setCategory(code, Categories.ELECTRICITY, applyTo) }
        settle { loaded().none { it.first == code && it.second != Categories.ELECTRICITY } }
    }

    @Test
    fun `setting categories deep in a long history leaves the list where it was`() {
        ingest(600)
        show()
        scrollTo(code(400))
        val before = onScreen()
        assertEquals(400, before.first())

        setCategory(code(400))
        assertEquals(before, onScreen(), "after the first change")
        setCategory(code(401))
        assertEquals(before, onScreen(), "after the second change")
        setCategory(code(402), ApplyTo.ALL_FROM_NAME)
        assertEquals(before, onScreen(), "after a change for everything from that name")
    }

    @Test
    fun `a payment that leaves a filtered list takes only itself away`() {
        ingest(600)
        val vm = show()
        val category = runBlocking { db.transactionsDao().get(code(0))!!.categoryKey }
        vm.applySheet(TransactionFilter(categoryKeys = setOf(category)))
        settle { items.itemCount > 0 }
        scrollTo(code(400))
        val before = onScreen()
        assertEquals(400, before.first())

        runBlocking { edits.setCategory(code(400), Categories.ELECTRICITY, ApplyTo.THIS_ONE) }
        settle { loaded().none { it.first == code(400) } }
        val after = onScreen()
        assertFalse(400 in after)
        assertTrue(after.containsAll(before - 400), "still on screen: ${before - 400}, shown: $after")
    }

    @Test
    fun `a chip that narrows or widens the list starts at the top of its results (final review I1)`() {
        // Every fifth payment is money in.
        ingest(600) { i, at ->
            if (i % 5 == 3) Sms.receive(code(i), "SHOP %03d 0712345111".format(i), "100.00", at) else Sms.buyGoods(code(i), "SHOP %03d".format(i), "100.00", at)
        }
        val vm = show()
        scrollTo(code(398))
        vm.setFlow(FlowFilter.IN)
        settle { onScreen().firstOrNull() == 3 }
        scrollTo(code(398))
        assertEquals(398, onScreen().first())

        vm.setFlow(FlowFilter.ALL)
        settle(require = false) { onScreen().firstOrNull() == 0 }
        assertEquals(0, onScreen().first(), "after All; on screen: ${onScreen()}")
    }

    @Test
    fun `a search and clearing it start at the top of their results (final review I1)`() {
        ingest(600)
        val vm = show()
        scrollTo(code(400))
        vm.setQuery("shop 01")
        settle { onScreen().firstOrNull() == 10 }
        scrollTo(code(15))
        assertEquals(15, onScreen().first())

        vm.setQuery("")
        settle(require = false) { onScreen().firstOrNull() == 0 }
        assertEquals(0, onScreen().first(), "after clearing the search; on screen: ${onScreen()}")
    }

    @Test
    fun `coming back to the list after it reloaded keeps the place (final review I2)`() {
        ingest(600)
        val vm = vm()
        var shown by mutableStateOf(true)
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                // Leaving Activity (another tab, or a category's page) keeps its saved state, as navigation does.
                val saved = rememberSaveableStateHolder()
                if (shown) {
                    saved.SaveableStateProvider("activity") {
                        items = vm.items.collectAsLazyPagingItems()
                        TransactionsPane(vm.ui.collectAsState().value, items, TransactionsActions())
                    }
                }
            }
        }
        settle { items.itemCount > 0 }
        scrollTo(code(400))
        val before = onScreen()
        assertEquals(400, before.first())

        shown = false
        compose.waitForIdle()
        // A change made elsewhere (the category's page) reloads the list while it is away.
        runBlocking { edits.setCategory(code(400), Categories.ELECTRICITY, ApplyTo.THIS_ONE) }
        settle(require = false) { false.also { Thread.sleep(5) } }
        shown = true
        settle { loaded().any { it.first == code(400) && it.second == Categories.ELECTRICITY } && onScreen().isNotEmpty() }
        assertEquals(before, onScreen(), "after coming back")
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-xhdpi")
    fun `in a short pane a change deep in the list keeps the place too`() {
        ingest(600)
        show()
        scrollTo(code(400))
        val before = onScreen()
        assertEquals(400, before.first())

        setCategory(code(400))
        assertEquals(before, onScreen(), "after the first change")
        setCategory(code(401))
        assertEquals(before, onScreen(), "after the second change")
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-xhdpi")
    fun `in a short pane at the top, a new payment arrives below the search (final review M1)`() {
        ingest(600)
        show()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        runBlocking {
            SmsIngestor(db, deriver).ingestAll(
                listOf(RawSms("MPESA", Sms.buyGoods("TJK59999ZA", "SHOP 999", "100.00", "31/10/26 at 8:00 AM"), clock.instant(), null, null, SmsSource.INBOX)),
            )
        }
        settle { loaded().firstOrNull()?.first == "TJK59999ZA" }
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        assertEquals(999, onScreen().first { it == 999 })
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-xhdpi")
    fun `in a short pane, typing a search keeps the field focused`() {
        ingest(600)
        show()
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        compose.waitForIdle()
        field.performTextInput("s")
        settle(require = false) { false.also { Thread.sleep(5) } }
        field.assertIsFocused()
        field.performTextInput("h")
        settle(require = false) { false.also { Thread.sleep(5) } }
        field.assertIsFocused()
    }

    @Test
    fun `setting categories deep in one person's payments leaves their list where it was`() {
        // Ksh 1,000 + i to the same person, so each row says which it is.
        ingest(600) { i, at -> Sms.send(code(i), "%,d.00".format(1_000 + i), at) }
        val sheet = vms.track(PersonSheetViewModel(LedgerQueries(db), db, live, selectedLine(db)))
        val key = runBlocking { db.transactionsDao().get(code(0))!!.counterpartyKey!! }
        sheet.open(PersonRowUi(key, "Jane Tester", "0712345111", 600, 0, clock.instant()))
        lateinit var rows: LazyPagingItems<TxRow>
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                rows = sheet.items.collectAsLazyPagingItems()
                PersonSheetContent(sheet.ui.collectAsState().value, rows, onOpenTx = {}, Modifier.fillMaxSize())
            }
        }
        settle { rows.itemCount > 0 }
        fun loadedRows() = rows.itemSnapshotList.items.map { it.code to it.categoryKey }
        scrollTo(compose.onNode(hasScrollToIndexAction()), code(400), { rows.itemCount }) { loadedRows().any { it.first == code(400) } }
        fun shown() = numbersOnScreen(Regex("(?i)Ksh ([0-9,]+) (?:sent|paid).*tester")) { it - 1_000 }
        val before = shown()
        assertEquals(400, before.first())

        listOf(code(400), code(401), code(402)).forEachIndexed { n, code ->
            runBlocking { edits.setCategory(code, Categories.ELECTRICITY, ApplyTo.THIS_ONE) }
            settle { loadedRows().none { it.first == code && it.second != Categories.ELECTRICITY } }
            assertEquals(before, shown(), "after change ${n + 1}")
        }
    }

    @Test
    fun `changing a payment deep in the Fuliza sheet leaves its list where it was`() {
        val newest = LocalDate.parse("2026-10-30").atTime(10, 0).toInstant(ZoneOffset.UTC)
        val drawn = (0 until 600).map { i ->
            txRow(code = code(i), kind = TxKind.BUY_GOODS, name = "SHOP %03d".format(i), account = null, lineId = null,
                at = newest.minus(Duration.ofDays(i.toLong())), categoryKey = Categories.OTHER, fulizaDrawnCents = 10_000)
        }
        runBlocking { db.transactionsDao().upsertAll(drawn) }
        val sheet = vms.track(FulizaSheetViewModel(LedgerQueries(db), db, selectedLine(db), live))
        lateinit var rows: LazyPagingItems<TxRow>
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                rows = sheet.items.collectAsLazyPagingItems()
                FulizaSheetContent(sheet.ui.collectAsState().value, rows, onOpenTx = {}, Modifier.fillMaxSize())
            }
        }
        settle { rows.itemCount > 0 }
        fun loadedRows() = rows.itemSnapshotList.items.map { it.code to it.categoryKey }
        scrollTo(compose.onNode(hasScrollToIndexAction()), code(400), { rows.itemCount }) { loadedRows().any { it.first == code(400) } }
        val before = onScreen()
        assertEquals(400, before.first())

        (400..402).forEachIndexed { n, i ->
            // Any write to a payment reloads the list; a category change is the common one.
            runBlocking { db.transactionsDao().upsertAll(listOf(drawn[i].copy(categoryKey = Categories.ELECTRICITY))) }
            settle { loadedRows().none { it.first == code(i) && it.second != Categories.ELECTRICITY } }
            assertEquals(before, onScreen(), "after change ${n + 1}")
        }
    }
}
