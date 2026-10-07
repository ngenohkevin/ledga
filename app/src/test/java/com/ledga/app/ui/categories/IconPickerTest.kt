package com.ledga.app.ui.categories

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.ledga.app.ui.design.icons.IconCatalog
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** D5, R98: the icon sheet. A synthetic three-icon set; the real set is IconCatalogTest's. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class IconPickerTest {
    @get:Rule val compose = createComposeRule()
    private val icons = IconCatalog.parse(
        "key\tname\tgroup\tkeywords\n" +
            "fluent_grinning_face\tgrinning face\tSmileys & Emotion\tface|grin\n" +
            "fluent_dog_face\tdog face\tAnimals & Nature\tdog|face|pet\n" +
            "fluent_hot_beverage\thot beverage\tFood & Drink\tcoffee|drink|tea\n",
    )
    private val picked = mutableListOf<String>()

    private fun show(selected: String = "fluent_dog_face", startQuery: String = "") = compose.setContent {
        var query by remember { mutableStateOf(startQuery) }
        LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
            IconPickerContent(selected, icons, query, onQuery = { query = it }, onPick = { picked += it })
        }
    }

    @Test
    fun `Suggested comes first, then the set by its groups, with the current icon chosen`() {
        show()
        compose.onNodeWithText("SUGGESTED").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Dog face"))
        compose.onNodeWithText("ANIMALS & NATURE").assertIsDisplayed()
        // Dog face is in Suggested too: every copy of the current icon is ringed.
        compose.onAllNodesWithContentDescription("Dog face").assertCountEquals(2).assertAll(isSelected())
    }

    @Test
    fun `search narrows to names and keywords`() {
        show(startQuery = "pet")
        compose.onNodeWithContentDescription("Dog face").assertIsDisplayed()
        compose.onNodeWithText("SUGGESTED").assertDoesNotExist()
        compose.onNodeWithContentDescription("Hot beverage").assertDoesNotExist()
    }

    @Test
    fun `a query that matches nothing says so`() {
        show(startQuery = "zebra")
        compose.onNodeWithText(IconPickerText.noMatch("zebra")).assertIsDisplayed()
    }

    @Test
    fun `a tap picks the icon`() {
        show(startQuery = "coffee")
        compose.onNodeWithContentDescription("Hot beverage").performClick()
        assertEquals(listOf("fluent_hot_beverage"), picked)
    }

    @Test
    fun `group names read in sentence case`() {
        assertEquals("Smileys & emotion", IconPickerText.groupLabel("Smileys & Emotion"))
        assertEquals("Food & drink", IconPickerText.groupLabel("Food & Drink"))
        assertEquals("No icon matches \"zebra\"", IconPickerText.noMatch("zebra"))
    }
}
