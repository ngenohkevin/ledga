package com.ledga.app.data.edit

import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.core.model.CategoryGroup
import com.ledga.core.model.Categories
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** D6: whether "Reset to default" has anything to do. */
class CategoryLooksTest {
    private val groceries = Categories.seed(Categories.GROCERIES)!!.let {
        CategoryRow(it.key, it.name, it.group, it.icon3d, null, null, false, it.sortOrder, CategoryOrigin.SYSTEM, false)
    }

    @Test
    fun `a built-in category differs from its seed once its icon or colour changes, and yours never does`() {
        assertFalse(CategoryLooks.differsFromSeed(groceries))
        assertTrue(CategoryLooks.differsFromSeed(groceries.copy(icon3d = "fluent_teapot")))
        assertTrue(CategoryLooks.differsFromSeed(groceries.copy(color = "#0277BD", colorDark = "#63C3FF")))
        val pets = CategoryRow("user_pets", "Pets", CategoryGroup.EVERYDAY, "fluent_dog_face", "#0277BD", "#63C3FF", false, 100, CategoryOrigin.USER, false)
        assertFalse(CategoryLooks.differsFromSeed(pets))
    }

    @Test
    fun `an icon key is fluent_ and lower-case words`() {
        assertTrue(CategoryLooks.ICON_KEY.matches("fluent_face_with_head_bandage"))
        assertFalse(CategoryLooks.ICON_KEY.matches("fluent_"))
        assertFalse(CategoryLooks.ICON_KEY.matches("Fluent_dog"))
        assertFalse(CategoryLooks.ICON_KEY.matches("fluent_dog-face"))
    }
}
