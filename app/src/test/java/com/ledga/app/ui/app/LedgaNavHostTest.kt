package com.ledga.app.ui.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.home.HomeNav
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The app's routes with stand-in screens: tabs, pushes, and where Back goes. */
@RunWith(RobolectricTestRunner::class)
class LedgaNavHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private object StandIns : LedgaScreens {
        @Composable override fun Onboarding(onDone: () -> Unit) = Text("Onboarding screen")

        @Composable override fun Home(nav: HomeNav) = Column {
            Text("Home screen")
            Button(onClick = { nav.openTracker("electricity") }) { Text("Home tile") }
            Button(onClick = nav.openActivity) { Text("Home search") }
        }

        @Composable override fun Activity() = Text("Activity screen")

        @Composable override fun Trackers(onOpen: (String) -> Unit) = Column {
            Button(onClick = { onOpen("electricity") }) { Text("Trackers row") }
            // Two taps before the first has drawn anything (a quick double tap).
            Button(onClick = { onOpen("electricity"); onOpen("electricity") }) { Text("Trackers row twice") }
        }

        @Composable override fun Tracker(onBack: () -> Unit, onSeeAll: () -> Unit) = Column {
            Text("Tracker detail")
            Button(onClick = onSeeAll) { Text("See all") }
        }

        @Composable override fun You() = Text("You screen")
    }

    private fun show() = compose.setContent { LedgaTheme(Appearance.LIGHT, reducedMotion = true) { LedgaNavHost(onboarded = true, screens = StandIns) } }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun `Back from See all returns to the tracker opened on the Trackers tab`() {
        show()
        tap("Trackers")
        tap("Trackers row")
        tap("See all")
        compose.onNodeWithText("Activity screen").assertIsDisplayed()
        back()
        compose.onNodeWithText("Tracker detail").assertIsDisplayed()
    }

    @Test
    fun `Back from See all returns to the tracker opened from Home`() {
        show()
        tap("Home tile")
        tap("See all")
        back()
        compose.onNodeWithText("Tracker detail").assertIsDisplayed()
    }

    @Test
    fun `a tab tapped after See all ends the way back, and Back goes Home as usual`() {
        show()
        tap("Trackers")
        tap("Trackers row")
        tap("See all")
        tap("Home")
        tap("Activity")
        back()
        compose.onNodeWithText("Home screen").assertIsDisplayed()
    }

    @Test
    fun `a double tap on a tracker opens it once`() {
        show()
        tap("Trackers")
        tap("Trackers row twice")
        back()
        compose.onNodeWithText("Trackers row").assertIsDisplayed()
    }
}
