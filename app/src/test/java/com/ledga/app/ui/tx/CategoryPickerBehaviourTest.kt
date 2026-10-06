package com.ledga.app.ui.tx

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ledga.app.data.edit.ApplyCounts
import com.ledga.app.ui.design.components.SheetScaffold
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.core.model.Categories
import com.ledga.core.model.CategoryGroup
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class CategoryPickerBehaviourTest {
    @get:Rule val compose = createComposeRule()

    private val kplc = PickerState(
        loaded = true,
        txName = "KPLC Prepaid",
        account = "37100000001",
        groups = Categories.SEED.filter { it.group == CategoryGroup.BILLS_UTILITIES }
            .let { seeds -> listOf(PickerGroup(CategoryGroup.BILLS_UTILITIES, seeds.map { PickerItem(it.key, it.name, it.icon3d, it.tracked) })) },
        selected = Categories.ELECTRICITY,
        counts = ApplyCounts(fromName = 14, forAccount = 9),
        applyAll = true,
    )

    @Test
    fun `categories are radio buttons and a tracked one says so`() {
        compose.setContent { LedgaTheme(Appearance.LIGHT, reducedMotion = true) { CategoryPickerContent(kplc, PickerActions()) } }
        compose.onNodeWithContentDescription("Electricity, tracked")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
    }

    @Test
    fun `tapping a category picks it, and the apply-to-all line is one switch`() {
        val picked = mutableListOf<String>()
        val applied = mutableListOf<Boolean>()
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CategoryPickerContent(kplc, PickerActions(onSelect = { picked += it }, onApplyAll = { applied += it }))
                }
            }
        }
        compose.onNodeWithContentDescription("Water, tracked").performClick()
        compose.onNodeWithText("Apply to all 14 from KPLC Prepaid")
            .performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .performClick()
        assertEquals(listOf(Categories.WATER), picked)
        assertEquals(listOf(false), applied)
    }

    @Test
    fun `at 1_3x on a 360 dp phone every category name wraps between words, never inside one`() {
        val spendGroups = setOf(CategoryGroup.BILLS_UTILITIES, CategoryGroup.CAR, CategoryGroup.EVERYDAY, CategoryGroup.MONEY)
        val spend = kplc.copy(
            groups = Categories.SEED.filter { it.group in spendGroups }.groupBy { it.group }
                .map { (group, seeds) -> PickerGroup(group, seeds.map { PickerItem(it.key, it.name, it.icon3d, it.tracked) }) },
        )
        val longestWord = mutableMapOf<String, Int>()
        var cellPadding = 0f
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                    val measurer = rememberTextMeasurer()
                    spend.groups.flatMap { it.items }.forEach { item ->
                        longestWord[item.name] = item.name.split(' ').maxOf { measurer.measure(it, LedgaType.caption).size.width }
                    }
                    cellPadding = with(LocalDensity.current) { 4.dp.toPx() }
                    SheetScaffold(title = null) {
                        Column(Modifier.verticalScroll(rememberScrollState())) { CategoryPickerContent(spend, PickerActions()) }
                    }
                }
            }
        }
        spend.groups.flatMap { it.items }.forEach { item ->
            val cell = compose.onNodeWithContentDescription(if (item.tracked) "${item.name}, tracked" else item.name).fetchSemanticsNode()
            val room = cell.size.width - cellPadding
            assertTrue(room >= longestWord.getValue(item.name), "${item.name}: its longest word needs ${longestWord[item.name]} px, the cell gives $room")
        }
    }
}
