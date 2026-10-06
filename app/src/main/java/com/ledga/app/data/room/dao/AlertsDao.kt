package com.ledga.app.data.room.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ledga.app.data.room.AlertRow
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** An alert and its payment's code while that payment exists and isn't hidden (R71: only then can it open). */
data class AlertWithTx(@Embedded val alert: AlertRow, val txCode: String?)

/** The notification log (spec §7.1). Phase 5 writes it; 4d reads it for Alerts and Home's bell. */
@Dao
interface AlertsDao {
    @Query(
        "SELECT a.*, t.code AS txCode FROM alerts a LEFT JOIN transactions t ON t.code = a.targetCode AND t.isHidden = 0 " +
            "ORDER BY a.createdAt DESC, a.`key`",
    )
    fun observeWithTx(): Flow<List<AlertWithTx>>

    @Query("SELECT COUNT(*) FROM alerts WHERE readAt IS NULL")
    fun observeUnread(): Flow<Int>

    @Query("SELECT `key` FROM alerts WHERE readAt IS NULL")
    suspend fun unreadKeys(): List<String>

    /** Chunk [keys] to `Deriver.CHUNK`. */
    @Query("UPDATE alerts SET readAt = :at WHERE `key` IN (:keys) AND readAt IS NULL")
    suspend fun markRead(keys: List<String>, at: Instant)

    /** Phase 5's dedupe (spec §11: nothing posts twice): -1 when the key is already logged. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(row: AlertRow): Long
}
