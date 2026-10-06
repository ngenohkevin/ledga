package com.ledga.app.ui.categories

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.core.model.Categories
import com.ledga.core.model.CategoryGroup
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
class CategoriesBehaviourTest {
    @get:Rule val compose = createComposeRule()
    private val seeded = Categories.SEED.map {
        CategoryRow(it.key, it.name, it.group, it.icon3d, null, null, it.tracked, it.sortOrder, CategoryOrigin.SYSTEM, false)
    }
    private val ui = CategoriesUi(true, CategoryGroup.entries.map { g -> CategoryGroupUi(g, seeded.filter { it.groupKey == g }.map { CategoryListRow(it, 0, 0) }) })

    @Test
    fun `a row opens its category, and New category asks in that group`() {
        val opened = mutableListOf<String>()
        val asked = mutableListOf<CategoryGroup>()
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) { CategoriesContent(ui, CategoriesActions(onOpen = { opened += it }, onNew = { asked += it })) }
        }
        // A lazy list composes only what is near the screen: scroll the list to each node first.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Groceries"))
        compose.onNodeWithText("Groceries").performClick()
        assertEquals(listOf(Categories.GROCERIES), opened)
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("New category in Everyday"))
        compose.onNodeWithContentDescription("New category in Everyday").performClick()
        assertEquals(listOf(CategoryGroup.EVERYDAY), asked)
    }

    @Test
    fun `Create waits for a name`() {
        compose.setContent { LedgaTheme(Appearance.LIGHT, reducedMotion = true) { NewCategoryContent("  ", onName = {}, onSave = {}) } }
        compose.onNodeWithText("Create").assertIsNotEnabled()
    }
}
