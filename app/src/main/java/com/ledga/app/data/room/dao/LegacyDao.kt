package com.ledga.app.data.room.dao

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery

data class LegacyTxRow(
    val code: String,
    val categoryId: Long?,
    val note: String?,
    val carTag: String?,
    val accountId: Long?,
    val type: String,
    val direction: String,
    val recipientName: String?,
    val accountNumber: String?,
)

data class LegacyRuleRow(val id: Long, val categoryId: Long, val matchType: String, val matchValue: String)

data class LegacyCategoryRow(val id: Long, val name: String, val icon: String, val color: String, val isDefault: Boolean, val isTransfer: Boolean)

/** Reads the `legacy_*` staging tables MIGRATION_5_6 leaves for LegacyImporter. */
@Dao
interface LegacyDao {
    @RawQuery suspend fun legacyTx(query: SupportSQLiteQuery): List<LegacyTxRow>
    @RawQuery suspend fun legacyRules(query: SupportSQLiteQuery): List<LegacyRuleRow>
    @RawQuery suspend fun legacyCategories(query: SupportSQLiteQuery): List<LegacyCategoryRow>
    @RawQuery suspend fun count(query: SupportSQLiteQuery): Int
}
