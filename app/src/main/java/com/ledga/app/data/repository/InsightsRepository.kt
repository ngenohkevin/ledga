package com.ledga.app.data.repository

import com.ledga.app.data.db.dao.InsightDao
import com.ledga.app.data.db.entity.Insight
import com.ledga.app.data.insights.InsightEngine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InsightsRepository @Inject constructor(
    private val insightDao: InsightDao,
    private val engine: InsightEngine,
) {

    /** Re-run the rule engine. Idempotent — safe to call on every app open. */
    suspend fun generateAll(): Int = engine.run()

    /** Live list of currently visible insights (not dismissed, not snoozed). */
    fun observeActive(): Flow<List<Insight>> =
        insightDao.observeActive(System.currentTimeMillis())

    /** The single highest-priority active insight — for the Home teaser. */
    fun observeTop(): Flow<Insight?> =
        insightDao.observeTop(System.currentTimeMillis())

    suspend fun dismiss(id: Long) {
        insightDao.dismiss(id, System.currentTimeMillis())
    }

    /** Snooze by [days]; once expired the insight reappears. */
    suspend fun snooze(id: Long, days: Long = 30) {
        val until = System.currentTimeMillis() + days * 86_400_000L
        insightDao.snooze(id, until)
    }
}
