package com.ledga.app.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ledga.app.data.room.CategoryRow

@Dao
interface CategoriesDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, `key`")
    suspend fun all(): List<CategoryRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(row: CategoryRow): Long
}
