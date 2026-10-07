package com.ledga.app.data.settings

import com.ledga.app.ui.design.theme.Appearance

/** You → Appearance → Text size. [scale] null follows Android's own font size; the others are v1's five sizes (R30). */
enum class TextSize(val scale: Float?) {
    SYSTEM(null),
    SMALL(0.85f),
    MEDIUM(1.0f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f),
}

/**
 * Every v2 setting (spec §8 step 4, §11, refinement R30). The defaults are a fresh install's; an upgraded phone's values
 * arrive through [V1SettingsMigration]. 5a's workers read the notification fields; Phase 6 adds the update channel.
 */
data class Settings(
    val appearance: Appearance = Appearance.SYSTEM,
    val textSize: TextSize = TextSize.SYSTEM,
    /** The optional name Home greets (spec §18). */
    val displayName: String? = null,
    val onboarded: Boolean = false,
    /** The line Home and Activity show; null = all lines. */
    val selectedLineId: Long? = null,
    val notifyDaily: Boolean = true,
    /** Minutes after midnight, Nairobi time: 20:00 by default (spec §11). */
    val dailySummaryMinute: Int = 20 * 60,
    val notifyWeekly: Boolean = true,
    val notifyLarge: Boolean = false,
    val largeThresholdCents: Long = 500_000L,
    val notifyFuliza: Boolean = true,
    /** The newest inbox `date` (epoch ms) already scanned; 0 = never (spec §9.1). */
    val smsWatermarkMillis: Long = 0L,
    /** The migration's full rescan (spec §8 step 3) hasn't completed yet: the next start runs it instead of a catch-up. */
    val fullRescanOwed: Boolean = false,
    /** R59: the person said "Not now" to Home's notifications banner; You → Notifications (4d) keeps the explanation. */
    val notificationNudgeDismissed: Boolean = false,
)
