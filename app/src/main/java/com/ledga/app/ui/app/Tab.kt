package com.ledga.app.ui.app

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.ledga.app.ui.design.components.LedgaTabs
import com.ledga.app.ui.design.components.NavTab

/** The four tabs, in `LedgaTabs.all` order (spec §10.4). */
enum class Tab(val nav: NavTab, val route: Any) {
    HOME(LedgaTabs.Home, HomeRoute),
    ACTIVITY(LedgaTabs.Activity, ActivityRoute),
    CATEGORIES(LedgaTabs.Categories, CategoriesRoute),
    YOU(LedgaTabs.You, YouRoute),
    ;

    companion object {
        /** The tab a destination is the root of; null for detail screens and onboarding (no bottom bar there). */
        fun of(destination: NavDestination?): Tab? = when {
            destination == null -> null
            destination.hasRoute<HomeRoute>() -> HOME
            destination.hasRoute<ActivityRoute>() -> ACTIVITY
            destination.hasRoute<CategoriesRoute>() -> CATEGORIES
            destination.hasRoute<YouRoute>() -> YOU
            else -> null
        }
    }
}

/** Bottom navigation's switch: one copy of each tab, each tab's state kept, Home always at the root. */
fun NavController.openTab(tab: Tab) = navigate(tab.route) {
    popUpTo<HomeRoute> { saveState = true }
    launchSingleTop = true
    restoreState = true
}
