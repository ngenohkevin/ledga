package com.ledga.app.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ledga.app.data.room.SmsRow
import com.ledga.app.data.room.SmsStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    /** -1 when the body hash already exists (a duplicate). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(row: SmsRow): Long

    @Query("SELECT * FROM sms WHERE status = 'PARSED' AND code IN (:codes)")
    suspend fun parsedByCodes(codes: List<String>): List<SmsRow>

    @Query("SELECT DISTINCT code FROM sms WHERE status = 'PARSED' AND code IS NOT NULL")
    suspend fun parsedCodes(): List<String>

    @Query("SELECT * FROM sms WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun pageAfter(afterId: Long, limit: Int): List<SmsRow>

    @Query("UPDATE sms SET code = :code, status = :status, statusReason = :reason, parserVersion = :parserVersion WHERE id = :id")
    suspend fun updateParse(id: Long, code: String?, status: SmsStatus, reason: String?, parserVersion: Int)

    /** Sets the real hash of a legacy row; 0 when another row already has that hash (the caller deletes this one). */
    @Query("UPDATE OR IGNORE sms SET bodyHash = :hash, code = :code, status = :status, statusReason = :reason, parserVersion = :parserVersion WHERE id = :id")
    suspend fun rehash(id: Long, hash: String, code: String?, status: SmsStatus, reason: String?, parserVersion: Int): Int

    @Query("DELETE FROM sms WHERE id = :id")
    suspend fun delete(id: Long)

    /** Legacy rows still carrying their provisional hash (refinement R10). */
    @Query("SELECT * FROM sms WHERE source = 'LEGACY' AND bodyHash LIKE 'legacy:%' ORDER BY id")
    suspend fun legacyPending(): List<SmsRow>

    @Query("SELECT * FROM sms WHERE status = 'UNREADABLE' ORDER BY receivedAt DESC")
    suspend fun unreadable(): List<SmsRow>

    @Query("SELECT COUNT(*) FROM sms WHERE status = :status")
    suspend fun countByStatus(status: SmsStatus): Int

    /** Every stored message (R119: no snapshot is written while there are none). */
    @Query("SELECT COUNT(*) FROM sms")
    suspend fun count(): Int

    /** R121: a restored message already here, with no line yet, takes the backup's line. */
    @Query("UPDATE sms SET lineId = :lineId WHERE bodyHash = :hash AND lineId IS NULL")
    suspend fun fillLine(hash: String, lineId: Long): Int

    /** R78: Messages Ledga couldn't read, newest first. */
    @Query("SELECT * FROM sms WHERE status = 'UNREADABLE' ORDER BY receivedAt DESC, id DESC")
    fun observeUnreadable(): Flow<List<SmsRow>>

    @Query("SELECT COUNT(*) FROM sms WHERE status = 'UNREADABLE'")
    fun observeUnreadableCount(): Flow<Int>

    /** Every SMS merged into one transaction, oldest first (the sheet's "Original SMS", spec §10.4). */
    @Query("SELECT body FROM sms WHERE code = :code AND status = 'PARSED' ORDER BY receivedAt, id")
    fun bodiesFor(code: String): Flow<List<String>>
}
