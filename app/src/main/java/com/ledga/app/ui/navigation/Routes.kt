package com.ledga.app.ui.navigation

import kotlinx.serialization.Serializable

// ---- Tab roots (4-tab IA per LEDGA_REDESIGN.md §3) ----
@Serializable object HomeRoute
@Serializable object ActivityRoute
@Serializable object InsightsRoute
@Serializable object YouRoute

// ---- Detail / pushed screens ----
@Serializable object OnboardingRoute
@Serializable object SettingsRoute     // legacy entry — YouScreen replaces, kept for back-compat
@Serializable object UnparsedSmsRoute
@Serializable object BudgetRoute
@Serializable object GoalsRoute
@Serializable data class GoalDetailRoute(val goalId: Long)
@Serializable object AccountsRoute
@Serializable object AccountBackfillRoute
@Serializable object UpdateRoute

// ---- Legacy aliases kept until Phase B sweep is complete ----
// Activity bar replaces these; AppNavigation will route them inside Activity.
@Serializable object TransactionsRoute
@Serializable object TrendsRoute
