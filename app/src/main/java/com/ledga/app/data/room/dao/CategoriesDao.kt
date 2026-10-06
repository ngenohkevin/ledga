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

    @Query("SELECT * FROM categories ORDER BY sortOrder, `key`")
    suspend fun all(): List<CategoryRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(row: CategoryRow): Long
}
