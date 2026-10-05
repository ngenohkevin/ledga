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
}
