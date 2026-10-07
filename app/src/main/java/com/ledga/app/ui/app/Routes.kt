package com.ledga.app.ui.app

import kotlinx.serialization.Serializable

/** Type-safe navigation routes (spec §10.4). Detail routes arrive with their screens in 4b–4d. */
@Serializable data object HomeRoute

@Serializable data object ActivityRoute


@Serializable data object YouRoute

@Serializable data object OnboardingRoute

/** Alerts (R71): pushed over Home, no bottom bar. */
@Serializable data object AlertsRoute

/** You → M-Pesa lines (R65). */
@Serializable data object LinesRoute

/** The Categories tab (4e D2). */
@Serializable data object CategoriesRoute

/** A category's page (4e D1). [month] is "2026-09" when Spending opened it at that month (D3). The ViewModel reads both by name. */
@Serializable data class CategoryRoute(val categoryKey: String, val month: String? = null)

@Serializable data object NotificationsRoute

@Serializable data object AppearanceRoute

/** Messages Ledga couldn't read (R78). */
@Serializable data object UnreadableRoute

@Serializable data object HistoryCheckRoute

@Serializable data object LicencesRoute

/** One licence's text; [asset] is its path under `assets/`. */
@Serializable data class LicenceRoute(val asset: String)
