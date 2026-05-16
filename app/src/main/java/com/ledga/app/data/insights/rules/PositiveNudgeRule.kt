package com.ledga.app.data.insights.rules

import com.ledga.app.data.db.entity.InsightSeverity
import com.ledga.app.data.db.entity.InsightType
import com.ledga.app.data.insights.InsightCandidate
import com.ledga.app.data.insights.InsightRule
import com.ledga.app.data.insights.RuleContext
import com.ledga.app.data.parser.TransactionDirection
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Per-category "nice — this is down" nudge.
 *
 * Compares this calendar-month's outflow per category against last month's.
 * Fires NUDGE severity when this month is ≤[IMPROVEMENT_RATIO] × last month
 * AND last month was at least [MIN_BASELINE] (so we don't celebrate
 * dropping from 50 → 30 shillings).
 *
 * Natural key: `nudge:{categoryId}:{yyyy-MM}` — one per category per month.
 */
class PositiveNudgeRule @Inject constructor() : InsightRule {

    override fun evaluate(ctx: RuleContext): List<InsightCandidate> {
        val thisMonthStart = startOfMonth(ctx.now)
        val lastMonthStart = startOfMonth(thisMonthStart - 1)

        val outflows = ctx.transactions.filter {
            it.direction == TransactionDirection.OUTFLOW && it.categoryId != null
        }

        val thisMonth = outflows
            .filter { it.timestamp >= thisMonthStart }
            .groupBy { it.categoryId!! }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }

        val lastMonth = outflows
            .filter { it.timestamp in lastMonthStart until thisMonthStart }
            .groupBy { it.categoryId!! }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }

        val monthKey = monthKey(ctx.now)
        val candidates = mutableListOf<InsightCandidate>()

        thisMonth.forEach { (categoryId, current) ->
            val previous = lastMonth[categoryId] ?: return@forEach
            if (previous < MIN_BASELINE) return@forEach
            if (current > previous * IMPROVEMENT_RATIO) return@forEach

            val category = ctx.categoriesById[categoryId] ?: return@forEach
            if (category.name in EXCLUDED_CATEGORIES) return@forEach

            val pctDown = ((1.0 - current / previous) * 100).roundToInt()

            candidates += InsightCandidate(
                naturalKey = "nudge:$categoryId:$monthKey",
                type = InsightType.POSITIVE_NUDGE,
                severity = InsightSeverity.NUDGE,
                typeLabel = "NICE",
                headline = "${category.name} is down $pctDown% this month. Keep it up!",
                body = null,
            )
        }
        return candidates
    }

    private fun startOfMonth(timestamp: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun monthKey(nowMs: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        return "%d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    companion object {
        /** This month must be ≤ this fraction of last month to fire. */
        const val IMPROVEMENT_RATIO = 0.85

        /** Last month must have at least this many shillings to count. */
        const val MIN_BASELINE = 2000.0

        /**
         * Categories whose "down" direction is ambiguous and shouldn't fire
         * a celebratory nudge.
         *  - "Other" → uncategorized; movement here usually means recategorization.
         *  - "Savings & Loans" → groups Fuliza (less = good) with M-Shwari/KCB
         *    deposits (less = bad). Without splitting it, the nudge is mixed.
         *  - "Received" → it's inflow; the rule already filters to OUTFLOW,
         *    but defense-in-depth doesn't hurt.
         */
        private val EXCLUDED_CATEGORIES = setOf(
            "Other",
            "Savings & Loans",
            "Received",
        )
    }
}
