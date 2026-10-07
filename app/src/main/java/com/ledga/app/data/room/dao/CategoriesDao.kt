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

    @Query("SELECT * FROM categories WHERE `key` = :key")
    suspend fun get(key: String): CategoryRow?

    /** D6, R91: any category (`TransactionEdits` checks the key's form). */
    @Query("UPDATE categories SET icon3d = :icon WHERE `key` = :key")
    suspend fun setIcon(key: String, icon: String)

    /** R74: both themes' colours, from one swatch. */
    @Query("UPDATE categories SET color = :light, colorDark = :dark WHERE `key` = :key")
    suspend fun setColor(key: String, light: String, dark: String)

    /** D6: a built-in category's seeded icon, and its palette colour (no stored colour). */
    @Query("UPDATE categories SET icon3d = :icon, color = NULL, colorDark = NULL WHERE `key` = :key")
    suspend fun resetLooks(key: String, icon: String)

    /** R72: archiving stops tracking; bringing it back leaves tracking off. */
    @Query("UPDATE categories SET archived = :archived, tracked = CASE WHEN :archived THEN 0 ELSE tracked END WHERE `key` = :key")
    suspend fun setArchived(key: String, archived: Boolean)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(row: CategoryRow): Long
}
