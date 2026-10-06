package com.ledga.app.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ledga.app.data.room.CategoryRow
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoriesDao {
    /** Every category, in picker order; screens resolve names, icons and colours from it. */
    @Query("SELECT * FROM categories ORDER BY sortOrder, `key`")
    fun observeAll(): Flow<List<CategoryRow>>

    /** The tracked categories, in picker order (spec §10.4 Trackers). */
    @Query("SELECT * FROM categories WHERE tracked = 1 AND archived = 0 ORDER BY sortOrder, `key`")
    fun observeTracked(): Flow<List<CategoryRow>>

    @Query("SELECT * FROM categories WHERE `key` = :key")
    fun observe(key: String): Flow<CategoryRow?>

    @Query("SELECT * FROM categories ORDER BY sortOrder, `key`")
    suspend fun all(): List<CategoryRow>

    /** Trackers (R50): tracking changes no transaction. */
    @Query("UPDATE categories SET tracked = :tracked WHERE `key` = :key")
    suspend fun setTracked(key: String, tracked: Boolean)

    /** R51: the name shows everywhere; the key stays. */
    @Query("UPDATE categories SET name = :name WHERE `key` = :key")
    suspend fun rename(key: String, name: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(row: CategoryRow): Long
}
