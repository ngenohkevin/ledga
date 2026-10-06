package com.ledga.app.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ledga.app.data.room.OverrideRow
import java.time.Instant

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

    /** Lets rules decide these codes' category again: "apply to all" means all (R36). */
    @Query("UPDATE overrides SET categoryKey = NULL, updatedAt = :at WHERE code IN (:codes)")
    suspend fun clearCategory(codes: List<String>, at: Instant)

    /** Lets rules decide these codes' own-account state again (R37). */
    @Query("UPDATE overrides SET ownAccount = NULL, updatedAt = :at WHERE code IN (:codes)")
    suspend fun clearOwnAccount(codes: List<String>, at: Instant)
}
