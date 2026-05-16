package com.ledga.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ledga.app.ui.components.v2.PillTab
import com.ledga.app.ui.components.v2.PillTabBar

private val TabHome = PillTab("home", "Home", Icons.Filled.Home)
private val TabActivity = PillTab("activity", "Activity", Icons.Filled.ShowChart)
private val TabInsights = PillTab("insights", "Insights", Icons.Filled.Insights)
private val TabYou = PillTab("you", "You", Icons.Filled.Person)

private val Tabs = listOf(TabHome, TabActivity, TabInsights, TabYou)

@Composable
fun LedgaBottomNavBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val selectedKey = when {
        currentDestination?.hasRoute(HomeRoute::class) == true -> TabHome.key
        currentDestination?.hasRoute(ActivityRoute::class) == true -> TabActivity.key
        currentDestination?.hasRoute(InsightsRoute::class) == true -> TabInsights.key
        currentDestination?.hasRoute(YouRoute::class) == true -> TabYou.key
        else -> TabHome.key
    }

    PillTabBar(
        tabs = Tabs,
        selectedKey = selectedKey,
        onSelect = { key ->
            val route: Any = when (key) {
                TabHome.key -> HomeRoute
                TabActivity.key -> ActivityRoute
                TabInsights.key -> InsightsRoute
                TabYou.key -> YouRoute
                else -> return@PillTabBar
            }
            navController.navigate(route) {
                popUpTo(HomeRoute) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
    )
}
