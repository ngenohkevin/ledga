package com.ledga.app.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ledga.app.data.room.RuleRow

@Dao
interface RulesDao {
    @Query("SELECT * FROM rules ORDER BY id")
    suspend fun all(): List<RuleRow>

    @Insert
    suspend fun insert(row: RuleRow): Long

    /** Removes USER rules like a new one (same field, the pattern ignoring case, same action): one per name and choice. */
    @Query("DELETE FROM rules WHERE origin = 'USER' AND field = :field AND pattern = :pattern COLLATE NOCASE AND action = :action")
    suspend fun deleteUser(field: String, pattern: String, action: String): Int
}
