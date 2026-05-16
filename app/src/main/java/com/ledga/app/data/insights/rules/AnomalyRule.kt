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
import kotlin.math.sqrt

/**
 * Per-category anomaly detection.
 *
 * For each category, compute spending in the **current ISO-week** vs the
 * mean+stddev of the previous 4 ISO-weeks. Flag when this week is more than
 * `MIN_Z` standard deviations above the baseline AND the absolute amount
 * crosses [MIN_THRESHOLD] (to suppress noise on tiny categories).
 *
 * Natural key: `anomaly:{categoryId}:{ISO-year-week}` — so the same insight
 * doesn't fire twice in the same week, and the user's dismiss state survives
 * regeneration.
 */
class AnomalyRule @Inject constructor() : InsightRule {

    override fun evaluate(ctx: RuleContext): List<InsightCandidate> {
        val outflows = ctx.transactions.filter { it.direction == TransactionDirection.OUTFLOW }
        if (outflows.isEmpty()) return emptyList()

        val byCategoryByWeek: Map<Long, Map<String, Double>> = outflows
            .filter { it.categoryId != null }
            .groupBy { it.categoryId!! }
            .mapValues { (_, txns) ->
                txns.groupBy { weekKey(it.timestamp) }
                    .mapValues { (_, ts) -> ts.sumOf { it.amount } }
            }

        val currentWeek = weekKey(ctx.now)
        val candidates = mutableListOf<InsightCandidate>()

        byCategoryByWeek.forEach { (categoryId, weekly) ->
            val thisWeek = weekly[currentWeek] ?: return@forEach
            if (thisWeek < MIN_THRESHOLD) return@forEach

            val baselineWeeks = previousWeekKeys(ctx.now, n = 4)
            val baseline = baselineWeeks.map { weekly[it] ?: 0.0 }
            if (baseline.all { it == 0.0 }) return@forEach // no history to compare

            val mean = baseline.average()
            val variance = baseline.map { (it - mean) * (it - mean) }.average()
            val stddev = sqrt(variance)
            // If baseline is mostly zero with a tiny stddev, fall back to a flat
            // 80% spike threshold so we still catch obvious jumps.
            val triggered = if (stddev > 1.0) {
                (thisWeek - mean) / stddev >= MIN_Z
            } else {
                mean > 0.0 && thisWeek >= mean * 1.8
            }
            if (!triggered) return@forEach

            val category = ctx.categoriesById[categoryId] ?: return@forEach
            val pctOver = if (mean > 0.0) {
                ((thisWeek / mean) - 1.0).times(100).roundToInt()
            } else 100

            candidates += InsightCandidate(
                naturalKey = "anomaly:$categoryId:$currentWeek",
                type = InsightType.ANOMALY,
                severity = InsightSeverity.WARN,
                typeLabel = "WATCH OUT",
                headline = "You're spending $pctOver% more on " +
                        "${category.name.lowercase()} this week than usual.",
                body = "Typical week ~Ksh ${formatCompact(mean)}; this week Ksh ${formatCompact(thisWeek)}.",
                ctaLabel = "See transactions",
                ctaArgs = "category=$categoryId",
            )
        }
        return candidates
    }

    private fun weekKey(timestampMs: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        // ISO-style: yyyy-Www
        val year = cal.weekYear
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        return "%d-W%02d".format(year, week)
    }

    private fun previousWeekKeys(nowMs: Long, n: Int): List<String> {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        return (1..n).map {
            cal.add(Calendar.WEEK_OF_YEAR, -1)
            weekKey(cal.timeInMillis)
        }
    }

    private fun formatCompact(amount: Double): String = when {
        amount >= 1000 -> "%.1fK".format(amount / 1000)
        else -> "%.0f".format(amount)
    }

    companion object {
        /** Minimum z-score over the rolling baseline to fire. */
        const val MIN_Z = 1.5

        /** Minimum absolute weekly spend to consider — suppresses noise. */
        const val MIN_THRESHOLD = 1000.0
    }
}
