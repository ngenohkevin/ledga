package com.ledga.app.ui.trackers

import androidx.compose.ui.layout.findRootCoordinates
import kotlin.test.assertTrue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import com.ledga.app.data.edit.RulePreview
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.testing.txRow
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.core.model.Categories
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.ledga.app.ui.rules.ShownPreview
import com.ledga.app.ui.rules.AddRuleContent

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class TrackerDetailBehaviourTest {
    @get:Rule val compose = createComposeRule()
    private val seed = Categories.seed(Categories.ELECTRICITY)!!
    private val ui = TrackerDetailUi(
        loaded = true,
        category = CategoryRow(seed.key, seed.name, seed.group, seed.icon3d, null, null, true, seed.sortOrder, CategoryOrigin.SYSTEM, false),
        rules = listOf(RuleChipUi(2, "Name has KPLC")),
        payments = listOf(txRow(at = Instant.parse("2026-10-02T07:00:00Z"))),
        year = 2026,
        today = LocalDate.parse("2026-10-06"),
    )

    private fun show(state: TrackerDetailUi, actions: TrackerDetailActions) = compose.setContent {
        LedgaTheme(Appearance.LIGHT, reducedMotion = true) { TrackerDetailContent(state, actions) }
    }

    @Test
    fun `Edit puts a remove button on each rule, and removing one hands it back for Undo`() {
        val calls = mutableListOf<String>()
        show(ui.copy(editing = true), TrackerDetailActions(onEdit = { calls += "done" }, onRemoveRule = { calls += "remove ${it.id}" }, onAddRule = { calls += "add" }))
        compose.onNodeWithContentDescription("Remove rule Name has KPLC").performClick()
        compose.onNodeWithText("Add rule").performClick()
        compose.onNodeWithText("Done").performClick()
        assertEquals(listOf("remove 2", "add", "done"), calls)
    }

    @Test
    fun `the overflow menu renames or stops tracking, and Back goes back`() {
        val calls = mutableListOf<String>()
        show(ui, TrackerDetailActions(onRename = { calls += "rename" }, onStopTracking = { calls += "stop" }, onBack = { calls += "back" }))
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Rename").performClick()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Stop tracking").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("rename", "stop", "back"), calls)
    }

    @Test
    fun `a payment opens, and a long press changes its category`() {
        val calls = mutableListOf<String>()
        show(ui, TrackerDetailActions(onOpenTx = { calls += "open $it" }, onPickCategory = { calls += "pick $it" }))
        compose.onNodeWithText("KPLC Prepaid").performClick()
        compose.onNodeWithText("KPLC Prepaid").performSemanticsAction(SemanticsActions.OnLongClick)
        assertEquals(listOf("open TJK4AB12FA", "pick TJK4AB12FA"), calls)
    }

    @Test
    fun `at 1_3x a long rule chip still shows its remove button`() {
        val long = "KPLC Prepaid · account 37100000001"
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                    TrackerDetailContent(ui.copy(rules = listOf(RuleChipUi(1, long)), editing = true), TrackerDetailActions())
                }
            }
        }
        val remove = compose.onNodeWithContentDescription("Remove rule $long").fetchSemanticsNode()
        assertTrue(remove.size.width > 0 && remove.boundsInRoot.right <= remove.layoutInfo.coordinates.findRootCoordinates().size.width, "the × is pushed out of the chip")
        compose.onNodeWithContentDescription("Remove rule $long").assertIsDisplayed()
    }

    @Test
    fun `Save waits while the text typed is still being counted (R48)`() {
        var saves = 0
        var name by mutableStateOf("sample wate")
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                AddRuleContent("Water", name, "", ShownPreview("sample wate", "", RulePreview(1, 1, 0)), onName = {}, onAccount = {}, onSave = { saves++ })
            }
        }
        compose.onNodeWithText("Save rule").assertIsEnabled()
        name = "sample water"
        compose.onNodeWithText("Counting${Char(0x2026)}").assertIsDisplayed()
        compose.onNodeWithText("Save rule").assertIsNotEnabled().performClick()
        assertEquals(0, saves, "the count on screen was for other text")
    }
}
