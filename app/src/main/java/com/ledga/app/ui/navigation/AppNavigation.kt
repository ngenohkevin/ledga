package com.ledga.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ledga.app.ui.accounts.AccountsScreen
import com.ledga.app.ui.accounts.BackfillScreen
import com.ledga.app.ui.car.CarExpensesScreen
import com.ledga.app.ui.update.UpdateScreen
import com.ledga.app.ui.activity.ActivityScreen
import com.ledga.app.ui.goals.GoalDetailScreen
import com.ledga.app.ui.goals.GoalsScreen
import com.ledga.app.ui.home.HomeScreen
import com.ledga.app.ui.insights.InsightsScreen
import com.ledga.app.ui.onboarding.OnboardingScreen
import com.ledga.app.ui.settings.BudgetScreen
import com.ledga.app.ui.settings.UnparsedSmsScreen
import com.ledga.app.ui.you.YouScreen

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

        // ---- Tab roots ----
        composable<HomeRoute> {
            HomeScreen(
                onNavigateToActivity = { navController.navigate(ActivityRoute) },
                onNavigateToInsights = { navController.navigate(InsightsRoute) },
                onNavigateToYou = { navController.navigate(YouRoute) },
                onManageAccounts = { navController.navigate(AccountsRoute) },
                onOpenUpdate = { navController.navigate(UpdateRoute) },
            )
        }
        composable<ActivityRoute> {
            ActivityScreen()
        }
        composable<InsightsRoute> {
            InsightsScreen()
        }
        composable<YouRoute> {
            YouScreen(
                onNavigateToUnparsed = { navController.navigate(UnparsedSmsRoute) },
                onNavigateToBudgets = { navController.navigate(BudgetRoute) },
                onNavigateToGoals = { navController.navigate(GoalsRoute) },
                onNavigateToAccounts = { navController.navigate(AccountsRoute) },
                onNavigateToCar = { navController.navigate(CarExpensesRoute) },
                onNavigateToUpdate = { navController.navigate(UpdateRoute) },
            )
        }

        // ---- Pushed detail screens ----
        composable<UnparsedSmsRoute> {
            UnparsedSmsScreen(onBack = { navController.popBackStack() })
        }
        composable<BudgetRoute> {
            BudgetScreen(onBack = { navController.popBackStack() })
        }
        composable<GoalsRoute> {
            GoalsScreen(
                onBack = { navController.popBackStack() },
                onOpenGoal = { id -> navController.navigate(GoalDetailRoute(id)) },
            )
        }
        composable<GoalDetailRoute> {
            GoalDetailScreen(onBack = { navController.popBackStack() })
        }
        composable<AccountsRoute> {
            AccountsScreen(
                onBack = { navController.popBackStack() },
                onOpenBackfill = { navController.navigate(AccountBackfillRoute) },
            )
        }
        composable<AccountBackfillRoute> {
            BackfillScreen(onBack = { navController.popBackStack() })
        }
        composable<CarExpensesRoute> {
            CarExpensesScreen(onBack = { navController.popBackStack() })
        }
        composable<UpdateRoute> {
            UpdateScreen(onBack = { navController.popBackStack() })
        }
    }
}
