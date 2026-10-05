package com.ledga.app.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ledga.app.data.room.OverrideRow

@Dao
interface OverridesDao {
    @Upsert
    suspend fun upsert(row: OverrideRow)

    @Query("SELECT * FROM overrides WHERE code IN (:codes)")
    suspend fun byCodes(codes: List<String>): List<OverrideRow>

    @Query("SELECT * FROM overrides")
    suspend fun all(): List<OverrideRow>

    @Query("SELECT * FROM overrides WHERE code = :code")
    suspend fun get(code: String): OverrideRow?
}
