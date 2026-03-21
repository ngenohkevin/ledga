package com.ledga.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ledga.app.data.db.entity.CategoryRule
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryRuleDao {

    @Query("SELECT * FROM category_rules")
    fun getAllRules(): Flow<List<CategoryRule>>

    @Query("SELECT * FROM category_rules WHERE categoryId = :categoryId")
    fun getRulesForCategory(categoryId: Long): Flow<List<CategoryRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<CategoryRule>)

    @Query("SELECT * FROM category_rules")
    suspend fun getAllRulesSync(): List<CategoryRule>
}
