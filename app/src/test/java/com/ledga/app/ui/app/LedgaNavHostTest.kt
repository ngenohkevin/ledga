package com.ledga.app.ui.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.data.settings.TextSize
import com.ledga.app.ui.activity.ActivityLink
import com.ledga.app.ui.activity.ActivityLinks
import com.ledga.app.ui.activity.TakeLinks
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.home.HomeNav
import com.ledga.app.ui.you.YouNav
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The app's routes with stand-in screens: tabs, pushes, and where Back goes. */
@RunWith(RobolectricTestRunner::class)
class LedgaNavHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private object StandIns : LedgaScreens {
        /** The app's one hand-off to Activity (R61); a fresh one per test. */
        var links = ActivityLinks()

        @Composable override fun Onboarding(onDone: () -> Unit) = Text("Onboarding screen")

        @Composable override fun Home(nav: HomeNav) = Column {
            Text("Home screen")
            Button(onClick = { nav.openCategory("electricity") }) { Text("Home tile") }
            Button(onClick = { nav.openCategory("fuel") }) { Text("Home view category") }
            Button(onClick = nav.openActivity) { Text("Home search") }
            Button(onClick = nav.openAlerts) { Text("Home bell") }
        }

        @Composable override fun Activity(openCategory: (String, String?) -> Unit) = Column {
            val probe = viewModel { LinkProbe(links) }
            TakeLinks(links.requests, probe::take)
            val showing by probe.showing.collectAsState()
            Text("Activity screen")
            Text("Activity showing $showing")
            Button(onClick = { openCategory("groceries", "2026-09") }) { Text("Spending row") }
        }

        @Composable override fun Categories(onOpen: (String) -> Unit) = Column {
            Button(onClick = { onOpen("electricity") }) { Text("Categories row") }
            // Two taps before the first has drawn anything (a quick double tap).
            Button(onClick = { onOpen("electricity"); onOpen("electricity") }) { Text("Categories row twice") }
        }


        @Composable override fun You(nav: YouNav) = Column {
            Text("You screen")
            Button(onClick = nav.openLines) { Text("You lines") }
            Button(onClick = nav.openPeople) { Text("You people") }
            Button(onClick = nav.openNotifications) { Text("You notifications") }
            Button(onClick = nav.openAppearance) { Text("You appearance") }
            Button(onClick = nav.openUnreadable) { Text("You unreadable") }
            Button(onClick = nav.openHistoryCheck) { Text("You history") }
            Button(onClick = nav.openLicences) { Text("You licences") }
        }

        @Composable override fun Lines(onBack: () -> Unit) = Text("Lines screen")


        @Composable override fun Category(onBack: () -> Unit, onSeeAll: () -> Unit) = Column {
            Text("Category page")
            Button(onClick = {
                links.open(ActivityLink.Transactions(TransactionFilter(categoryKeys = setOf("electricity"))))
                onSeeAll()
            }) { Text("See all") }
        }

        @Composable override fun NotificationSettings(onBack: () -> Unit) = Text("Notifications screen")

        @Composable override fun AppearanceSettings(onBack: () -> Unit) = Text("Appearance screen")

        @Composable override fun Unreadable(onBack: () -> Unit) = Text("Unreadable screen")

        @Composable override fun HistoryCheck(onBack: () -> Unit, onOpenCategory: (String) -> Unit) = Column {
            Text("History check screen")
            Button(onClick = { onOpenCategory("fuel") }) { Text("History view category") }
        }

        @Composable override fun Licences(onBack: () -> Unit, onOpen: (String) -> Unit) = Column {
            Text("Licences screen")
            Button(onClick = { onOpen("licenses/inter-OFL.txt") }) { Text("Licences row") }
        }

        @Composable override fun Licence(asset: String, onBack: () -> Unit) = Text("Licence $asset")

        @Composable override fun Alerts(onBack: () -> Unit, onOpenCategory: (String) -> Unit, openCode: String?) = Column {
            Text("Alerts screen")
            Text("Alerts opens ${openCode ?: "nothing"}")
            Button(onClick = onBack) { Text("Alerts back") }
        }
    }

    /** Stands in for ActivityViewModel's side of R61: it takes each request once, the way the real one does. */
    /**
     * Stands in for ActivityViewModel's side of R61/R93: it applies a hand-off only when the screen hands it one through
     * [TakeLinks], the way ActivityTab does — never by itself (final review I2).
     */
    class LinkProbe(private val links: ActivityLinks) : ViewModel() {
        val showing = MutableStateFlow("nothing")

        fun take(link: ActivityLink) {
            showing.value = when (link) {
                is ActivityLink.Transactions -> "transactions ${link.filter.categoryKeys.joinToString()}"
                ActivityLink.Spending -> "spending"
                ActivityLink.People -> "people"
            }
            links.taken(link)
        }
    }

    private val textSize = mutableStateOf(TextSize.SYSTEM)

    @Before
    fun freshLinks() {
        StandIns.links = ActivityLinks()
    }

    private val opens = MutableStateFlow<OpenDestination?>(null)
    private val opened = mutableListOf<OpenDestination>()

    private fun show(onboarded: Boolean = true) = compose.setContent {
        LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
            WithTextSize(textSize.value) {
                LedgaNavHost(onboarded = onboarded, screens = StandIns, opens = opens, onOpened = { opened += it; opens.value = null })
            }
        }
    }

    private fun notificationOpens(destination: OpenDestination) {
        compose.runOnIdle { opens.value = destination }
        compose.waitForIdle()
    }

    private fun setTextSize(size: TextSize) {
        compose.runOnUiThread { textSize.value = size }
        compose.waitForIdle()
    }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun `Back from See all returns to the page opened on the Categories tab`() {
        show()
        tap("Categories")
        tap("Categories row")
        tap("See all")
        compose.onNodeWithText("Activity screen").assertIsDisplayed()
        back()
        compose.onNodeWithText("Category page").assertIsDisplayed()
    }

    @Test
    fun `Back from See all returns to the page opened from Home`() {
        show()
        tap("Home tile")
        tap("See all")
        back()
        compose.onNodeWithText("Category page").assertIsDisplayed()
    }

    @Test
    fun `a tab tapped after See all ends the way back, and Back goes Home as usual`() {
        show()
        tap("Categories")
        tap("Categories row")
        tap("See all")
        tap("Home")
        tap("Activity")
        back()
        compose.onNodeWithText("Home screen").assertIsDisplayed()
    }

    @Test
    fun `a double tap on a tracker opens it once`() {
        show()
        tap("Categories")
        tap("Categories row twice")
        back()
        compose.onNodeWithText("Categories row").assertIsDisplayed()
    }

    @Test
    fun `the bell pushes Alerts over Home without the bottom bar, and Back returns`() {
        show()
        tap("Home bell")
        compose.onNodeWithText("Alerts screen").assertIsDisplayed()
        compose.onNodeWithText("Categories").assertDoesNotExist()
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
            "You lines" to "Lines screen", "You notifications" to "Notifications screen",
            "You appearance" to "Appearance screen", "You unreadable" to "Unreadable screen", "You history" to "History check screen",
            "You licences" to "Licences screen",
        ).forEach { (row, screen) ->
            tap(row)
            compose.onNodeWithText(screen).assertIsDisplayed()
            compose.onNodeWithText("Categories").assertDoesNotExist()
            back()
            compose.onNodeWithText("You screen").assertIsDisplayed()
        }
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

    @Test
    fun `a text size change keeps the screen you are on (S26)`() {
        show()
        tap("You")
        tap("You lines")
        setTextSize(TextSize.LARGE)
        compose.onNodeWithText("Lines screen").assertIsDisplayed()
        setTextSize(TextSize.SYSTEM)
        compose.onNodeWithText("Lines screen").assertIsDisplayed()
    }

    @Test
    fun `after a text size change, a tracker's See all reaches the Activity on screen (S26)`() {
        show()
        tap("Activity")
        setTextSize(TextSize.LARGE)
        tap("Home")
        tap("Home tile")
        tap("See all")
        compose.onNodeWithText("Activity showing transactions electricity").assertIsDisplayed()
    }

    @Test
    fun `a Spending row opens the page, and Back returns to Spending (D3)`() {
        show()
        tap("Activity")
        tap("Spending row")
        compose.onNodeWithText("Category page").assertIsDisplayed()
        compose.onNodeWithText("Categories").assertDoesNotExist() // no bottom bar on a pushed screen
        back()
        compose.onNodeWithText("Activity screen").assertIsDisplayed()
    }

    @Test
    fun `See all from a page opened in Activity returns to Activity's root (R90)`() {
        show()
        tap("Activity")
        tap("Spending row")
        tap("See all")
        compose.onNodeWithText("Activity showing transactions electricity").assertIsDisplayed()
        back()
        compose.onNodeWithText("Home screen").assertIsDisplayed()
    }

    @Test
    fun `a payment's View opens its category over the tab it was on`() {
        show()
        tap("Home view category")
        compose.onNodeWithText("Category page").assertIsDisplayed()
        back()
        compose.onNodeWithText("Home screen").assertIsDisplayed()
        tap("You")
        tap("You history")
        tap("History view category")
        compose.onNodeWithText("Category page").assertIsDisplayed()
        back()
        compose.onNodeWithText("History check screen").assertIsDisplayed()
    }

    @Test
    fun `a notification tapped before Ledga was open lands on its payment, and Back returns Home (R100)`() {
        opens.value = OpenDestination.Alerts("TJK4AB12FA")
        show()
        compose.onNodeWithText("Alerts opens TJK4AB12FA").assertIsDisplayed()
        assertEquals(listOf<OpenDestination>(OpenDestination.Alerts("TJK4AB12FA")), opened)
        back()
        compose.onNodeWithText("Home screen").assertIsDisplayed()
    }

    @Test
    fun `a second payment's notification replaces the Alerts on top, and Back returns where you were (R100)`() {
        show()
        tap("You")
        notificationOpens(OpenDestination.Alerts("TJK4AB12FA"))
        compose.onNodeWithText("Alerts opens TJK4AB12FA").assertIsDisplayed()
        notificationOpens(OpenDestination.Alerts("TJK4AB12FB"))
        compose.onNodeWithText("Alerts opens TJK4AB12FB").assertIsDisplayed()
        back()
        compose.onNodeWithText("You screen").assertIsDisplayed()
    }

    @Test
    fun `a Fuliza reminder opens Home from any tab (R100)`() {
        show()
        tap("You")
        notificationOpens(OpenDestination.Home)
        compose.onNodeWithText("Home screen").assertIsDisplayed()
    }

    @Test
    fun `a summary opens Activity on the payments it left, even from a pushed screen (R100)`() {
        show()
        tap("You")
        tap("You lines")
        StandIns.links.open(ActivityLink.Transactions(TransactionFilter(categoryKeys = setOf("groceries"))))
        notificationOpens(OpenDestination.Activity)
        compose.onNodeWithText("Activity showing transactions groceries").assertIsDisplayed()
    }

    @Test
    fun `a notification tapped during onboarding opens nothing, then or later (R100)`() {
        show(onboarded = false)
        notificationOpens(OpenDestination.Alerts("TJK4AB12FA"))
        compose.onNodeWithText("Onboarding screen").assertIsDisplayed()
        assertEquals(listOf<OpenDestination>(OpenDestination.Alerts("TJK4AB12FA")), opened, "taken, so it never opens later")
    }
}
