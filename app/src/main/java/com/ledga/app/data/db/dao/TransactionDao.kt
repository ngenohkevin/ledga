package com.ledga.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ledga.app.data.db.entity.TransactionEntity
import com.ledga.app.data.db.entity.TransactionWithCategory
import com.ledga.app.data.parser.TransactionType
import kotlinx.coroutines.flow.Flow

data class CategorySpending(
    val categoryId: Long?,
    val totalAmount: Double
)

data class DailySpending(
    val dayTimestamp: Long,
    val totalAmount: Double
)

data class TopMerchant(
    val recipientName: String,
    val totalAmount: Double,
    val transactionCount: Int
)

data class MonthlySpending(
    /** Calendar month key in the device timezone, e.g. "2026-06". */
    val monthKey: String,
    val totalAmount: Double,
    val totalFees: Double,
    val transactionCount: Int
)

/**
 * Every account-aware query takes a nullable `accountId` — null means
 * "Combined view" (all accounts). The repository wires this from the
 * user's selected-account preference.
 */
@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    @androidx.room.Update
    suspend fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAllSync(): List<TransactionEntity>

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :id")
    suspend fun updateCategory(id: Long, categoryId: Long?)

    @Query("UPDATE transactions SET accountId = :accountId WHERE id = :id")
    suspend fun updateAccount(id: Long, accountId: Long?)

    /**
     * Bulk-attribute transactions in a timestamp range to an account.
     * Used by the historical-tagging backfill (date-range fallback).
     */
    @Query("""
        UPDATE transactions SET accountId = :accountId
        WHERE timestamp BETWEEN :startTime AND :endTime
    """)
    suspend fun updateAccountForRange(accountId: Long?, startTime: Long, endTime: Long): Int

    /**
     * Bulk-attribute by transaction code list — used by the SMS-DB backfill
     * after we match codes to subscription ids.
     */
    @Query("""
        UPDATE transactions SET accountId = :accountId
        WHERE transactionCode IN (:codes)
    """)
    suspend fun updateAccountForCodes(accountId: Long, codes: List<String>): Int

    @Transaction
    @Query("""
        SELECT * FROM transactions
        WHERE (:accountId IS NULL OR accountId = :accountId)
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    fun getRecentTransactions(limit: Int = 10, accountId: Long? = null): Flow<List<TransactionWithCategory>>

    @Transaction
    @Query("""
        SELECT * FROM transactions
        WHERE timestamp BETWEEN :startTime AND :endTime
          AND (:accountId IS NULL OR accountId = :accountId)
        ORDER BY timestamp DESC
    """)
    fun getTransactions(startTime: Long, endTime: Long, accountId: Long? = null): Flow<List<TransactionWithCategory>>

    @Query("""
        SELECT * FROM transactions
        WHERE (:accountId IS NULL OR accountId = :accountId)
        ORDER BY timestamp DESC LIMIT 1
    """)
    fun getLatestTransaction(accountId: Long? = null): Flow<TransactionEntity?>

    /**
     * Latest wallet balance for the Balance card. A genuine Ksh0.00 (you spent
     * down to nothing / are running on Fuliza) is a REAL balance and must show.
     * We only skip rows whose balance is a parser-default 0 — Fuliza
     * repayment/reversal/companion and unparsed SMS carry no balance of their
     * own — so a stale positive balance never masks a true zero.
     */
    @Query("""
        SELECT * FROM transactions
        WHERE (:accountId IS NULL OR accountId = :accountId)
          AND NOT (balance = 0 AND type IN
              ('FULIZA_REPAYMENT','FULIZA_REVERSAL','REVERSAL','FULIZA','UNKNOWN'))
        ORDER BY timestamp DESC LIMIT 1
    """)
    fun getLatestTransactionWithBalance(accountId: Long? = null): Flow<TransactionEntity?>

    /*
     * Spending queries share three exclusions beyond direction = OUTFLOW:
     *  - reversed originals (the money came back)
     *  - Fuliza repayments/auto-pay (the Fuliza-backed purchase was already
     *    counted when it happened — counting the repayment doubles it)
     *  - M-Shwari / KCB M-Pesa deposits (own money moving to savings; goals
     *    track these as contributions, not spending)
     */

    @Query("""
        SELECT categoryId, SUM(amount) as totalAmount
        FROM transactions
        WHERE direction = 'OUTFLOW'
          AND timestamp BETWEEN :startTime AND :endTime
          AND (:accountId IS NULL OR accountId = :accountId)
          AND type NOT IN ('FULIZA_REPAYMENT', 'FULIZA_AUTO_PAY', 'MSHWARI', 'KCB_MPESA')
          AND (categoryId IS NULL OR categoryId NOT IN (
              SELECT id FROM categories WHERE isTransfer = 1
          ))
          AND transactionCode NOT IN (
              SELECT reversedTransactionCode FROM transactions
              WHERE reversedTransactionCode IS NOT NULL
          )
        GROUP BY categoryId
    """)
    fun getSpendingByCategory(startTime: Long, endTime: Long, accountId: Long? = null): Flow<List<CategorySpending>>

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM transactions
        WHERE direction = 'OUTFLOW'
          AND timestamp BETWEEN :startTime AND :endTime
          AND (:accountId IS NULL OR accountId = :accountId)
          AND type NOT IN ('FULIZA_REPAYMENT', 'FULIZA_AUTO_PAY', 'MSHWARI', 'KCB_MPESA')
          AND (categoryId IS NULL OR categoryId NOT IN (
              SELECT id FROM categories WHERE isTransfer = 1
          ))
          AND transactionCode NOT IN (
              SELECT reversedTransactionCode FROM transactions
              WHERE reversedTransactionCode IS NOT NULL
          )
    """)
    fun getTotalSpending(startTime: Long, endTime: Long, accountId: Long? = null): Flow<Double>

    @Query("""
        SELECT COALESCE(SUM(transactionCost), 0.0) FROM transactions
        WHERE timestamp BETWEEN :startTime AND :endTime
          AND (:accountId IS NULL OR accountId = :accountId)
    """)
    fun getTotalFees(startTime: Long, endTime: Long, accountId: Long? = null): Flow<Double>

    @Transaction
    @Query("""
        SELECT * FROM transactions
        WHERE recipientName LIKE '%' || :query || '%'
          AND (:accountId IS NULL OR accountId = :accountId)
        ORDER BY timestamp DESC
    """)
    fun searchTransactions(query: String, accountId: Long? = null): Flow<List<TransactionWithCategory>>

    @Transaction
    @Query("""
        SELECT * FROM transactions
        WHERE type IN (:types)
          AND (:accountId IS NULL OR accountId = :accountId)
        ORDER BY timestamp DESC
    """)
    fun getTransactionsByType(types: List<TransactionType>, accountId: Long? = null): Flow<List<TransactionWithCategory>>

    @Query("SELECT * FROM transactions WHERE transactionCode = :code LIMIT 1")
    suspend fun getTransactionByCode(code: String): TransactionEntity?

    @Transaction
    @Query("SELECT * FROM transactions WHERE type = 'UNKNOWN' ORDER BY timestamp DESC")
    fun getUnparsedTransactions(): Flow<List<TransactionWithCategory>>

    @Query("""
        SELECT (timestamp / 86400000) * 86400000 as dayTimestamp, SUM(amount) as totalAmount
        FROM transactions
        WHERE direction = 'OUTFLOW'
          AND timestamp BETWEEN :startTime AND :endTime
          AND (:accountId IS NULL OR accountId = :accountId)
          AND type NOT IN ('FULIZA_REPAYMENT', 'FULIZA_AUTO_PAY', 'MSHWARI', 'KCB_MPESA')
          AND (categoryId IS NULL OR categoryId NOT IN (
              SELECT id FROM categories WHERE isTransfer = 1
          ))
          AND transactionCode NOT IN (
              SELECT reversedTransactionCode FROM transactions
              WHERE reversedTransactionCode IS NOT NULL
          )
        GROUP BY dayTimestamp
        ORDER BY dayTimestamp ASC
    """)
    fun getDailySpending(startTime: Long, endTime: Long, accountId: Long? = null): Flow<List<DailySpending>>

    @Query("""
        SELECT recipientName, SUM(amount) as totalAmount, COUNT(*) as transactionCount
        FROM transactions
        WHERE direction = 'OUTFLOW' AND recipientName IS NOT NULL
            AND timestamp BETWEEN :startTime AND :endTime
            AND (:accountId IS NULL OR accountId = :accountId)
            AND type NOT IN ('FULIZA_REPAYMENT', 'FULIZA_AUTO_PAY', 'MSHWARI', 'KCB_MPESA')
            AND (categoryId IS NULL OR categoryId NOT IN (
                SELECT id FROM categories WHERE isTransfer = 1
            ))
            AND transactionCode NOT IN (
                SELECT reversedTransactionCode FROM transactions
                WHERE reversedTransactionCode IS NOT NULL
            )
        GROUP BY recipientName
        ORDER BY totalAmount DESC
        LIMIT :limit
    """)
    fun getTopMerchants(startTime: Long, endTime: Long, accountId: Long? = null, limit: Int = 5): Flow<List<TopMerchant>>

    @Query("SELECT COUNT(*) FROM transactions WHERE type = 'UNKNOWN'")
    fun getUnparsedCount(): Flow<Int>

    /**
     * Calendar-month spending totals (device timezone), newest first.
     * Same exclusions as [getTotalSpending]: outflows only, reversed
     * originals removed. Fees ride along since inflow rows carry zero cost.
     */
    @Query("""
        SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch', 'localtime') as monthKey,
               SUM(amount) as totalAmount,
               SUM(transactionCost) as totalFees,
               COUNT(*) as transactionCount
        FROM transactions
        WHERE direction = 'OUTFLOW'
          AND (:accountId IS NULL OR accountId = :accountId)
          AND type NOT IN ('FULIZA_REPAYMENT', 'FULIZA_AUTO_PAY', 'MSHWARI', 'KCB_MPESA')
          AND (categoryId IS NULL OR categoryId NOT IN (
              SELECT id FROM categories WHERE isTransfer = 1
          ))
          AND transactionCode NOT IN (
              SELECT reversedTransactionCode FROM transactions
              WHERE reversedTransactionCode IS NOT NULL
          )
        GROUP BY monthKey
        ORDER BY monthKey DESC
        LIMIT :limit
    """)
    fun getMonthlySpending(accountId: Long? = null, limit: Int = 12): Flow<List<MonthlySpending>>

    // ---- Goal progress helpers (Phase D) ----
    // These are account-agnostic by design: a goal spans every line.

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM transactions
        WHERE type IN ('MSHWARI', 'KCB_MPESA')
          AND direction = 'OUTFLOW'
          AND timestamp >= :since
    """)
    fun observeSavingsContributionsSince(since: Long): Flow<Double>

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM transactions
        WHERE recipientName LIKE '%' || :recipientFragment || '%' COLLATE NOCASE
          AND direction = 'OUTFLOW'
          AND timestamp >= :since
    """)
    fun observeRecipientContributionsSince(recipientFragment: String, since: Long): Flow<Double>

    /** All transactions in a window — used by Goal detail to list contributors. */
    @Transaction
    @Query("""
        SELECT * FROM transactions
        WHERE timestamp >= :since
          AND type IN ('MSHWARI', 'KCB_MPESA')
          AND direction = 'OUTFLOW'
        ORDER BY timestamp DESC
    """)
    fun observeSavingsTransactionsSince(since: Long): Flow<List<TransactionWithCategory>>

    @Transaction
    @Query("""
        SELECT * FROM transactions
        WHERE timestamp >= :since
          AND recipientName LIKE '%' || :recipientFragment || '%' COLLATE NOCASE
          AND direction = 'OUTFLOW'
        ORDER BY timestamp DESC
    """)
    fun observeRecipientTransactionsSince(recipientFragment: String, since: Long): Flow<List<TransactionWithCategory>>

    /** Per-recipient outflow totals, used to suggest recipient candidates when building a goal. */
    @Query("""
        SELECT recipientName as recipientName, SUM(amount) as totalAmount, COUNT(*) as transactionCount
        FROM transactions
        WHERE direction = 'OUTFLOW'
          AND recipientName IS NOT NULL
          AND timestamp >= :since
        GROUP BY recipientName
        ORDER BY totalAmount DESC
        LIMIT :limit
    """)
    fun observeRecipientsSince(since: Long, limit: Int = 20): Flow<List<TopMerchant>>

    @Query("SELECT * FROM transactions WHERE type = 'UNKNOWN'")
    suspend fun getUnparsedSync(): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ---- Fuliza status (latest known values from SMS) ----

    @Query("""
        SELECT * FROM transactions
        WHERE fulizaLimit IS NOT NULL
        ORDER BY timestamp DESC LIMIT 1
    """)
    fun getLatestFulizaLimit(): Flow<TransactionEntity?>

    @Query("""
        SELECT * FROM transactions
        WHERE fulizaOutstanding IS NOT NULL
        ORDER BY timestamp DESC LIMIT 1
    """)
    fun getLatestFulizaOutstanding(): Flow<TransactionEntity?>

    // ---- Own-account (transfer) recipient marking ----

    @Query("""
        UPDATE transactions SET categoryId = :categoryId
        WHERE recipientName LIKE '%' || :fragment || '%' COLLATE NOCASE
    """)
    suspend fun updateCategoryForRecipientFragment(fragment: String, categoryId: Long?): Int

    @Query("""
        SELECT * FROM transactions
        WHERE recipientName LIKE '%' || :fragment || '%' COLLATE NOCASE
    """)
    suspend fun getByRecipientFragmentSync(fragment: String): List<TransactionEntity>
}
