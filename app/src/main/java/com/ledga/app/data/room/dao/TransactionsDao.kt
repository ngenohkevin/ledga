package com.ledga.app.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ledga.app.data.room.TxRow
import com.ledga.core.model.FlowKind

@Dao
interface TransactionsDao {
    @Upsert
    suspend fun upsertAll(rows: List<TxRow>)

    @Query("DELETE FROM transactions WHERE code IN (:codes)")
    suspend fun deleteCodes(codes: List<String>)

    @Query("SELECT reversesCode FROM transactions WHERE code IN (:codes) AND reversesCode IS NOT NULL")
    suspend fun reversesCodesOf(codes: List<String>): List<String>

    @Query("DELETE FROM transactions WHERE code NOT IN (SELECT code FROM sms WHERE status = 'PARSED' AND code IS NOT NULL)")
    suspend fun deleteWithoutSms(): Int

    /** A transaction is reversed when another one's reversesCode equals its code (`:core` Reversals). */
    @Query("UPDATE transactions SET isReversed = EXISTS(SELECT 1 FROM transactions r WHERE r.reversesCode = transactions.code) WHERE code IN (:codes)")
    suspend fun refreshReversed(codes: List<String>)

    @Query("UPDATE transactions SET isReversed = EXISTS(SELECT 1 FROM transactions r WHERE r.reversesCode = transactions.code)")
    suspend fun refreshAllReversed()

    @Query("SELECT * FROM transactions WHERE code = :code")
    suspend fun get(code: String): TxRow?

    @Query("SELECT * FROM transactions")
    suspend fun all(): List<TxRow>

    @Query("UPDATE transactions SET flow = :flow, categoryKey = :categoryKey WHERE code = :code")
    suspend fun updateClassification(code: String, flow: FlowKind, categoryKey: String)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
}
