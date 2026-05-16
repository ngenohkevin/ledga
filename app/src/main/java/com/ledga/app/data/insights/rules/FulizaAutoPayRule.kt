package com.ledga.app.data.insights.rules

import com.ledga.app.data.db.entity.InsightSeverity
import com.ledga.app.data.db.entity.InsightType
import com.ledga.app.data.db.entity.TransactionEntity
import com.ledga.app.data.insights.InsightCandidate
import com.ledga.app.data.insights.InsightRule
import com.ledga.app.data.insights.RuleContext
import com.ledga.app.data.parser.TransactionType
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Tells the user that money they received was auto-deducted to repay
 * Fuliza — connecting the two SMS that arrive in quick succession.
 *
 * Without this, users see "Ksh 1,000 received" followed seconds later
 * by "Ksh 800 used to pay your Fuliza" and have to mentally connect the
 * dots. The insight makes the cause-and-effect explicit.
 *
 * **Natural key** is tied to the specific auto-pay transaction code so
 * each event surfaces exactly once and persists in history. The rule
 * looks at auto-pays within the last [WINDOW_DAYS] days so a stale row
 * doesn't keep generating "new" insights forever.
 */
class FulizaAutoPayRule @Inject constructor() : InsightRule {

    override fun evaluate(ctx: RuleContext): List<InsightCandidate> {
        val cutoff = ctx.now - WINDOW_DAYS * DAY_MS
        val recent = ctx.transactions
            .asSequence()
            .filter { it.type == TransactionType.FULIZA_AUTO_PAY }
            .filter { it.timestamp >= cutoff }
            .sortedByDescending { it.timestamp }
            .take(MAX_INSIGHTS)
            .toList()

        return recent.map { tx ->
            val isFullClear = tx.recipientName?.contains("cleared", ignoreCase = true) == true
            val nearbyReceived = findRecentReceived(ctx.transactions, tx)

            val headline = if (isFullClear) {
                "Ksh ${tx.amount.roundToInt()} cleared your Fuliza overdraft."
            } else {
                "Ksh ${tx.amount.roundToInt()} went toward your Fuliza overdraft."
            }

            val sourceCopy = nearbyReceived?.let {
                val from = it.recipientName?.takeIf { n -> n.isNotBlank() }
                    ?: it.recipientPhone
                    ?: "money you received"
                "Triggered by Ksh ${it.amount.roundToInt()} from $from. "
            } ?: ""

            val body = if (isFullClear) {
                "${sourceCopy}Money landed in your wallet and the overdraft auto-cleared."
            } else {
                "${sourceCopy}Money landed in your wallet and was auto-deducted. " +
                        "Some Fuliza is still outstanding."
            }

            InsightCandidate(
                naturalKey = "fuliza_auto_pay:${tx.transactionCode}",
                type = InsightType.FULIZA_AUTO_PAY,
                // Full clear is mildly positive (debt gone), partial is informational.
                severity = if (isFullClear) InsightSeverity.NUDGE else InsightSeverity.INFO,
                typeLabel = if (isFullClear) "FULIZA CLEARED" else "FULIZA AUTO-DEDUCT",
                headline = headline,
                body = body,
            )
        }
    }

    /**
     * The RECEIVED transaction that most plausibly triggered this auto-pay.
     * Heuristic: latest RECEIVED in the [PAIR_WINDOW_MIN] minutes before the
     * auto-pay. Returns null if no such transaction exists (common when the
     * trigger was a deposit or a later inflow).
     */
    private fun findRecentReceived(
        all: List<TransactionEntity>,
        autoPay: TransactionEntity,
    ): TransactionEntity? {
        val windowStart = autoPay.timestamp - PAIR_WINDOW_MIN * 60_000L
        return all.asSequence()
            .filter { it.type == TransactionType.RECEIVED }
            .filter { it.timestamp in windowStart..autoPay.timestamp }
            .maxByOrNull { it.timestamp }
    }

    companion object {
        /** Don't re-surface auto-pays older than this. */
        const val WINDOW_DAYS = 7L

        /** Defense against pathological histories: never emit more than this
         * many in a single rule pass. The engine upserts by natural key, so
         * caps just bound work per run, not total events captured. */
        const val MAX_INSIGHTS = 5

        /** How close in time a RECEIVED must be to count as the trigger. */
        const val PAIR_WINDOW_MIN = 15L

        private const val DAY_MS = 86_400_000L
    }
}
