package com.ledga.app.ui.categories

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.trackers.CategoryMeasure
import com.ledga.app.testing.txRow
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.core.model.CategoryGroup
import com.ledga.core.model.Categories
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals

/** 4e §3.2: what the page's taps do. Synthetic values; the ViewModel is CategoryPageViewModelTest's. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class CategoryPageBehaviourTest {
    @get:Rule val compose = createComposeRule()
    private val electricity = Categories.seed(Categories.ELECTRICITY)!!.let {
        CategoryRow(it.key, it.name, it.group, it.icon3d, null, null, true, it.sortOrder, CategoryOrigin.SYSTEM, false)
    }
    private val received = Categories.seed(Categories.RECEIVED)!!.let {
        CategoryRow(it.key, it.name, it.group, it.icon3d, null, null, false, it.sortOrder, CategoryOrigin.SYSTEM, false)
    }
    private val ui = CategoryPageUi(
        loaded = true,
        category = electricity,
        thisMonthCents = 90_000,
        topPlaces = listOf(TopPlaceUi("kplc", "Kplc Prepaid", 90_000, 1)),
        payments = listOf(txRow()),
        paymentCount = 1,
        rules = listOf(RuleUi(11, "Name has KPLC", builtIn = true, enabled = true), RuleUi(901, "Name has Sample Power", builtIn = false, enabled = true)),
        today = LocalDate.parse("2026-10-06"),
    )
    private val calls = mutableListOf<String>()
    private val actions = CategoryPageActions(
        onBack = { calls += "back" },
        onRename = { calls += "rename" },
        onTracked = { calls += "tracked $it" },
        onOpenTx = { calls += "open $it" },
        onPickCategory = { calls += "pick $it" },
        onSeeAll = { calls += "all" },
        onPlace = { calls += "place ${it.counterpartyKey}" },
        onIcon = { calls += "icon" },
        onColour = { calls += "colour" },
        onRuleEnabled = { id, on -> calls += "rule $id $on" },
        onDeleteRule = { calls += "delete ${it.id}" },
    )

    private fun show(state: CategoryPageUi = ui) = compose.setContent { LedgaTheme(Appearance.LIGHT, reducedMotion = true) { CategoryPageContent(state, actions) } }

    private fun scrollTo(text: String) = compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))

    @Test
    fun `the menu renames or stops tracking, and Back goes back`() {
        show()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Rename").performClick()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Stop tracking").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("rename", "tracked false", "back"), calls)
    }

    @Test
    fun `a top place and See all open payments`() {
        show()
        compose.onNodeWithText("Kplc Prepaid", useUnmergedTree = true).performClick()
        scrollTo("See all")
        compose.onNodeWithText("See all").performClick()
        assertEquals(listOf("place kplc", "all"), calls)
    }

    @Test
    fun `settings take an icon, a colour and Show on Trackers, and every rule is a switch with Delete only for yours`() {
        show()
        scrollTo("Icon")
        compose.onNodeWithText("Icon").performClick()
        scrollTo("Colour")
        compose.onNodeWithText("Colour").performClick()
        scrollTo("Show on Trackers")
        compose.onNodeWithText("Show on Trackers").performClick()
        scrollTo("Name has KPLC")
        compose.onNodeWithText("Name has KPLC").performClick()
        scrollTo("Name has Sample Power")
        compose.onNodeWithContentDescription("Delete rule: Name has Sample Power").performClick()
        compose.onNodeWithContentDescription("Delete rule: Name has KPLC").assertDoesNotExist()
        assertEquals(listOf("icon", "colour", "tracked false", "rule 11 false", "delete 901"), calls)
    }

    @Test
    fun `Money in has no Show on Trackers, and a built-in category has no Archive`() {
        show(ui.copy(category = received, measure = CategoryMeasure.RECEIVED))
        compose.onNodeWithText("Money received").assertIsDisplayed()
        scrollTo("Colour")
        compose.onNodeWithText("Show on Trackers").assertDoesNotExist()
        compose.onNodeWithText("Archive category").assertDoesNotExist()
    }

    @Test
    fun `a category with no payments says so, and its settings still show`() {
        show(ui.copy(payments = emptyList(), paymentCount = 0, topPlaces = emptyList(), thisMonthCents = 0))
        compose.onNodeWithText("No payments in Electricity yet").assertIsDisplayed()
        compose.onNodeWithText("This month").assertDoesNotExist()
        scrollTo("Icon")
        compose.onNodeWithText("Icon").assertIsDisplayed()
    }
}
