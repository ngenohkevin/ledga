package com.ledga.app.data.room.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ledga.app.data.room.LineBalance
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

    /** The transactions list (§7.5): newest first, hidden excluded; [like] is an already-escaped LIKE pattern. */
    @Query(
        "SELECT * FROM transactions WHERE isHidden = 0 AND (:lineId IS NULL OR lineId = :lineId) " +
            "AND (:like IS NULL OR searchText LIKE :like ESCAPE '\\') ORDER BY occurredAt DESC, code DESC",
    )
    fun page(lineId: Long?, like: String?): PagingSource<Int, TxRow>

    /** Each line's latest stated wallet balance (one row per lineId, the null line included). */
    @Query(
        "SELECT t.lineId AS lineId, t.balanceCents AS balanceCents FROM transactions t WHERE t.balanceCents IS NOT NULL " +
            "AND t.code = (SELECT t2.code FROM transactions t2 WHERE t2.balanceCents IS NOT NULL AND t2.lineId IS t.lineId " +
            "ORDER BY t2.occurredAt DESC, t2.code DESC LIMIT 1)",
    )
    suspend fun latestBalances(): List<LineBalance>
}
