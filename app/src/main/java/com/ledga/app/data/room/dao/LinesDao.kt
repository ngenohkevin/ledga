package com.ledga.app.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ledga.app.data.room.LineRow
import kotlinx.coroutines.flow.Flow

@Dao
interface LinesDao {
    @Query("SELECT * FROM lines ORDER BY isPrimary DESC, id")
    fun observeAll(): Flow<List<LineRow>>

    @Query("SELECT * FROM lines ORDER BY id")
    suspend fun all(): List<LineRow>

    @Query("SELECT * FROM lines WHERE subscriptionId = :subscriptionId")
    suspend fun bySubscription(subscriptionId: Int): LineRow?

    /** The line this subscription id's past messages are filed under: a SIM whose id has since changed keeps its line. */
    @Query(
        "SELECT lineId FROM sms WHERE subscriptionId = :subscriptionId AND lineId IS NOT NULL " +
            "GROUP BY lineId ORDER BY COUNT(*) DESC LIMIT 1",
    )
    suspend fun lineOfPastMessages(subscriptionId: Int): Long?

    /** -1 when another caller created the same subscription first (the caller re-reads). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: LineRow): Long

    @Update
    suspend fun update(row: LineRow)

    @Query("UPDATE lines SET displayName = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("SELECT * FROM lines WHERE id = :id")
    suspend fun get(id: Long): LineRow?

    /** Every code on line [id]: its messages' and the person's own placements (R178). */
    @Query("SELECT code FROM sms WHERE lineId = :id AND code IS NOT NULL UNION SELECT code FROM overrides WHERE lineId = :id")
    suspend fun codesOnLine(id: Long): List<String>

    @Query("UPDATE sms SET lineId = :into WHERE lineId = :from")
    suspend fun moveSms(from: Long, into: Long): Int

    @Query("UPDATE overrides SET lineId = :into WHERE lineId = :from")
    suspend fun moveOverrides(from: Long, into: Long): Int

    @Query("UPDATE lines SET isPrimary = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setPrimary(id: Long)

    @Query("DELETE FROM lines WHERE id = :id")
    suspend fun delete(id: Long)
}
