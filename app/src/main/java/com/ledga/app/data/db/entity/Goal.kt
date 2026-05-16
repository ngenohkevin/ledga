package com.ledga.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Goals — LEDGA_REDESIGN.md §4.6.
 *
 * ContributionRule is persisted as two flat columns so Room can index
 * and query it cheaply. See [ContributionRule.encode]/[decode] in the
 * repository for the mapping.
 *
 * Rule encodings (column = `contributionRule`):
 *   ALL_SAVINGS_DEPOSITS                -> matches every M-Shwari / KCB deposit
 *   TO_RECIPIENT:<name fragment>        -> matches outflows whose recipientName
 *                                          contains the fragment (case-insensitive)
 *   MANUAL                              -> only counts transactions the user
 *                                          explicitly marked as contributions
 */
@Entity(
    tableName = "goals",
    indices = [Index(value = ["completedAt"])]
)
data class Goal(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val targetAmount: Double,
    /** Epoch millis; null = no ETA, only progress matters. */
    val targetDate: Long?,
    /** Encoded ContributionRule. See class header for grammar. */
    val contributionRule: String,
    val colorHex: String,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
)

/**
 * Join row for transactions a user manually marked as contributing to a
 * specific goal. Needed because rule-matched contributions are computed,
 * but a user override has to be persisted.
 */
@Entity(
    tableName = "goal_contributions",
    primaryKeys = ["goalId", "transactionId"],
    indices = [
        Index(value = ["goalId"]),
        Index(value = ["transactionId"]),
    ]
)
data class GoalContribution(
    val goalId: Long,
    val transactionId: Long,
    val markedAt: Long = System.currentTimeMillis(),
)

sealed class ContributionRule {
    object AllSavingsDeposits : ContributionRule()
    data class ToRecipient(val recipientName: String) : ContributionRule()
    object Manual : ContributionRule()

    fun encode(): String = when (this) {
        AllSavingsDeposits -> ENC_ALL_SAVINGS
        is ToRecipient -> "$ENC_TO_RECIPIENT:$recipientName"
        Manual -> ENC_MANUAL
    }

    companion object {
        private const val ENC_ALL_SAVINGS = "ALL_SAVINGS_DEPOSITS"
        private const val ENC_TO_RECIPIENT = "TO_RECIPIENT"
        private const val ENC_MANUAL = "MANUAL"

        fun decode(encoded: String): ContributionRule = when {
            encoded == ENC_ALL_SAVINGS -> AllSavingsDeposits
            encoded == ENC_MANUAL -> Manual
            encoded.startsWith("$ENC_TO_RECIPIENT:") ->
                ToRecipient(encoded.removePrefix("$ENC_TO_RECIPIENT:"))
            // Unknown rule strings degrade gracefully to Manual so a stale row
            // never crashes the app.
            else -> Manual
        }
    }
}
