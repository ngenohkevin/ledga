package com.ledga.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState

data class BottomNavItem<T : Any>(
    val label: String,
    val icon: ImageVector,
    val route: T
)

@Composable
fun LedgaBottomNavBar(navController: NavController) {
    val items = listOf(
        BottomNavItem("Home", Icons.Default.Home, HomeRoute),
        BottomNavItem("Transactions", Icons.Default.List, TransactionsRoute),
        BottomNavItem("Trends", Icons.Default.TrendingUp, TrendsRoute),
        BottomNavItem("Settings", Icons.Default.Settings, SettingsRoute),
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentDestination?.hasRoute(item.route::class) == true,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(HomeRoute) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}
