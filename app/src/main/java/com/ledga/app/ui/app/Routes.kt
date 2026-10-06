package com.ledga.app.ui.app

import kotlinx.serialization.Serializable

/** Type-safe navigation routes (spec §10.4). Detail routes arrive with their screens in 4b–4d. */
@Serializable data object HomeRoute

@Serializable data object ActivityRoute

@Serializable data object TrackersRoute

@Serializable data object YouRoute

@Serializable data object OnboardingRoute

/** Alerts (R71): pushed over Home, no bottom bar. */
@Serializable data object AlertsRoute

/** You → M-Pesa lines (R65). */
@Serializable data object LinesRoute

/** Categories & rules (R67). */
@Serializable data object CategoriesRoute

/** A category's screen (R67). The ViewModel reads [categoryKey] by that name. */
@Serializable data class CategoryRoute(val categoryKey: String)

@Serializable data object NotificationsRoute

@Serializable data object AppearanceRoute

/** Messages Ledga couldn't read (R78). */
@Serializable data object UnreadableRoute

@Serializable data object HistoryCheckRoute

@Serializable data object LicencesRoute

/** One licence's text; [asset] is its path under `assets/`. */
@Serializable data class LicenceRoute(val asset: String)

/** Tracker detail (spec §10.4): pushed full screen, no bottom bar. The ViewModel reads [categoryKey] by that name. */
@Serializable data class TrackerRoute(val categoryKey: String)
