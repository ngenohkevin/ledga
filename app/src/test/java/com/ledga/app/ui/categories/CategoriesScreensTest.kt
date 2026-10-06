package com.ledga.app.ui.categories

import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.testing.snapScreen
import com.ledga.app.testing.snapScreenLandscape
import com.ledga.app.ui.app.ShellFrame
import com.ledga.core.model.Categories
import com.ledga.core.model.CategoryGroup
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Spec §15.2: Categories & rules (not mocked; R67), light/dark × 1.0/1.3, landscape. Synthetic values. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class CategoriesScreensTest {
    private val seeded = Categories.SEED.map {
        CategoryRow(it.key, it.name, it.group, it.icon3d, null, null, it.tracked, it.sortOrder, CategoryOrigin.SYSTEM, false)
    }
    private val wedding = CategoryRow("user_wedding", "Wedding", CategoryGroup.EVERYDAY, "fluent_church", "#0277BD", "#63C3FF", false, 100, CategoryOrigin.USER, true)
    private val counts = mapOf(Categories.ELECTRICITY to (1 to 1), Categories.WATER to (5 to 0), Categories.FUEL to (11 to 0), Categories.GROCERIES to (6 to 0))

    private fun row(c: CategoryRow) = (counts[c.key] ?: (0 to 0)).let { (on, off) -> CategoryListRow(c, on, off) }

    private val ui = CategoriesUi(
        loaded = true,
        groups = CategoryGroup.entries.map { g -> CategoryGroupUi(g, seeded.filter { it.groupKey == g }.map(::row)) },
        archived = listOf(row(wedding)),
    )

    @Test
    fun categories() = snapScreen("categories") { ShellFrame(null, onSelect = {}) { CategoriesContent(ui, CategoriesActions()) } }

    @Test
    fun landscape() = snapScreenLandscape("categories") { ShellFrame(null, onSelect = {}) { CategoriesContent(ui, CategoriesActions()) } }
}
