package com.ledga.app.data.room.dao

import kotlinx.coroutines.flow.Flow
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ledga.app.data.room.RuleRow

@Dao
interface RulesDao {
    @Query("SELECT * FROM rules ORDER BY id")
    suspend fun all(): List<RuleRow>

    /** Tracker detail's "Matched by" (R49): the enabled rules filing into [categoryKey], the person's first and newest first. */
    @Query(
        "SELECT * FROM rules WHERE action = 'SET_CATEGORY' AND categoryKey = :categoryKey AND enabled = 1 " +
            "ORDER BY CASE origin WHEN 'USER' THEN 0 ELSE 1 END, createdAt DESC, id",
    )
    fun observeForCategory(categoryKey: String): Flow<List<RuleRow>>

    /** Categories & rules (R67): every rule, on or off — the person's first and newest first, then built-in ones in seed order. */
    @Query("SELECT * FROM rules ORDER BY CASE origin WHEN 'USER' THEN 0 ELSE 1 END, CASE origin WHEN 'USER' THEN -createdAt ELSE 0 END, id")
    fun observeAll(): Flow<List<RuleRow>>

    @Insert
    suspend fun insert(row: RuleRow): Long

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun get(id: Long): RuleRow?

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun delete(id: Long)

    /** Built-in rules are switched off, never deleted (spec §7.1). */
    @Query("UPDATE rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    /** Removes USER rules like a new one (same field, the pattern ignoring case, same action): one per name and choice. */
    @Query("DELETE FROM rules WHERE origin = 'USER' AND field = :field AND pattern = :pattern COLLATE NOCASE AND action = :action")
    suspend fun deleteUser(field: String, pattern: String, action: String): Int

    /** The person's rules [deleteUser] would replace. */
    @Query("SELECT * FROM rules WHERE origin = 'USER' AND field = :field AND pattern = :pattern COLLATE NOCASE AND action = :action")
    suspend fun userLike(field: String, pattern: String, action: String): List<RuleRow>
}
