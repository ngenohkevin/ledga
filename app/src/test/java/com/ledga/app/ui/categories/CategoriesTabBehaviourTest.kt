package com.ledga.app.ui.categories

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.trackers.CategoryMeasure
import com.ledga.app.data.trackers.TrackerSummary
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.core.chart.Bucket
import com.ledga.core.model.Categories
import com.ledga.core.model.CategoryGroup
import com.ledga.core.money.Money
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 4e D2, R92, R97: what the Categories tab's taps do. Synthetic values. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class CategoriesTabBehaviourTest {
    @get:Rule val compose = createComposeRule()
    private val categories = Categories.SEED.map { CategoryRow(it.key, it.name, it.group, it.icon3d, null, null, it.tracked, it.sortOrder, CategoryOrigin.SYSTEM, false) }
    private val electricity = TrackerSummary(
        categories.first { it.key == Categories.ELECTRICITY },
        Periods.lastN(PeriodType.MONTH, Instant.parse("2026-10-06T06:00:00Z"), 13).map { Bucket(it, Money(95_000), 1) },
        95_000, null, null,
    )
    private val school = categories.first { it.key == Categories.SCHOOL }

    private val groceries = categories.first { it.key == Categories.GROCERIES }
    private val wedding = CategoryRow("user_wedding", "Wedding", CategoryGroup.EVERYDAY, "fluent_church", null, null, false, 100, CategoryOrigin.USER, true)
    private val tab = CategoriesTabUi(
        loaded = true,
        trackers = listOf(electricity),
        untracked = listOf(school),
        groups = listOf(TabGroupUi(CategoryGroup.EVERYDAY, listOf(CategoryLineUi(groceries, 64_000, CategoryMeasure.SPENT)))),
        archived = listOf(CategoryLineUi(wedding, 0, CategoryMeasure.SPENT)),
        today = LocalDate.parse("2026-10-06"),
    )
    private val calls = mutableListOf<String>()
    private val actions = CategoriesTabActions(
        onOpen = { calls += "open $it" },
        onTrackNew = { calls += "track" },
        onNew = { calls += "new" },
        onCreate = { calls += "create $it" },
    )

    private fun show(ui: CategoriesTabUi = tab) = compose.setContent { LedgaTheme(Appearance.LIGHT, reducedMotion = true) { CategoriesTabContent(ui, actions) } }

    @Test
    fun `a tracker opens its page, plus makes a category, and the last tracker row offers one to track`() {
        show()
        compose.onNodeWithContentDescription("Electricity, Ksh 950 this month", substring = true).performClick() // the legend says "Electricity" too
        compose.onNodeWithContentDescription("New category").performClick()
        compose.onNodeWithText("+ Track another category").performClick()
        assertEquals(listOf("open electricity", "new", "track"), calls)
    }

    @Test
    fun `a category row opens its page, and Archived opens to show archived ones (R97)`() {
        show()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Groceries"))
        compose.onNodeWithText("Groceries").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(CategoriesTabText.archivedLabel(1)))
        compose.onNodeWithText("Wedding").assertDoesNotExist()
        compose.onNodeWithText(CategoriesTabText.archivedLabel(1)).performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Wedding"))
        compose.onNodeWithText("Wedding").performClick()
        assertEquals(listOf("open groceries", "open user_wedding"), calls)
    }

    @Test
    fun `no match offers to create it, with the name filled in (R92)`() {
        show(tab.copy(query = " Chama ", results = emptyList()))
        compose.onNodeWithText(CategoriesTabText.createLabel(" Chama ")).performClick()
        assertEquals(listOf("create Chama"), calls)
    }

    @Test
    fun `New category asks for a name and a group, and Create waits for a name (R92)`() {
        var name by mutableStateOf("")
        var group by mutableStateOf(CategoryGroup.EVERYDAY)
        val saved = mutableListOf<String>()
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                NewCategoryContent(name, { name = it }, group, { group = it }, onSave = { saved += "$name in ${group.displayName}" })
            }
        }
        compose.onNodeWithText("Create").assertIsNotEnabled()
        compose.onNodeWithText("Name").performTextInput("Pets")
        compose.onNodeWithText("Car").performClick()
        compose.onNodeWithText("Create").assertIsEnabled().performClick()
        assertEquals(listOf("Pets in Car"), saved)
    }

    @Test
    fun `picking a category tracks it`() {
        val picked = mutableListOf<String>()
        compose.setContent { LedgaTheme(Appearance.LIGHT, reducedMotion = true) { TrackCategoryContent(listOf(school), onTrack = { picked += it }) } }
        compose.onNodeWithText("School").performClick()
        assertEquals(listOf(Categories.SCHOOL), picked)
    }
}
