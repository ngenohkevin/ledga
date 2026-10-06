package com.ledga.app.ui.activity

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ledga.app.data.room.dao.PeriodTotals
import com.ledga.app.ui.design.charts.Bar
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.core.model.Categories
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class SpendingBehaviourTest {
    @get:Rule val compose = createComposeRule()
    private val october = YearMonth.of(2026, 10)
    private val fuel = ShareRow(Categories.FUEL, "Fuel", "fluent_fuel_pump", Categories.FUEL, null, null, 300_000, 2, 1f, setOf(Categories.FUEL))

    /** History began this month: nothing to step back to, nothing after it. */
    private val only = SpendingUi(
        loaded = true,
        month = october,
        current = october,
        earliest = october,
        totals = PeriodTotals(300_000, 0, 0),
        months = listOf(october),
        bars = listOf(Bar("OCT", listOf(300_000L), "October 2026 so far: Ksh 3,000", inProgress = true)),
        shares = listOf(fuel),
    )

    @Test
    fun `the month stepper stops at the first and the current month`() {
        compose.setContent { LedgaTheme(Appearance.LIGHT, reducedMotion = true) { SpendingPane(only, SpendingActions()) } }
        compose.onNodeWithContentDescription("Previous month").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next month").assertIsNotEnabled()
    }

    @Test
    fun `tapping a category shows its transactions`() {
        val tapped = mutableListOf<ShareRow>()
        compose.setContent { LedgaTheme(Appearance.LIGHT, reducedMotion = true) { SpendingPane(only, SpendingActions(onShare = { tapped += it })) } }
        compose.onNodeWithText("Fuel").performScrollTo().performClick()
        assertEquals(listOf(fuel), tapped)
    }
}
