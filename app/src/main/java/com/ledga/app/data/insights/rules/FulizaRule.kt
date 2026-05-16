package com.ledga.app.data.insights.rules

import com.ledga.app.data.db.entity.InsightSeverity
import com.ledga.app.data.db.entity.InsightType
import com.ledga.app.data.insights.InsightCandidate
import com.ledga.app.data.insights.InsightRule
import com.ledga.app.data.insights.RuleContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Fuliza outstanding-balance alert.
 *
 * Inspects the latest transaction whose `fulizaOutstanding` is non-null and
 * fires an ALERT if the outstanding amount is positive. Recorded with a
 * fixed natural key so there's always at most one Fuliza card active.
 *
 * The card auto-resolves: when a later transaction shows the outstanding is
 * back to zero, the rule emits no candidate, and the engine will fall back
 * to the previously generated card eventually expiring via [pruneOldDismissed].
 * In the meantime we mark it dismissed implicitly by stamping `dismissedAt`
 * — that happens in the repository's `generateAll` after every rule pass.
 */
class FulizaRule @Inject constructor() : InsightRule {

    override fun evaluate(ctx: RuleContext): List<InsightCandidate> {
        val latestWithFuliza = ctx.transactions
            .firstOrNull { it.fulizaOutstanding != null }
            ?: return emptyList()
        val outstanding = latestWithFuliza.fulizaOutstanding ?: 0.0
        if (outstanding <= 0.0) return emptyList()

        val daysAgo = TimeUnit.MILLISECONDS.toDays(ctx.now - latestWithFuliza.timestamp)
        val ago = when (daysAgo) {
            0L -> "today"
            1L -> "yesterday"
            else -> "$daysAgo days ago"
        }

        return listOf(
            InsightCandidate(
                naturalKey = "fuliza:outstanding",
                type = InsightType.FULIZA,
                severity = InsightSeverity.ALERT,
                typeLabel = "FULIZA OVERDRAFT",
                headline = "You owe Ksh ${outstanding.roundToInt()} on Fuliza.",
                body = "Drawn $ago. Any money you receive on M-Pesa is " +
                        "auto-deducted toward this — a daily charge keeps " +
                        "adding until it's cleared.",
                ctaLabel = "How Fuliza charges work",
            )
        )
    }
}
