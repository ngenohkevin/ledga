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

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :id")
    suspend fun updateCategory(id: Long, categoryId: Long?)

    @Transaction
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int = 10): Flow<List<TransactionWithCategory>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getTransactions(startTime: Long, endTime: Long): Flow<List<TransactionWithCategory>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT 1")
    fun getLatestTransaction(): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE balance > 0 ORDER BY timestamp DESC LIMIT 1")
    fun getLatestTransactionWithBalance(): Flow<TransactionEntity?>

    @Query("""
        SELECT categoryId, SUM(amount) as totalAmount
        FROM transactions
        WHERE direction = 'OUTFLOW' AND timestamp BETWEEN :startTime AND :endTime
        GROUP BY categoryId
    """)
    fun getSpendingByCategory(startTime: Long, endTime: Long): Flow<List<CategorySpending>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE direction = 'OUTFLOW' AND timestamp BETWEEN :startTime AND :endTime")
    fun getTotalSpending(startTime: Long, endTime: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(transactionCost), 0.0) FROM transactions WHERE timestamp BETWEEN :startTime AND :endTime")
    fun getTotalFees(startTime: Long, endTime: Long): Flow<Double>

    @Transaction
    @Query("SELECT * FROM transactions WHERE recipientName LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchTransactions(query: String): Flow<List<TransactionWithCategory>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE type IN (:types) ORDER BY timestamp DESC")
    fun getTransactionsByType(types: List<TransactionType>): Flow<List<TransactionWithCategory>>

    @Query("SELECT * FROM transactions WHERE transactionCode = :code LIMIT 1")
    suspend fun getTransactionByCode(code: String): TransactionEntity?

    @Transaction
    @Query("SELECT * FROM transactions WHERE type = 'UNKNOWN' ORDER BY timestamp DESC")
    fun getUnparsedTransactions(): Flow<List<TransactionWithCategory>>

    // Trends: daily spending totals for a period
    @Query("""
        SELECT (timestamp / 86400000) * 86400000 as dayTimestamp, SUM(amount) as totalAmount
        FROM transactions
        WHERE direction = 'OUTFLOW' AND timestamp BETWEEN :startTime AND :endTime
        GROUP BY dayTimestamp
        ORDER BY dayTimestamp ASC
    """)
    fun getDailySpending(startTime: Long, endTime: Long): Flow<List<DailySpending>>

    // Trends: top merchants by total spending
    @Query("""
        SELECT recipientName, SUM(amount) as totalAmount, COUNT(*) as transactionCount
        FROM transactions
        WHERE direction = 'OUTFLOW' AND recipientName IS NOT NULL
            AND timestamp BETWEEN :startTime AND :endTime
        GROUP BY recipientName
        ORDER BY totalAmount DESC
        LIMIT :limit
    """)
    fun getTopMerchants(startTime: Long, endTime: Long, limit: Int = 5): Flow<List<TopMerchant>>

    @Query("SELECT COUNT(*) FROM transactions WHERE type = 'UNKNOWN'")
    fun getUnparsedCount(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE type = 'UNKNOWN'")
    suspend fun getUnparsedSync(): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
