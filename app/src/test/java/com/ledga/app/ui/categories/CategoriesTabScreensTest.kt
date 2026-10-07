package com.ledga.app.ui.categories

import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.dao.CategorySpend
import com.ledga.app.data.trackers.CategoryMeasure
import com.ledga.app.data.trackers.TrackerSummary
import com.ledga.app.testing.snapScreen
import com.ledga.app.testing.snapScreenLandscape
import com.ledga.app.ui.app.ShellFrame
import com.ledga.app.ui.app.Tab
import com.ledga.app.ui.design.charts.Bar
import com.ledga.app.ui.design.components.SheetScaffold
import com.ledga.core.chart.Bucket
import com.ledga.core.model.Categories
import com.ledga.core.model.CategoryGroup
import com.ledga.core.money.Money
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
import java.time.Instant
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 4e D2: the Categories tab (trackers, all categories, archived folded), its search and no-match states, the New category and Track sheets, landscape. Synthetic values. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class CategoriesTabScreensTest {
    private val now = Instant.parse("2026-10-06T06:00:00Z")
    private val categories = Categories.SEED.map {
        CategoryRow(it.key, it.name, it.group, it.icon3d, null, null, it.tracked, it.sortOrder, CategoryOrigin.SYSTEM, false)
    }
    private val byKey = categories.associateBy { it.key }
    private val months = Periods.lastN(PeriodType.MONTH, now, 13)

    private fun tracker(key: String, last6: List<Long>, average: Long?, usual: Int? = null, last: CategorySpend? = null): TrackerSummary {
        val values = List(7) { 0L } + last6
        return TrackerSummary(byKey.getValue(key), months.mapIndexed { i, p -> Bucket(p, Money(values[i]), if (values[i] > 0) 1 else 0) }, average, usual, last)
    }

    private val trackers = listOf(
        tracker(Categories.ELECTRICITY, listOf(171_000, 182_000, 168_000, 190_000, 176_000, 95_000), 178_000),
        tracker(Categories.WATER, listOf(64_000, 58_000, 61_000, 66_000, 59_000, 0), 61_000, usual = 12),
        tracker(Categories.FUEL, listOf(740_000, 815_000, 690_000, 760_000, 705_000, 230_000), 742_000),
        // The June service the bar shows is its last payment, as the reader would report it.
        tracker(
            Categories.CAR_SERVICE, listOf(0, 520_000, 0, 0, 0, 0), 87_000,
            last = CategorySpend("TJK4AB12CS", Categories.CAR_SERVICE, Instant.parse("2026-06-12T07:00:00Z"), 520_000, "SAMPLE GARAGE"),
        ),
    )
    private val labels = listOf("MAY", "JUN", "JUL", "AUG", "SEP", "OCT")
    private val bars = labels.mapIndexed { i, label -> Bar(label, trackers.map { it.months[7 + i].total.cents }, label, inProgress = i == 5) }
    private val untracked = categories.filter { !it.tracked && it.groupKey.accepts(com.ledga.core.model.FlowKind.SPEND) }

    private fun line(key: String, cents: Long) = byKey.getValue(key).let { CategoryLineUi(it, cents, CategoryMeasure.of(it.groupKey)) }
    private val wedding = CategoryRow("user_wedding", "Wedding", CategoryGroup.EVERYDAY, "fluent_church", null, null, false, 100, CategoryOrigin.USER, true)
    private val greenClub = CategoryRow("user_green_grocer_club", "Green Grocer Club", CategoryGroup.EVERYDAY, "fluent_seedling", null, null, false, 101, CategoryOrigin.USER, true)

    private val ui = CategoriesTabUi(
        loaded = true,
        trackers = trackers,
        bars = bars,
        averageCents = 1_085_000,
        thisMonthCents = 325_000,
        heading = "All trackers · last 6 months",
        untracked = untracked,
        groups = listOf(
            TabGroupUi(CategoryGroup.BILLS_UTILITIES, listOf(line(Categories.INTERNET, 300_000), line(Categories.TV, 0), line(Categories.RENT, 2_500_000))),
            TabGroupUi(CategoryGroup.EVERYDAY, listOf(line(Categories.GROCERIES, 64_000), line(Categories.FOOD, 12_500), line(Categories.TRANSPORT, 24_500))),
            TabGroupUi(CategoryGroup.MONEY_IN, listOf(line(Categories.RECEIVED, 500_000), line(Categories.CASH_DEPOSIT, 0))),
        ),
        archived = listOf(CategoryLineUi(wedding, 0, CategoryMeasure.SPENT)),
        today = LocalDate.parse("2026-10-06"),
    )

    @Test
    fun tab() = snapScreen("categories_tab") { ShellFrame(Tab.CATEGORIES, onSelect = {}) { CategoriesTabContent(ui, CategoriesTabActions()) } }

    @Test
    fun search() = snapScreen("categories_tab_search") {
        ShellFrame(Tab.CATEGORIES, onSelect = {}) {
            CategoriesTabContent(ui.copy(query = "gro", results = listOf(line(Categories.GROCERIES, 64_000), CategoryLineUi(greenClub, 0, CategoryMeasure.SPENT))), CategoriesTabActions())
        }
    }

    @Test
    fun none() = snapScreen("categories_tab_none") { ShellFrame(Tab.CATEGORIES, onSelect = {}) { CategoriesTabContent(ui.copy(query = "Chama", results = emptyList()), CategoriesTabActions()) } }

    @Test
    fun newCategory() = snapScreen("new_category") { SheetScaffold("New category") { NewCategoryContent("Pets", {}, CategoryGroup.EVERYDAY, {}, {}) } }

    @Test
    fun trackSheet() = snapScreen("track_sheet") { SheetScaffold("Track a category") { TrackCategoryContent(untracked, onTrack = {}) } }

    @Test
    fun landscape() = snapScreenLandscape("categories_tab") { ShellFrame(Tab.CATEGORIES, onSelect = {}) { CategoriesTabContent(ui, CategoriesTabActions()) } }
}
