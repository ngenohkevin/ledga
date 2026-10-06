package com.ledga.app.ui.activity

import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.ledga.app.testing.txRow
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

/** Owner ruling M5: landscape is supported, so a short Transactions pane gives its height to the payments. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w800dp-h360dp-xhdpi")
class ActivityLandscapeTest {
    @get:Rule val compose = createComposeRule()
    private val complete = LoadStates(LoadState.NotLoading(false), LoadState.NotLoading(true), LoadState.NotLoading(true))

    @Test
    fun `in a short pane the search and chips scroll away with the list`() {
        val rows = (0 until 12).map { i -> txRow(code = "TJK4AB12M${'A' + i}", at = Instant.parse("2026-10-06T11:15:00Z").minusSeconds(60L * i)) }
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                val items = remember { flowOf(PagingData.from(rows, complete).toActivityItems()) }.collectAsLazyPagingItems()
                TransactionsPane(TransactionsUi(today = LocalDate.parse("2026-10-06")), items, TransactionsActions())
            }
        }
        compose.onNodeWithText("Money out").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(13)
        compose.onNodeWithText("Money out").assertDoesNotExist()
    }
}
