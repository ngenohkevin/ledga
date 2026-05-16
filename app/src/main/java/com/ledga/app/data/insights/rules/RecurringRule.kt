package com.ledga.app.data.insights.rules

import com.ledga.app.data.db.entity.InsightSeverity
import com.ledga.app.data.db.entity.InsightType
import com.ledga.app.data.insights.InsightCandidate
import com.ledga.app.data.insights.InsightRule
import com.ledga.app.data.insights.RuleContext
import com.ledga.app.data.parser.TransactionDirection
import com.ledga.app.data.parser.TransactionType
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Recurring-payment detection.
 *
 * For each outflow merchant (recipientName) with ≥3 transactions in the
 * last 90 days, compute consecutive-interval days. If the median interval
 * is within ±[INTERVAL_SLOP_DAYS] days of 30, flag as a recurring monthly
 * payment.
 *
 * Copy differentiates "monthly bill" (paybill / buy-goods → businesses)
 * from "recurring payment" (send-to-person → likely rent, allowances, etc).
 * Both are useful but reading "X looks like a monthly bill" for a person
 * name is jarring.
 *
 * Natural key: `recurring:{merchantHash}` — one persistent insight per
 * merchant. Re-runs refresh content (e.g., updated average amount).
 */
class RecurringRule @Inject constructor() : InsightRule {

    override fun evaluate(ctx: RuleContext): List<InsightCandidate> {
        val byMerchant = ctx.transactions
            .asSequence()
            .filter { it.direction == TransactionDirection.OUTFLOW }
            .filter { it.recipientName != null }
            .groupBy { it.recipientName!! }
            .filterValues { it.size >= MIN_OCCURRENCES }

        return byMerchant.mapNotNull { (merchant, txns) ->
            val sorted = txns.sortedBy { it.timestamp }
            val intervals = sorted.zipWithNext { a, b ->
                (b.timestamp - a.timestamp) / DAY_MS
            }
            val median = intervals.sorted()[intervals.size / 2]
            val isMonthly = abs(median - 30) <= INTERVAL_SLOP_DAYS
            if (!isMonthly) return@mapNotNull null

            // Distinguish "monthly bill" (looks like a business) from "monthly
            // payment" (looks like a person — name has multiple words).
            val looksLikeBusiness = sorted.any {
                it.type in BUSINESS_TYPES
            } || !merchant.trim().contains(' ')
            val descriptor = if (looksLikeBusiness) "a monthly bill"
            else "a monthly payment"

            val avg = sorted.map { it.amount }.average()
            InsightCandidate(
                naturalKey = "recurring:${merchant.hashCode()}",
                type = InsightType.RECURRING,
                severity = InsightSeverity.INFO,
                typeLabel = "RECURRING DETECTED",
                headline = "${merchant.titlecase()} looks like $descriptor.",
                body = "Avg Ksh ${formatCompact(avg)} · last ${sorted.size} months. " +
                        "Consider pinning it to your budget.",
                ctaLabel = "Add to budget",
                ctaArgs = "merchant=$merchant",
            )
        }
    }

    private fun String.titlecase(): String =
        split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { c -> c.uppercase() }
        }

    private fun formatCompact(amount: Double): String = when {
        amount >= 1000 -> "%.1fK".format(amount / 1000)
        else -> amount.roundToInt().toString()
    }

    companion object {
        const val MIN_OCCURRENCES = 3
        const val INTERVAL_SLOP_DAYS = 7L
        private const val DAY_MS = 86_400_000L

        /** Types that strongly indicate a business / bill, not a person. */
        private val BUSINESS_TYPES = setOf(
            TransactionType.PAY_BILL,
            TransactionType.BUY_GOODS,
        )
    }
}
