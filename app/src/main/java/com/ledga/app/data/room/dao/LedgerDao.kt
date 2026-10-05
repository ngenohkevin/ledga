package com.ledga.app.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import com.ledga.app.data.room.LedgerRow
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class CategoryTotal(val categoryKey: String, val cents: Long, val count: Int)

/** Every aggregate reads the `ledger` view: the spending definition exists once. [to] null = a live, open-ended range. */
@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger")
    suspend fun all(): List<LedgerRow>

    @Query(
        "SELECT COALESCE(SUM(spendCents + feeCents), 0) FROM ledger " +
            "WHERE occurredAt >= :from AND (:to IS NULL OR occurredAt < :to) AND (:lineId IS NULL OR lineId = :lineId)",
    )
    fun spent(from: Instant, to: Instant?, lineId: Long?): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(inCents), 0) FROM ledger " +
            "WHERE occurredAt >= :from AND (:to IS NULL OR occurredAt < :to) AND (:lineId IS NULL OR lineId = :lineId)",
    )
    fun moneyIn(from: Instant, to: Instant?, lineId: Long?): Flow<Long>

    @Query(
        "SELECT categoryKey, SUM(spendCents + feeCents) AS cents, COUNT(*) AS count FROM ledger " +
            "WHERE occurredAt >= :from AND (:to IS NULL OR occurredAt < :to) AND (:lineId IS NULL OR lineId = :lineId) " +
            "AND spendCents + feeCents > 0 GROUP BY categoryKey ORDER BY cents DESC, categoryKey",
    )
    fun spentByCategory(from: Instant, to: Instant?, lineId: Long?): Flow<List<CategoryTotal>>
}
