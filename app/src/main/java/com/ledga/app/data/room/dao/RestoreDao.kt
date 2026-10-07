package com.ledga.app.data.room.dao

import androidx.room.Dao
import androidx.room.Query

/** R121: "Replace everything" clears this phone's history and puts the built-ins back, before the backup comes in. */
@Dao
interface RestoreDao {
    @Query("DELETE FROM sms")
    suspend fun deleteSms()

    @Query("DELETE FROM transactions")
    suspend fun deleteTransactions()

    @Query("DELETE FROM overrides")
    suspend fun deleteOverrides()

    @Query("DELETE FROM rules WHERE origin = 'USER'")
    suspend fun deleteUserRules()

    @Query("UPDATE rules SET enabled = 1 WHERE origin = 'SYSTEM'")
    suspend fun enableBuiltInRules()

    @Query("DELETE FROM categories WHERE origin = 'USER'")
    suspend fun deleteUserCategories()

    @Query("UPDATE categories SET name = :name, icon3d = :icon, color = NULL, colorDark = NULL, tracked = :tracked, archived = 0 WHERE `key` = :key")
    suspend fun resetBuiltIn(key: String, name: String, icon: String, tracked: Boolean)

    @Query("DELETE FROM lines")
    suspend fun deleteLines()
}
