package com.ledga.app.ui.categories

import com.ledga.app.data.edit.CategoryLooks
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.testing.snapScreen
import com.ledga.app.testing.snapScreenLandscape
import com.ledga.app.ui.app.ShellFrame
import com.ledga.app.ui.design.components.SheetScaffold
import com.ledga.core.model.Categories
import com.ledga.core.model.CategoryGroup
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Spec §15.2: a category's screen (not mocked; R67), its icon and colour sheets, landscape. Synthetic values. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class CategoryScreensTest {
    private val groceries = Categories.seed(Categories.GROCERIES)!!.let {
        CategoryRow(it.key, it.name, it.group, it.icon3d, null, null, false, it.sortOrder, CategoryOrigin.SYSTEM, false)
    }
    private val wedding = CategoryRow("user_wedding", "Wedding", CategoryGroup.EVERYDAY, "fluent_church", "#0277BD", "#63C3FF", true, 100, CategoryOrigin.USER, false)

    private val builtIn = CategoryUi(
        loaded = true,
        category = groceries,
        rules = listOf(
            RuleUi(901, "Name has Sample Grocer", builtIn = false, enabled = true),
            RuleUi(11, "Name has Naivas", builtIn = true, enabled = true),
            RuleUi(12, "Name has Quickmart", builtIn = true, enabled = false),
            RuleUi(13, "Name has Carrefour", builtIn = true, enabled = true),
        ),
        payments = 87,
    )
    private val own = CategoryUi(loaded = true, category = wedding, rules = listOf(RuleUi(902, "Name has Sample Venue", false, true)), payments = 3)

    @Test
    fun builtIn() = snapScreen("category") { ShellFrame(null, onSelect = {}) { CategoryContent(builtIn, CategoryActions()) } }

    @Test
    fun own() = snapScreen("category_own") { ShellFrame(null, onSelect = {}) { CategoryContent(own, CategoryActions()) } }

    @Test
    fun icons() = snapScreen("category_icons") { SheetScaffold("Icon") { IconChoiceContent(selected = "fluent_church", onPick = {}) } }

    @Test
    fun colours() = snapScreen("category_colours") { SheetScaffold("Colour") { ColourChoiceContent(selected = CategoryLooks.SWATCHES[8].light, onPick = {}) } }

    @Test
    fun landscape() = snapScreenLandscape("category") { ShellFrame(null, onSelect = {}) { CategoryContent(builtIn, CategoryActions()) } }
}
