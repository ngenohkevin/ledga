package com.ledga.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single derived insight surfaced to the user. See LEDGA_REDESIGN.md §4.5.
 *
 * **Natural-key dedup**: every rule produces a stable [naturalKey] for the
 * insight it would emit (e.g. `anomaly:2:2026-W11`). Re-running the rule
 * engine upserts on that key, so the same insight doesn't pile up day after
 * day, and the user's dismiss/snooze state survives regeneration.
 */
@Entity(
    tableName = "insights",
    indices = [
        Index(value = ["naturalKey"], unique = true),
        Index(value = ["generatedAt"]),
        Index(value = ["dismissedAt"]),
        Index(value = ["snoozedUntil"]),
    ]
)
data class Insight(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val naturalKey: String,
    val type: InsightType,
    val severity: InsightSeverity,
    /** Short uppercase chip label, e.g. "WATCH OUT", "RECURRING DETECTED". */
    val typeLabel: String,
    /** The big "the thing to know" — one sentence. */
    val headline: String,
    /** Optional supporting detail line shown under the headline. */
    val body: String?,
    /** Optional action button label, e.g. "See transactions". */
    val ctaLabel: String? = null,
    /** Free-form args the screen uses to wire navigation, e.g. "category=2". */
    val ctaArgs: String? = null,
    val generatedAt: Long,
    /** Set to a future epoch ms to hide until that time. */
    val snoozedUntil: Long? = null,
    /** Set when the user has dismissed the card. */
    val dismissedAt: Long? = null,
)

/**
 * Visual treatment driver — maps to background tint + icon tint on the card.
 */
enum class InsightSeverity {
    /** Neutral / positive nudges. Accent-soft background. */
    NUDGE,
    /** General information. Surface background. */
    INFO,
    /** Watch-out / unusual activity. Warning-soft background. */
    WARN,
    /** Outstanding Fuliza, mistakes worth acting on. Danger-soft background. */
    ALERT,
    ;

    /** Sort priority — higher first. */
    val priority: Int
        get() = when (this) {
            ALERT -> 3
            WARN -> 2
            INFO -> 1
            NUDGE -> 0
        }
}

enum class InsightType {
    ANOMALY,
    RECURRING,
    FEE_TIP,
    FULIZA,
    /**
     * Fires after an auto-deduction event — distinct from [FULIZA] (which
     * warns about an *outstanding* balance) so the UI can treat them
     * differently (icon, severity).
     */
    FULIZA_AUTO_PAY,
    POSITIVE_NUDGE,
}
