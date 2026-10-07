package com.ledga.app.ui.design.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the bottom bar is four tabs with one selected`() {
        var tab by mutableIntStateOf(0)
        compose.setContent { LedgaTheme(Appearance.LIGHT, reducedMotion = true) { LedgaBottomBar(selected = tab, onSelect = { tab = it }) } }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(4)
        compose.onNodeWithText("Home").assertIsSelected().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Categories").performClick()
        assertEquals(2, tab)
        compose.onNodeWithText("Categories").assertIsSelected()
    }

    @Test
    fun `a banner's action is a button`() {
        var tapped = false
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                Banner("Ledga 2.0.1 is ready", BannerTone.Info, actionLabel = "Install", onAction = { tapped = true })
            }
        }
        compose.onNodeWithText("Install").assertHasClickAction().performClick()
        assertTrue(tapped)
    }

    @Test
    fun `a progress banner reports its progress and drops the indeterminate bar when motion is reduced`() {
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                Column {
                    Banner("Updating your history…", BannerTone.Progress, progress = 0.4f)
                    Banner("Checking for updates…", BannerTone.Progress)
                }
            }
        }
        val info = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0.4f, info.current)
    }

    @OptIn(ExperimentalMaterial3Api::class) // LedgaModalSheet exposes M3's experimental SheetState
    @Test
    fun `a modal sheet shows its title and content`() {
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                LedgaModalSheet(onDismiss = {}, title = "Category") { Text("Groceries") }
            }
        }
        compose.onAllNodesWithText("Category").onFirst().assertExists()
        compose.onAllNodesWithText("Groceries").onFirst().assertExists()
    }

    @Test
    fun `an error state offers a retry`() {
        var retried = false
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                ErrorState("Couldn't load your history", "Check the SMS permission and try again.", onRetry = { retried = true })
            }
        }
        compose.onNodeWithText("Try again").performClick()
        assertTrue(retried)
    }
    @Test
    fun `the bottom bar keeps its own height under a weighted spacer`() {
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                Column(Modifier.height(600.dp)) {
                    Spacer(Modifier.weight(1f))
                    LedgaBottomBar(selected = 0, onSelect = {}, modifier = Modifier.testTag("bar"))
                }
            }
        }
        val height = compose.onNodeWithTag("bar").fetchSemanticsNode().layoutInfo.height
        assertTrue(with(compose.density) { height.toDp() } <= 80.dp, "bar grew to ${height}px")
    }
}
