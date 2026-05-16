package com.ledga.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ledga.app.data.db.entity.Insight
import kotlinx.coroutines.flow.Flow

@Dao
interface InsightDao {

    /**
     * Upsert by natural key. If a row with the same naturalKey exists we
     * keep its id + dismiss/snooze state and just refresh content + timestamp.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(insight: Insight): Long

    @Query("""
        UPDATE insights
        SET headline = :headline,
            body = :body,
            typeLabel = :typeLabel,
            severity = :severity,
            ctaLabel = :ctaLabel,
            ctaArgs = :ctaArgs,
            generatedAt = :generatedAt
        WHERE naturalKey = :naturalKey
    """)
    suspend fun refresh(
        naturalKey: String,
        typeLabel: String,
        severity: String,
        headline: String,
        body: String?,
        ctaLabel: String?,
        ctaArgs: String?,
        generatedAt: Long,
    )

    @Query("SELECT * FROM insights WHERE naturalKey = :key LIMIT 1")
    suspend fun findByKey(key: String): Insight?

    /**
     * Active = not dismissed AND (not snoozed OR snooze expired).
     * Ordered by severity priority desc, then by generation time desc.
     */
    @Query("""
        SELECT * FROM insights
        WHERE dismissedAt IS NULL
          AND (snoozedUntil IS NULL OR snoozedUntil < :now)
        ORDER BY
            CASE severity
                WHEN 'ALERT' THEN 3
                WHEN 'WARN'  THEN 2
                WHEN 'INFO'  THEN 1
                WHEN 'NUDGE' THEN 0
                ELSE 0
            END DESC,
            generatedAt DESC
    """)
    fun observeActive(now: Long): Flow<List<Insight>>

    @Query("""
        SELECT * FROM insights
        WHERE dismissedAt IS NULL
          AND (snoozedUntil IS NULL OR snoozedUntil < :now)
        ORDER BY
            CASE severity
                WHEN 'ALERT' THEN 3
                WHEN 'WARN'  THEN 2
                WHEN 'INFO'  THEN 1
                WHEN 'NUDGE' THEN 0
                ELSE 0
            END DESC,
            generatedAt DESC
        LIMIT 1
    """)
    fun observeTop(now: Long): Flow<Insight?>

    @Query("UPDATE insights SET dismissedAt = :now WHERE id = :id")
    suspend fun dismiss(id: Long, now: Long)

    @Query("UPDATE insights SET snoozedUntil = :until WHERE id = :id")
    suspend fun snooze(id: Long, until: Long)

    @Query("DELETE FROM insights WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM insights WHERE naturalKey = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM insights WHERE generatedAt < :before AND dismissedAt IS NOT NULL")
    suspend fun pruneOldDismissed(before: Long)
}
