package com.ledga.app.ui.tx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ledga.app.data.edit.ApplyCounts
import com.ledga.app.testing.snapScreen
import com.ledga.app.testing.snapScreenLandscape
import com.ledga.app.ui.design.components.SheetScaffold
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.core.model.Categories
import com.ledga.core.model.CategoryGroup
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Spec §15.2: the category picker's body (mockup `picker`), light/dark × 1.0/1.3, plus landscape. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class CategoryPickerScreensTest {
    private val spendGroups = setOf(CategoryGroup.BILLS_UTILITIES, CategoryGroup.CAR, CategoryGroup.EVERYDAY, CategoryGroup.MONEY)

    /** The seeded spending categories, as the ViewModel groups them. */
    private val groups = Categories.SEED.filter { it.group in spendGroups }.groupBy { it.group }
        .map { (group, seeds) -> PickerGroup(group, seeds.map { PickerItem(it.key, it.name, it.icon3d, it.tracked) }) }

    private val kplc = PickerState(
        loaded = true,
        txName = "KPLC Prepaid",
        account = "37100000001",
        groups = groups,
        selected = Categories.ELECTRICITY,
        counts = ApplyCounts(fromName = 14, forAccount = 9),
        applyAll = true,
    )

    @Composable
    private fun Sheet(state: PickerState, searchOpen: Boolean = false) {
        Box(Modifier.fillMaxSize().background(LedgaTheme.colors.canvas), contentAlignment = Alignment.BottomCenter) {
            SheetScaffold(title = null) {
                Column(Modifier.verticalScroll(rememberScrollState())) { CategoryPickerContent(state, PickerActions(), searchOpen = searchOpen) }
            }
        }
    }

    @Test
    fun picker() = snapScreen("picker_spend") { Sheet(kplc) }

    @Test
    fun newCategory() = snapScreen("picker_new") {
        Sheet(kplc.copy(selected = Categories.GROCERIES, newCategoryIn = CategoryGroup.EVERYDAY, applyAll = false, counts = ApplyCounts(1, 1)), searchOpen = true)
    }

    @Test
    fun landscape() = snapScreenLandscape("picker") { Sheet(kplc) }
}
