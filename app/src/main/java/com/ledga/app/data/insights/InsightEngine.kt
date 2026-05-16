package com.ledga.app.data.insights

import com.ledga.app.data.db.dao.InsightDao
import com.ledga.app.data.db.entity.Insight
import com.ledga.app.data.repository.CategoryRepository
import com.ledga.app.data.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrator that runs every [InsightRule] against the same data snapshot
 * and persists candidates as [Insight] rows.
 *
 * Idempotency is the key contract: re-running with no data changes must not
 * create duplicate rows or destroy user dismiss/snooze state. We achieve that
 * with [InsightCandidate.naturalKey] + an "insert-if-absent, else refresh"
 * pattern.
 *
 * Old dismissed rows older than 30 days are pruned so the table doesn't grow
 * unboundedly.
 */
@Singleton
class InsightEngine @Inject constructor(
    private val rules: List<@JvmSuppressWildcards InsightRule>,
    private val insightDao: InsightDao,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) {

    suspend fun run(now: Long = System.currentTimeMillis()): Int {
        val ninetyDaysAgo = now - 90L * DAY_MS
        val txns = transactionRepository
            .getTransactions(ninetyDaysAgo, now)
            .first()
            .map { it.transaction }
            .sortedByDescending { it.timestamp }

        val categoriesById = categoryRepository
            .getAllCategories()
            .first()
            .associateBy { it.id }

        val ctx = RuleContext(now = now, transactions = txns, categoriesById = categoriesById)
        val candidates = rules.flatMap { rule ->
            runCatching { rule.evaluate(ctx) }
                .onFailure { /* rules are pure — but never let one bad rule poison the run */ }
                .getOrDefault(emptyList())
        }

        candidates.forEach { upsert(it, now) }
        // Stateful keys: if a rule that "owns" a singleton key didn't emit
        // this run, the underlying condition is gone — delete the stale row
        // outright (vs leaving it to expire 30 days post-dismiss).
        val emittedKeys = candidates.map { it.naturalKey }.toSet()
        STATEFUL_KEYS.forEach { key ->
            if (key !in emittedKeys) insightDao.deleteByKey(key)
        }
        insightDao.pruneOldDismissed(before = now - 30L * DAY_MS)
        return candidates.size
    }

    private suspend fun upsert(c: InsightCandidate, now: Long) {
        val existing = insightDao.findByKey(c.naturalKey)
        if (existing == null) {
            insightDao.insertIfAbsent(
                Insight(
                    naturalKey = c.naturalKey,
                    type = c.type,
                    severity = c.severity,
                    typeLabel = c.typeLabel,
                    headline = c.headline,
                    body = c.body,
                    ctaLabel = c.ctaLabel,
                    ctaArgs = c.ctaArgs,
                    generatedAt = now,
                )
            )
        } else {
            insightDao.refresh(
                naturalKey = c.naturalKey,
                typeLabel = c.typeLabel,
                severity = c.severity.name,
                headline = c.headline,
                body = c.body,
                ctaLabel = c.ctaLabel,
                ctaArgs = c.ctaArgs,
                generatedAt = now,
            )
        }
    }

    companion object {
        private const val DAY_MS = 86_400_000L

        /**
         * Natural keys whose underlying condition is stateful — only true when
         * a current event matches. If a rule using one of these keys returns
         * no candidates this run, the existing insight is stale and should
         * be deleted, not just re-emitted.
         *
         * Per-period keys (anomaly:1:2026-W12, nudge:2:2026-04, fees:2026-04)
         * are NOT stateful in this sense — they refer to a fixed past period
         * and should persist in history.
         */
        private val STATEFUL_KEYS = setOf(
            "fuliza:outstanding",
        )
    }
}
