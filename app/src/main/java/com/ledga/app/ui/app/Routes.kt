package com.ledga.app.ui.app

import kotlinx.serialization.Serializable

/** Type-safe navigation routes (spec §10.4). Detail routes arrive with their screens in 4b–4d. */
@Serializable data object HomeRoute

@Serializable data object ActivityRoute


@Serializable data object YouRoute

@Serializable data object OnboardingRoute

/** Alerts (R71): pushed over the screen it was opened from, no bottom bar. [openCode]: a notification's payment (R100). */
@Serializable data class AlertsRoute(val openCode: String? = null)

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

/** Not on a line (R129). */
@Serializable data object UnassignedRoute

@Serializable data object LicencesRoute

/** You → Export & restore (R124, R125). */
@Serializable data object BackupRoute

/** You → About → Updates (spec §13.4). */
@Serializable data object UpdatesRoute

/** You → About → Version history (R142). */
@Serializable data object VersionHistoryRoute

/** One licence's text; [asset] is its path under `assets/`. */
@Serializable data class LicenceRoute(val asset: String)
