package com.ledga.app.ui.trackers

import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.trackers.TrackerSummary
import com.ledga.app.testing.snapScreen
import com.ledga.app.testing.snapScreenLandscape
import com.ledga.app.ui.app.ShellFrame
import com.ledga.app.ui.app.Tab
import com.ledga.app.ui.design.charts.Bar
import com.ledga.app.ui.design.components.SheetScaffold
import com.ledga.core.chart.Bucket
import com.ledga.core.model.Categories
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

/** Spec §15.2: Trackers (mockup `trackers`) light/dark × 1.0/1.3, empty, the "Track a category" sheet, landscape. Synthetic values. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class TrackersScreensTest {
    private val now = Instant.parse("2026-10-06T06:00:00Z")
    private val categories = Categories.SEED.map {
        CategoryRow(it.key, it.name, it.group, it.icon3d, null, null, it.tracked, it.sortOrder, CategoryOrigin.SYSTEM, false)
    }
    private val byKey = categories.associateBy { it.key }
    private val months = Periods.lastN(PeriodType.MONTH, now, 13)

    private fun tracker(key: String, last6: List<Long>, average: Long?, usual: Int? = null): TrackerSummary {
        val values = List(7) { 0L } + last6
        return TrackerSummary(byKey.getValue(key), months.mapIndexed { i, p -> Bucket(p, Money(values[i]), if (values[i] > 0) 1 else 0) }, average, usual, null)
    }

    private val trackers = listOf(
        tracker(Categories.ELECTRICITY, listOf(171_000, 182_000, 168_000, 190_000, 176_000, 95_000), 178_000),
        tracker(Categories.WATER, listOf(64_000, 58_000, 61_000, 66_000, 59_000, 0), 61_000, usual = 12),
        tracker(Categories.FUEL, listOf(740_000, 815_000, 690_000, 760_000, 705_000, 230_000), 742_000),
        tracker(Categories.CAR_SERVICE, listOf(0, 520_000, 0, 0, 0, 0), 87_000),
    )
    private val labels = listOf("MAY", "JUN", "JUL", "AUG", "SEP", "OCT")
    private val bars = labels.mapIndexed { i, label -> Bar(label, trackers.map { it.months[7 + i].total.cents }, label, inProgress = i == 5) }
    private val untracked = categories.filter { !it.tracked && it.groupKey.accepts(com.ledga.core.model.FlowKind.SPEND) }

    private val ui = TrackersUi(
        loaded = true,
        trackers = trackers,
        bars = bars,
        averageCents = 1_085_000,
        thisMonthCents = 325_000,
        heading = "All trackers · last 6 months",
        untracked = untracked,
        today = LocalDate.parse("2026-10-06"),
    )

    @Test
    fun trackers() = snapScreen("trackers") { ShellFrame(Tab.TRACKERS, onSelect = {}) { TrackersContent(ui, TrackersActions()) } }

    @Test
    fun empty() = snapScreen("trackers_empty") {
        ShellFrame(Tab.TRACKERS, onSelect = {}) { TrackersContent(TrackersUi(loaded = true, untracked = untracked, today = ui.today), TrackersActions()) }
    }

    @Test
    fun trackSheet() = snapScreen("track_sheet") { SheetScaffold("Track a category") { TrackCategoryContent(untracked, onTrack = {}) } }

    @Test
    fun landscape() = snapScreenLandscape("trackers") { ShellFrame(Tab.TRACKERS, onSelect = {}) { TrackersContent(ui, TrackersActions()) } }
}
