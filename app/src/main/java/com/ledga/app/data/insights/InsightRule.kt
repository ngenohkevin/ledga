package com.ledga.app.data.insights

import com.ledga.app.data.db.entity.Category
import com.ledga.app.data.db.entity.InsightSeverity
import com.ledga.app.data.db.entity.InsightType
import com.ledga.app.data.db.entity.TransactionEntity

/**
 * What a rule sees when it runs.
 *
 * Rules are pure functions over this context — no IO, no DAOs — so they're
 * trivially unit-testable. The engine loads everything once per run.
 */
data class RuleContext(
    val now: Long,
    /** All transactions in the last 90 days, newest first. */
    val transactions: List<TransactionEntity>,
    val categoriesById: Map<Long, Category>,
)

/**
 * One thing a rule would tell the user. The engine maps these to [Insight]
 * rows, deduping by [naturalKey].
 */
data class InsightCandidate(
    val naturalKey: String,
    val type: InsightType,
    val severity: InsightSeverity,
    val typeLabel: String,
    val headline: String,
    val body: String? = null,
    val ctaLabel: String? = null,
    val ctaArgs: String? = null,
)

interface InsightRule {
    fun evaluate(ctx: RuleContext): List<InsightCandidate>
}
