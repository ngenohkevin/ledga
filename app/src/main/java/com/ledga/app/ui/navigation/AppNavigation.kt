package com.ledga.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ledga.app.ui.home.HomeScreen
import com.ledga.app.ui.onboarding.OnboardingScreen
import com.ledga.app.ui.settings.BudgetScreen
import com.ledga.app.ui.settings.SettingsScreen
import com.ledga.app.ui.settings.UnparsedSmsScreen
import com.ledga.app.ui.transactions.TransactionsScreen
import com.ledga.app.ui.trends.TrendsScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: Any,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(HomeRoute) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                    }
                }
            )
        }
        composable<HomeRoute> {
            HomeScreen()
        }
        composable<TransactionsRoute> {
            TransactionsScreen()
        }
        composable<TrendsRoute> {
            TrendsScreen()
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onNavigateToUnparsed = { navController.navigate(UnparsedSmsRoute) },
                onNavigateToBudgets = { navController.navigate(BudgetRoute) }
            )
        }
        composable<UnparsedSmsRoute> {
            UnparsedSmsScreen(onBack = { navController.popBackStack() })
        }
        composable<BudgetRoute> {
            BudgetScreen(onBack = { navController.popBackStack() })
        }
    }
}
