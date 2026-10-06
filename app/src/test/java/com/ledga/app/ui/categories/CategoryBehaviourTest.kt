package com.ledga.app.ui.categories

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.core.model.Categories
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class CategoryBehaviourTest {
    @get:Rule val compose = createComposeRule()

    private fun category(key: String) = Categories.seed(key)!!.let {
        CategoryRow(it.key, it.name, it.group, it.icon3d, null, null, false, it.sortOrder, CategoryOrigin.SYSTEM, false)
    }

    @Test
    fun `a rule is a switch, and only your own has Delete`() {
        val switched = mutableListOf<Pair<Long, Boolean>>()
        val deleted = mutableListOf<Long>()
        val ui = CategoryUi(
            true, category = category(Categories.GROCERIES),
            rules = listOf(RuleUi(901, "Name has Sample Grocer", builtIn = false, enabled = true), RuleUi(12, "Name has Quickmart", builtIn = true, enabled = false)),
        )
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                CategoryContent(ui, CategoryActions(onRuleEnabled = { id, on -> switched += id to on }, onDeleteRule = { deleted += it.id }))
            }
        }
        compose.onNodeWithText("Name has Quickmart").performScrollTo()
        compose.onNodeWithText("Name has Quickmart", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Delete rule: Name has Quickmart").assertDoesNotExist()
        compose.onNodeWithContentDescription("Delete rule: Name has Sample Grocer").performScrollTo().performClick()
        assertEquals(listOf(901L), deleted)
        compose.onNodeWithText("Built-in · off").performScrollTo().performClick()
        assertEquals(listOf(12L to true), switched)
    }

    @Test
    fun `Money in has no tracking, and a built-in category has no icon, colour or archive`() {
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) { CategoryContent(CategoryUi(true, category = category(Categories.RECEIVED)), CategoryActions()) }
        }
        compose.onNodeWithText("Track month by month").assertDoesNotExist()
        compose.onNodeWithText("Icon").assertDoesNotExist()
        compose.onNodeWithText("Colour").assertDoesNotExist()
        compose.onNodeWithText("Archive category").assertDoesNotExist()
    }
}
