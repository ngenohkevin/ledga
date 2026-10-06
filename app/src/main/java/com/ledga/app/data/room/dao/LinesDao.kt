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

    /** -1 when another caller created the same subscription first (the caller re-reads). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: LineRow): Long

    @Update
    suspend fun update(row: LineRow)

    @Query("UPDATE lines SET displayName = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)
}
