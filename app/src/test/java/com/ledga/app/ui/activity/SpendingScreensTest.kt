package com.ledga.app.ui.activity

import com.ledga.app.data.room.dao.PeriodTotals
import com.ledga.app.testing.snapScreen
import com.ledga.app.testing.snapScreenLandscape
import com.ledga.app.ui.app.ShellFrame
import com.ledga.app.ui.app.Tab
import com.ledga.app.ui.design.charts.Bar
import com.ledga.app.ui.design.format.DateLabels
import com.ledga.core.model.Categories
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Spec §15.2: Activity › Spending (mockup `spending`), light/dark × 1.0/1.3, plus landscape. Synthetic numbers. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class SpendingScreensTest {
    private val current = YearMonth.of(2026, 10)
    private val months = (7 downTo 0).map { current.minusMonths(it.toLong()) }
    private val values = listOf<Long>(3_890_000, 4_120_000, 4_010_000, 1_111_100, 3_970_000, 5_210_000, 1_111_100, 1_111_100)
    private val bars = months.mapIndexed { i, m ->
        Bar(m.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ENGLISH), listOf(values[i]), DateLabels.monthYear(m), inProgress = i == 7)
    }

    private fun share(key: String, name: String, icon: String, cents: Long, count: Int) =
        ShareRow(key, name, icon, key, null, null, cents, count, cents / 1_111_100f, setOf(key))

    private val september = SpendingUi(
        loaded = true,
        month = YearMonth.of(2026, 9),
        current = current,
        earliest = YearMonth.of(2025, 1),
        totals = PeriodTotals(spentCents = 1_111_100, feeCents = 41_200, inCents = 6_100_000),
        deltaPercent = 9,
        comparedWith = "Aug",
        months = months,
        bars = bars,
        shares = listOf(
            share(Categories.SENT_TO_PEOPLE, "Sent to people", "fluent_outbox_tray", 1_111_100, 23),
            share(Categories.FUEL, "Fuel", "fluent_fuel_pump", 1_111_100, 4),
            share(Categories.GROCERIES, "Groceries", "fluent_shopping_cart", 111_100, 9),
            share(Categories.ELECTRICITY, "Electricity", "fluent_high_voltage", 245_000, 3),
            share(Categories.OTHER, "Other", "fluent_package", 3_000, 1),
        ),
    )

    @Test
    fun pastMonth() = snapScreen("spending_past") {
        ShellFrame(Tab.ACTIVITY, onSelect = {}) {
            ActivityContent(ActivitySegment.SPENDING, onSegment = {}, filterCount = 0, onFilters = {}) { SpendingPane(september, SpendingActions()) }
        }
    }

    @Test
    fun currentMonthEmpty() = snapScreen("spending_empty") {
        val empty = september.copy(month = current, totals = PeriodTotals(0, 0, 0), deltaPercent = null, comparedWith = "same days Sep", shares = emptyList())
        ShellFrame(Tab.ACTIVITY, onSelect = {}) {
            ActivityContent(ActivitySegment.SPENDING, onSegment = {}, filterCount = 0, onFilters = {}) { SpendingPane(empty, SpendingActions()) }
        }
    }

    @Test
    fun landscape() = snapScreenLandscape("spending") {
        ShellFrame(Tab.ACTIVITY, onSelect = {}) {
            ActivityContent(ActivitySegment.SPENDING, onSegment = {}, filterCount = 0, onFilters = {}) { SpendingPane(september, SpendingActions()) }
        }
    }
}
