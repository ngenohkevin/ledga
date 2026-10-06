package com.ledga.app.ui.app

import androidx.compose.runtime.Composable
import com.ledga.app.ui.activity.ActivityTab
import com.ledga.app.ui.alerts.AlertsScreen
import com.ledga.app.ui.home.HomeNav
import com.ledga.app.ui.trackers.TrackerDetailRoute
import com.ledga.app.ui.trackers.TrackersTab
import com.ledga.app.ui.home.HomeRoute as HomeScreenRoute
import com.ledga.app.ui.onboarding.OnboardingRoute as OnboardingScreenRoute

/**
 * What each route shows. The app passes [AppScreens]; navigation tests pass stand-ins, so what they test is the wiring
 * between routes: tabs, pushes, and where Back goes.
 */
interface LedgaScreens {
    @Composable fun Onboarding(onDone: () -> Unit)

    @Composable fun Home(nav: HomeNav)

    @Composable fun Activity()

    @Composable fun Trackers(onOpen: (String) -> Unit)

    @Composable fun Tracker(onBack: () -> Unit, onSeeAll: () -> Unit)

    @Composable fun You()

    @Composable fun Alerts(onBack: () -> Unit)
}

/** The real screens, each with its Hilt ViewModel. */
object AppScreens : LedgaScreens {
    @Composable override fun Onboarding(onDone: () -> Unit) = OnboardingScreenRoute(onDone = onDone)

    @Composable override fun Home(nav: HomeNav) = HomeScreenRoute(nav)

    @Composable override fun Activity() = ActivityTab()

    @Composable override fun Trackers(onOpen: (String) -> Unit) = TrackersTab(onOpen = onOpen)

    @Composable override fun Tracker(onBack: () -> Unit, onSeeAll: () -> Unit) = TrackerDetailRoute(onBack = onBack, onSeeAll = onSeeAll)

    @Composable override fun You() = ComingNext(Tab.YOU)

    @Composable override fun Alerts(onBack: () -> Unit) = AlertsScreen(onBack = onBack)
}
