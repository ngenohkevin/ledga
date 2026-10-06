package com.ledga.app.ui.app

import kotlinx.serialization.Serializable

/** Type-safe navigation routes (spec §10.4). Detail routes arrive with their screens in 4b–4d. */
@Serializable data object HomeRoute

@Serializable data object ActivityRoute

@Serializable data object TrackersRoute

@Serializable data object YouRoute

@Serializable data object OnboardingRoute

/** Tracker detail (spec §10.4): pushed full screen, no bottom bar. The ViewModel reads [categoryKey] by that name. */
@Serializable data class TrackerRoute(val categoryKey: String)
