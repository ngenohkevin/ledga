package com.ledga.app.data.room.dao

import com.ledga.app.data.room.BalanceReading
import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ledga.app.data.room.FulizaReading
import com.ledga.app.data.room.LineBalance
import com.ledga.app.data.room.TxRow
import com.ledga.app.data.room.TxSpan
import com.ledga.core.model.FlowKind
import kotlinx.coroutines.flow.Flow
import java.time.Instant

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

    /** One transaction, live: the sheet follows every edit. */
    @Query("SELECT * FROM transactions WHERE code = :code")
    fun observe(code: String): Flow<TxRow?>

    /** Candidates for a name rule: a LIKE pre-filter ([like] from `LedgerQueries.likePattern`); `RuleEngine.matches` decides. */
    @Query("SELECT * FROM transactions WHERE counterpartyName LIKE :like ESCAPE '\\'")
    suspend fun named(like: String): List<TxRow>

    @Query("SELECT * FROM transactions")
    suspend fun all(): List<TxRow>

    @Query("UPDATE transactions SET flow = :flow, categoryKey = :categoryKey WHERE code = :code")
    suspend fun updateClassification(code: String, flow: FlowKind, categoryKey: String)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    /** How much history there is (the interim Home now, You's profile counts in 4d). */
    @Query("SELECT COUNT(*) AS count, MIN(occurredAt) AS firstAt, MAX(occurredAt) AS lastAt FROM transactions WHERE isHidden = 0")
    fun observeSpan(): Flow<TxSpan>

    /** Activity › Transactions (§7.5, R38): newest first, through the shared `TX_FILTER`. */
    @Query("SELECT t.* FROM transactions t WHERE " + TX_FILTER + " ORDER BY t.occurredAt DESC, t.code DESC")
    fun page(
        includeHidden: Boolean,
        lineId: Long?,
        like: String?,
        flow: String,
        anyCategory: Boolean,
        categories: List<String>,
        from: Instant?,
        to: Instant?,
        minCents: Long?,
        counterpartyKey: String?,
    ): PagingSource<Int, TxRow>

    /**
     * Each line's latest stated wallet balance (one row per lineId, the null line included). The latest-code subquery
     * runs once per distinct line, not once per row (a per-row correlated subquery took ~10 s at 10k rows).
     */
    @Query(
        "SELECT t.lineId AS lineId, t.balanceCents AS balanceCents FROM transactions t WHERE t.code IN (" +
            "SELECT (SELECT t2.code FROM transactions t2 WHERE t2.balanceCents IS NOT NULL AND t2.lineId IS l.lineId " +
            "ORDER BY t2.occurredAt DESC, t2.code DESC LIMIT 1) " +
            "FROM (SELECT DISTINCT lineId FROM transactions WHERE balanceCents IS NOT NULL) l)",
    )
    suspend fun latestBalances(): List<LineBalance>

    /** [latestBalances] with each reading's time, live for Home (spec §7.5). Hidden payments count: the balance is the wallet's. */
    @Query(
        "SELECT t.lineId AS lineId, t.balanceCents AS balanceCents, t.occurredAt AS occurredAt FROM transactions t WHERE t.code IN (" +
            "SELECT (SELECT t2.code FROM transactions t2 WHERE t2.balanceCents IS NOT NULL AND t2.lineId IS l.lineId " +
            "ORDER BY t2.occurredAt DESC, t2.code DESC LIMIT 1) " +
            "FROM (SELECT DISTINCT lineId FROM transactions WHERE balanceCents IS NOT NULL) l)",
    )
    fun observeLatestBalances(): Flow<List<BalanceReading>>

    /** [fulizaReadings], live for Home's Fuliza strip (R58). */
    @Query(
        "SELECT code, lineId, kind, occurredAt, amountCents, fulizaOutstandingCents, fulizaLimitCents, fulizaDueDate FROM transactions " +
            "WHERE fulizaOutstandingCents IS NOT NULL OR fulizaLimitCents IS NOT NULL " +
            "OR kind IN ('FULIZA_REPAY_AUTO', 'FULIZA_REPAY_MANUAL') ORDER BY occurredAt, code",
    )
    fun observeFulizaReadings(): Flow<List<FulizaReading>>

    /** Home's Recent and Tracker detail's payments: newest first, in Activity's order; hidden ones left out. */
    @Query(
        "SELECT * FROM transactions WHERE isHidden = 0 AND (:lineId IS NULL OR lineId = :lineId) " +
            "AND (:categoryKey IS NULL OR categoryKey = :categoryKey) ORDER BY occurredAt DESC, code DESC LIMIT :limit",
    )
    fun recent(lineId: Long?, categoryKey: String?, limit: Int): Flow<List<TxRow>>

    @Query(
        "SELECT code, lineId, kind, occurredAt, amountCents, fulizaOutstandingCents, fulizaLimitCents, fulizaDueDate FROM transactions " +
            "WHERE fulizaOutstandingCents IS NOT NULL OR fulizaLimitCents IS NOT NULL " +
            "OR kind IN ('FULIZA_REPAY_AUTO', 'FULIZA_REPAY_MANUAL') ORDER BY occurredAt, code",
    )
    suspend fun fulizaReadings(): List<FulizaReading>
}
