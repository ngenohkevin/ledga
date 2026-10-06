package com.ledga.app.ui.activity

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.ledga.app.data.derive.FlowFilter
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.LineRow
import com.ledga.app.testing.txRow
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.core.model.Categories
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ActivityBehaviourTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.parse("2026-10-06")
    private val created = Instant.parse("2026-01-01T00:00:00Z")
    private val complete = LoadStates(LoadState.NotLoading(false), LoadState.NotLoading(true), LoadState.NotLoading(true))
    private val categories = Categories.SEED.associate {
        it.key to CategoryRow(it.key, it.name, it.group, it.icon3d, null, null, it.tracked, it.sortOrder, CategoryOrigin.SYSTEM, false)
    }
    private val lines = listOf(LineRow(1, 1, "0712000023", "Personal", "#0E9F6E", true, created), LineRow(2, 2, "0733000087", "Business", "#1E7FD8", false, created))
    private val ui = TransactionsUi(categories = categories, lines = lines, today = today)

    private fun showPane(actions: TransactionsActions) = compose.setContent {
        LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
            val items = remember { flowOf(PagingData.from(listOf(txRow(at = Instant.parse("2026-10-06T11:15:00Z"))), complete).toActivityItems()) }
                .collectAsLazyPagingItems()
            TransactionsPane(ui, items, actions)
        }
    }

    @Test
    fun `tapping a row opens it and a long press changes its category`() {
        val opened = mutableListOf<String>()
        val picked = mutableListOf<String>()
        showPane(TransactionsActions(onOpen = { opened += it }, onPickCategory = { picked += it }))
        val row = compose.onNodeWithContentDescription("Ksh 1,000 spent at KPLC Prepaid, today 2:15 PM")
        row.performClick()
        row.performTouchInput { longClick() }
        assertEquals(listOf("TJK4AB12FA"), opened)
        assertEquals(listOf("TJK4AB12FA"), picked)
    }

    @Test
    fun `the flow chips are one choice and a line chip toggles`() {
        val flows = mutableListOf<FlowFilter>()
        val lineTaps = mutableListOf<Long>()
        showPane(TransactionsActions(onFlow = { flows += it }, onLine = { lineTaps += it }))
        compose.onNodeWithText("Money in").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()
        compose.onNodeWithText("Business ··87").performScrollTo().performClick()
        assertEquals(listOf(FlowFilter.IN), flows)
        assertEquals(listOf(2L), lineTaps)
    }

    @Test
    fun `the filter button says how many filters are on`() {
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                ActivityContent(ActivitySegment.TRANSACTIONS, onSegment = {}, filterCount = 2, onFilters = {}) {}
            }
        }
        compose.onNodeWithContentDescription("Filters, 2 on").assertExists()
    }

    @Test
    fun `the filter sheet applies what was chosen`() {
        val applied = mutableListOf<TransactionFilter>()
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    FilterSheetContent(TransactionFilter(), categories.values.sortedBy { it.sortOrder }, Instant.parse("2026-10-06T06:00:00Z")) { applied += it }
                }
            }
        }
        compose.onNodeWithText("Electricity").performScrollTo().performClick()
        compose.onNodeWithText("Last month").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("1,000")
        compose.onNodeWithText("Show hidden payments").performScrollTo().performClick()
        compose.onNodeWithText("Show results").performScrollTo().performClick()
        val f = applied.single()
        assertEquals(setOf(Categories.ELECTRICITY), f.categoryKeys)
        assertEquals("Last month", f.dates?.label)
        assertEquals(100_000L, f.minAmountCents)
        assertEquals(true, f.includeHidden)
    }

    @Test
    fun `the filter count sits on the button's edge, clear of its round clip`() {
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                ActivityContent(ActivitySegment.TRANSACTIONS, onSegment = {}, filterCount = 2, onFilters = {}) {}
            }
        }
        // Unclipped: boundsInRoot trims a node to its parents' bounds, which the overhanging badge passes by design.
        val button = compose.onNodeWithContentDescription("Filters, 2 on").getUnclippedBoundsInRoot()
        val count = compose.onNodeWithTag("filter-count", useUnmergedTree = true).getUnclippedBoundsInRoot()
        // Inside the circle the clip shaved the badge and the digit (seen on the S26); on the edge nothing clips it.
        assertTrue(count.top < button.top && count.right > button.right, "badge $count inside button $button")
    }

    @Test
    fun `a search link puts the cursor in the search field, once`() {
        var consumed = 0
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                val items = remember { flowOf(PagingData.from(listOf(txRow(at = Instant.parse("2026-10-06T11:15:00Z"))), complete).toActivityItems()) }
                    .collectAsLazyPagingItems()
                TransactionsPane(ui.copy(searchFocus = 1), items, TransactionsActions(onSearchFocused = { consumed++ }))
            }
        }
        compose.onNode(hasSetTextAction()).assertIsFocused()
        assertEquals(1, consumed, "the pane hands the request back, so a later visit doesn't take the focus again")
    }
}
