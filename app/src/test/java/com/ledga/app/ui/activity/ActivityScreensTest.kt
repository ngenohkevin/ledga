package com.ledga.app.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.ledga.app.data.derive.DateFilter
import com.ledga.app.data.derive.FlowFilter
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.LineRow
import com.ledga.app.data.room.TxRow
import com.ledga.app.data.room.dao.DayTotal
import com.ledga.app.testing.fulizaTxRow
import com.ledga.app.testing.snapScreen
import com.ledga.app.testing.snapScreenLandscape
import com.ledga.app.testing.txRow
import com.ledga.app.ui.app.ShellFrame
import com.ledga.app.ui.app.Tab
import com.ledga.app.ui.design.components.SheetScaffold
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.core.model.Categories
import com.ledga.core.model.FlowKind
import com.ledga.core.model.TxKind
import com.ledga.core.time.InstantRange
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

/** Spec §15.2: Activity › Transactions and its filter sheet, light/dark × 1.0/1.3, plus landscape. Synthetic data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class ActivityScreensTest {
    private val today = LocalDate.parse("2026-10-06")
    private val created = Instant.parse("2026-01-01T00:00:00Z")
    private val complete = LoadStates(LoadState.NotLoading(false), LoadState.NotLoading(true), LoadState.NotLoading(true))
    private val categories = Categories.SEED.associate {
        it.key to CategoryRow(it.key, it.name, it.group, it.icon3d, null, null, it.tracked, it.sortOrder, CategoryOrigin.SYSTEM, false)
    }
    private val lines = listOf(
        LineRow(1, 1, "0712000023", "Personal", "#0E9F6E", true, created),
        LineRow(2, 2, "0733000087", "Business", "#1E7FD8", false, created),
    )

    /** Nairobi 6 Oct (today) and 5 Oct (yesterday), mockup `activity`'s rows. */
    private val rows = listOf(
        txRow(code = "TJK4AB12RA", kind = TxKind.BUY_GOODS, amountCents = 300_000, name = "SAMPLE FUEL STATION", account = null, categoryKey = Categories.FUEL, at = Instant.parse("2026-10-06T15:40:00Z")),
        txRow(code = "TJK4AB12FA", at = Instant.parse("2026-10-06T11:15:00Z")),
        txRow(code = "TJK4AB12RC", kind = TxKind.RECEIVE, flow = FlowKind.INCOME, amountCents = 500_000, name = "JANE TESTER", phone = "0712***111", account = null, categoryKey = Categories.RECEIVED, at = Instant.parse("2026-10-06T08:02:00Z")),
        txRow(code = "TJK4AB12RD", kind = TxKind.BUY_GOODS, amountCents = 127_500, name = "SAMPLE SUPERMARKET", account = null, categoryKey = Categories.GROCERIES, at = Instant.parse("2026-10-05T16:12:00Z")),
        fulizaTxRow().copy(occurredAt = Instant.parse("2026-10-05T14:30:00Z")),
        txRow(code = "TJK4AB12RE", kind = TxKind.BUY_GOODS, amountCents = 123_456_789, name = "A VERY LONG MERCHANT NAME THAT KEEPS GOING SUPERMARKET LIMITED", account = null, categoryKey = Categories.SHOPPING, at = Instant.parse("2026-10-05T05:03:00Z")),
    )
    private val totals = mapOf(
        today to DayTotal(today.toEpochDay(), 400_000, 500_000),
        today.minusDays(1) to DayTotal(today.minusDays(1).toEpochDay(), 123_830_789, 0),
    )
    private val ui = TransactionsUi(categories = categories, lines = lines, today = today, dayTotals = totals)

    @Composable
    private fun Transactions(state: TransactionsUi, data: List<TxRow>, filterCount: Int = 0) {
        val items = remember(data) { flowOf(PagingData.from(data, complete).toActivityItems()) }.collectAsLazyPagingItems()
        ShellFrame(Tab.ACTIVITY, onSelect = {}) {
            ActivityContent(ActivitySegment.TRANSACTIONS, onSegment = {}, filterCount = filterCount, onFilters = {}) {
                TransactionsPane(state, items, TransactionsActions())
            }
        }
    }

    @Test
    fun transactions() = snapScreen("activity_transactions") { Transactions(ui, rows) }

    @Test
    fun noMatches() = snapScreen("activity_no_matches") {
        val march = DateFilter("March 2026", InstantRange(Instant.parse("2026-02-28T21:00:00Z"), Instant.parse("2026-03-31T21:00:00Z")))
        val filter = TransactionFilter(flow = FlowFilter.OUT, categoryKeys = setOf(Categories.FUEL), dates = march)
        Transactions(ui.copy(query = "zzz", filter = filter), emptyList(), filterCount = filter.sheetCount)
    }

    @Test
    fun filterSheet() = snapScreen("activity_filters") {
        Box(Modifier.fillMaxSize().background(LedgaTheme.colors.canvas), contentAlignment = Alignment.BottomCenter) {
            SheetScaffold(title = "Filters") {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    FilterSheetContent(
                        current = TransactionFilter(categoryKeys = setOf(Categories.FUEL, Categories.ELECTRICITY), minAmountCents = 100_000),
                        categories = categories.values.sortedBy { it.sortOrder },
                        now = Instant.parse("2026-10-06T06:00:00Z"),
                        onApply = {},
                    )
                }
            }
        }
    }

    @Test
    fun landscape() = snapScreenLandscape("activity_transactions") { Transactions(ui, rows) }
}
