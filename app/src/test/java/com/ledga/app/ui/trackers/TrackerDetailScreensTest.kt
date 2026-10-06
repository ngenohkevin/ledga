package com.ledga.app.ui.trackers

import com.ledga.app.data.edit.RulePreview
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.testing.snapScreen
import com.ledga.app.testing.snapScreenLandscape
import com.ledga.app.testing.txRow
import com.ledga.app.ui.app.ShellFrame
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

/** Spec §15.2: Tracker detail (mockup Electricity tracker) light/dark × 1.0/1.3, editing, the add-rule sheet, landscape. Synthetic values. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class TrackerDetailScreensTest {
    private val now = Instant.parse("2026-10-06T06:00:00Z")
    private val seed = Categories.seed(Categories.ELECTRICITY)!!
    private val electricity = CategoryRow(seed.key, seed.name, seed.group, seed.icon3d, null, null, true, seed.sortOrder, CategoryOrigin.SYSTEM, false)
    private val values = listOf(180_000L, 190_000, 111_100, 200_000, 185_000, 111_100, 170_000, 111_100, 190_000, 200_000, 150_000, 90_000)
    private val buckets = Periods.lastN(PeriodType.MONTH, now, 12).mapIndexed { i, p -> Bucket(p, Money(values[i]), if (i == 10) 2 else 1) }

    private val ui = TrackerDetailUi(
        loaded = true,
        category = electricity,
        range = DetailRange.TWELVE_MONTHS,
        buckets = buckets,
        bars = buckets.mapIndexed { i, b ->
            val (title, caption) = TrackerText.tooltip(b, i == 11)
            com.ledga.app.ui.design.charts.Bar(b.period.start.month.name.take(3), listOf(b.total.cents), "$title, $caption", inProgress = i == 11)
        },
        selectedIndex = 10,
        averageCents = 185_500,
        thisMonthCents = 90_000,
        lastMonthCents = 150_000,
        monthlyAverageCents = 185_500,
        year = 2026,
        yearSoFarCents = 1_760_000,
        rules = listOf(RuleChipUi(1, "KPLC Prepaid · account 37100000001"), RuleChipUi(2, "Name has KPLC"), RuleChipUi(3, "Name has Kenya Power")),
        payments = listOf(
            txRow(code = "TJK4AB12VA", at = Instant.parse("2026-10-02T07:00:00Z"), amountCents = 90_000),
            txRow(code = "TJK4AB12VB", at = Instant.parse("2026-09-24T06:30:00Z"), amountCents = 100_000),
            txRow(code = "TJK4AB12VC", at = Instant.parse("2026-09-05T06:30:00Z"), amountCents = 50_000),
        ),
        today = LocalDate.parse("2026-10-06"),
    )

    @Test
    fun detail() = snapScreen("tracker") { ShellFrame(null, onSelect = {}) { TrackerDetailContent(ui, TrackerDetailActions()) } }

    @Test
    fun editing() = snapScreen("tracker_edit") { ShellFrame(null, onSelect = {}) { TrackerDetailContent(ui.copy(editing = true), TrackerDetailActions()) } }

    @Test
    fun addRule() = snapScreen("add_rule") {
        SheetScaffold("Add a rule") {
            AddRuleContent("Electricity", "sample power", "", ShownPreview("sample power", "", RulePreview(3, 2, 1)), onName = {}, onAccount = {}, onSave = {})
        }
    }

    @Test
    fun landscape() = snapScreenLandscape("tracker") { ShellFrame(null, onSelect = {}) { TrackerDetailContent(ui, TrackerDetailActions()) } }
}
