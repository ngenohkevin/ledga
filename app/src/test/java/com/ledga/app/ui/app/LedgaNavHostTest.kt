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
import com.ledga.app.ui.you.YouNav
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
            Button(onClick = nav.openAlerts) { Text("Home bell") }
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

        @Composable override fun You(nav: YouNav) = Column {
            Text("You screen")
            Button(onClick = nav.openLines) { Text("You lines") }
            Button(onClick = nav.openCategories) { Text("You categories") }
            Button(onClick = nav.openPeople) { Text("You people") }
            Button(onClick = nav.openNotifications) { Text("You notifications") }
            Button(onClick = nav.openAppearance) { Text("You appearance") }
            Button(onClick = nav.openUnreadable) { Text("You unreadable") }
            Button(onClick = nav.openHistoryCheck) { Text("You history") }
            Button(onClick = nav.openLicences) { Text("You licences") }
        }

        @Composable override fun Lines(onBack: () -> Unit) = Text("Lines screen")

        @Composable override fun Categories(onBack: () -> Unit, onOpen: (String) -> Unit) = Column {
            Text("Categories screen")
            Button(onClick = { onOpen("groceries") }) { Text("Categories row") }
        }

        @Composable override fun Category(onBack: () -> Unit, onSeePayments: () -> Unit) = Column {
            Text("Category screen")
            Button(onClick = onSeePayments) { Text("See payments") }
        }

        @Composable override fun NotificationSettings(onBack: () -> Unit) = Text("Notifications screen")

        @Composable override fun AppearanceSettings(onBack: () -> Unit) = Text("Appearance screen")

        @Composable override fun Unreadable(onBack: () -> Unit) = Text("Unreadable screen")

        @Composable override fun HistoryCheck(onBack: () -> Unit) = Text("History check screen")

        @Composable override fun Licences(onBack: () -> Unit, onOpen: (String) -> Unit) = Column {
            Text("Licences screen")
            Button(onClick = { onOpen("licenses/inter-OFL.txt") }) { Text("Licences row") }
        }

        @Composable override fun Licence(asset: String, onBack: () -> Unit) = Text("Licence $asset")

        @Composable override fun Alerts(onBack: () -> Unit) = Column {
            Text("Alerts screen")
            Button(onClick = onBack) { Text("Alerts back") }
        }
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

    @Test
    fun `the bell pushes Alerts over Home without the bottom bar, and Back returns`() {
        show()
        tap("Home bell")
        compose.onNodeWithText("Alerts screen").assertIsDisplayed()
        compose.onNodeWithText("Trackers").assertDoesNotExist()
        back()
        compose.onNodeWithText("Home screen").assertIsDisplayed()
        tap("Home bell")
        tap("Alerts back")
        compose.onNodeWithText("Home screen").assertIsDisplayed()
    }

    @Test
    fun `You's rows push their screens without the bottom bar, and Back returns to You (R83)`() {
        show()
        tap("You")
        listOf(
            "You lines" to "Lines screen", "You categories" to "Categories screen", "You notifications" to "Notifications screen",
            "You appearance" to "Appearance screen", "You unreadable" to "Unreadable screen", "You history" to "History check screen",
            "You licences" to "Licences screen",
        ).forEach { (row, screen) ->
            tap(row)
            compose.onNodeWithText(screen).assertIsDisplayed()
            compose.onNodeWithText("Trackers").assertDoesNotExist()
            back()
            compose.onNodeWithText("You screen").assertIsDisplayed()
        }
    }

    @Test
    fun `a category's See payments opens Activity, and Back returns to the category (R83)`() {
        show()
        tap("You")
        tap("You categories")
        tap("Categories row")
        tap("See payments")
        compose.onNodeWithText("Activity screen").assertIsDisplayed()
        back()
        compose.onNodeWithText("Category screen").assertIsDisplayed()
        back()
        compose.onNodeWithText("Categories screen").assertIsDisplayed()
    }

    @Test
    fun `People from You opens Activity, and Back returns to You (R82)`() {
        show()
        tap("You")
        tap("You people")
        compose.onNodeWithText("Activity screen").assertIsDisplayed()
        back()
        compose.onNodeWithText("You screen").assertIsDisplayed()
    }

    @Test
    fun `a licence opens over the list`() {
        show()
        tap("You")
        tap("You licences")
        tap("Licences row")
        compose.onNodeWithText("Licence licenses/inter-OFL.txt").assertIsDisplayed()
        back()
        compose.onNodeWithText("Licences screen").assertIsDisplayed()
    }
}
