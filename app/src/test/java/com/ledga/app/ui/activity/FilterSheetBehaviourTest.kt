package com.ledga.app.ui.activity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ledga.app.data.derive.DateFilter
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.assertIsSelected
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.core.model.CategoryGroup

/** R70: the custom range in the filter sheet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class FilterSheetBehaviourTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.parse("2026-10-06")

    private fun show(current: TransactionFilter, applied: MutableList<TransactionFilter>) = compose.setContent {
        LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
            Column(Modifier.verticalScroll(rememberScrollState())) { FilterSheetContent(current, emptyList(), today) { applied += it } }
        }
    }

    @Test
    fun `Custom range starts at the first of this month and runs to today`() {
        val applied = mutableListOf<TransactionFilter>()
        show(TransactionFilter(), applied)
        compose.onNodeWithText("Custom range").performScrollTo().performClick()
        compose.onNodeWithText("1 Oct 2026").assertIsDisplayed()
        compose.onNodeWithText("6 Oct 2026").assertIsDisplayed()
        compose.onNodeWithText("Show results").performScrollTo().performClick()
        assertEquals(DateFilter.Custom(LocalDate.parse("2026-10-01"), today), applied.single().dates)
    }

    @Test
    fun `a range already applied shows its days, and a month from Spending keeps its own chip`() {
        val applied = mutableListOf<TransactionFilter>()
        show(TransactionFilter(dates = DateFilter.Custom(LocalDate.parse("2026-09-02"), LocalDate.parse("2026-09-14"))), applied)
        compose.onNodeWithText("2 Sep 2026").assertIsDisplayed()
        compose.onNodeWithText("14 Sep 2026").assertIsDisplayed()
    }

    @Test
    fun `a month Spending sent reads as that month`() {
        show(TransactionFilter(dates = DateFilter.Month(YearMonth.of(2026, 9))), mutableListOf())
        compose.onNodeWithText("September 2026").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a category archived while it is chosen still shows, chosen, and can be cleared (R72)`() {
        val wedding = CategoryRow("user_wedding", "Wedding", CategoryGroup.EVERYDAY, "fluent_church", null, null, false, 100, CategoryOrigin.USER, true)
        val chama = CategoryRow("user_chama", "Chama", CategoryGroup.EVERYDAY, "fluent_label", null, null, false, 101, CategoryOrigin.USER, true)
        val applied = mutableListOf<TransactionFilter>()
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    FilterSheetContent(TransactionFilter(categoryKeys = setOf(wedding.key)), listOf(wedding, chama), today) { applied += it }
                }
            }
        }
        compose.onNodeWithText("Wedding").assertIsSelected()
        compose.onNodeWithText("Chama").assertDoesNotExist()
        compose.onNodeWithText("Wedding").performClick()
        compose.onNodeWithText("Show results").performScrollTo().performClick()
        assertEquals(emptySet(), applied.single().categoryKeys)
    }

    @Test
    fun `on a tall screen the date opens as the calendar`() {
        show(TransactionFilter(dates = DateFilter.Custom(LocalDate.parse("2026-09-02"), LocalDate.parse("2026-09-14"))), mutableListOf())
        compose.onNodeWithText("From").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog())).assertCountEquals(0)
        compose.onAllNodesWithContentDescription("input mode", substring = true).assertCountEquals(1)
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun `on a short screen the date opens as typed fields, with no way into the clipped calendar (S26)`() {
        show(TransactionFilter(dates = DateFilter.Custom(LocalDate.parse("2026-09-02"), LocalDate.parse("2026-09-14"))), mutableListOf())
        compose.onNodeWithText("From").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).assertExists()
        compose.onAllNodesWithContentDescription("input mode", substring = true).assertCountEquals(0)
    }
}
