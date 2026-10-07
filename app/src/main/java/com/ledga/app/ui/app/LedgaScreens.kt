package com.ledga.app.ui.app

import androidx.compose.runtime.Composable
import com.ledga.app.ui.activity.ActivityTab
import com.ledga.app.ui.alerts.AlertsScreen
import com.ledga.app.ui.home.HomeNav
import com.ledga.app.ui.home.HomeRoute as HomeScreenRoute
import com.ledga.app.ui.onboarding.OnboardingRoute as OnboardingScreenRoute
import com.ledga.app.ui.categories.CategoriesTab
import com.ledga.app.ui.categories.CategoryPageScreen
import com.ledga.app.ui.you.AppearanceScreen
import com.ledga.app.ui.you.HistoryCheckScreen
import com.ledga.app.ui.you.LicenceScreen
import com.ledga.app.ui.you.LicencesContent
import com.ledga.app.ui.you.LinesScreen
import com.ledga.app.ui.you.NotificationsScreen
import com.ledga.app.ui.you.UnreadableScreen
import com.ledga.app.ui.you.YouNav
import com.ledga.app.ui.you.YouScreen

/**
 * What each route shows. The app passes [AppScreens]; navigation tests pass stand-ins, so what they test is the wiring
 * between routes: tabs, pushes, and where Back goes.
 */
interface LedgaScreens {
    @Composable fun Onboarding(onDone: () -> Unit)

    @Composable fun Home(nav: HomeNav)

    @Composable fun Activity(openCategory: (String, String?) -> Unit)

    @Composable fun You(nav: YouNav)

    @Composable fun Lines(onBack: () -> Unit)

    @Composable fun Categories(onOpen: (String) -> Unit)

    @Composable fun Category(onBack: () -> Unit, onSeeAll: () -> Unit)

    @Composable fun NotificationSettings(onBack: () -> Unit)

    @Composable fun AppearanceSettings(onBack: () -> Unit)

    @Composable fun Unreadable(onBack: () -> Unit)

    @Composable fun HistoryCheck(onBack: () -> Unit, onOpenCategory: (String) -> Unit)

    @Composable fun Licences(onBack: () -> Unit, onOpen: (String) -> Unit)

    @Composable fun Licence(asset: String, onBack: () -> Unit)

    @Composable fun Alerts(onBack: () -> Unit, onOpenCategory: (String) -> Unit)
}

/** The real screens, each with its Hilt ViewModel. */
object AppScreens : LedgaScreens {
    @Composable override fun Onboarding(onDone: () -> Unit) = OnboardingScreenRoute(onDone = onDone)

    @Composable override fun Home(nav: HomeNav) = HomeScreenRoute(nav)

    @Composable override fun Activity(openCategory: (String, String?) -> Unit) = ActivityTab(openCategory = openCategory)

    @Composable override fun You(nav: YouNav) = YouScreen(nav)

    @Composable override fun Lines(onBack: () -> Unit) = LinesScreen(onBack = onBack)

    @Composable override fun Categories(onOpen: (String) -> Unit) = CategoriesTab(onOpen = onOpen)

    @Composable override fun Category(onBack: () -> Unit, onSeeAll: () -> Unit) = CategoryPageScreen(onBack = onBack, onSeeAll = onSeeAll)

    @Composable override fun NotificationSettings(onBack: () -> Unit) = NotificationsScreen(onBack = onBack)

    @Composable override fun AppearanceSettings(onBack: () -> Unit) = AppearanceScreen(onBack = onBack)

    @Composable override fun Unreadable(onBack: () -> Unit) = UnreadableScreen(onBack = onBack)

    @Composable override fun HistoryCheck(onBack: () -> Unit, onOpenCategory: (String) -> Unit) = HistoryCheckScreen(onBack = onBack, onOpenCategory = onOpenCategory)

    @Composable override fun Licences(onBack: () -> Unit, onOpen: (String) -> Unit) = LicencesContent(onBack = onBack, onOpen = onOpen)

    @Composable override fun Licence(asset: String, onBack: () -> Unit) = LicenceScreen(asset = asset, onBack = onBack)

    @Composable override fun Alerts(onBack: () -> Unit, onOpenCategory: (String) -> Unit) = AlertsScreen(onBack = onBack, onOpenCategory = onOpenCategory)
}
