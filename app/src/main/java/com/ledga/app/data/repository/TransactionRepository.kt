package com.ledga.app.data.repository

import com.ledga.app.data.db.dao.CategoryRuleDao
import com.ledga.app.data.db.dao.CategorySpending
import com.ledga.app.data.db.dao.DailySpending
import com.ledga.app.data.db.dao.TopMerchant
import com.ledga.app.data.db.dao.TransactionDao
import com.ledga.app.data.db.entity.MatchType
import com.ledga.app.data.db.entity.TransactionEntity
import com.ledga.app.data.db.entity.TransactionWithCategory
import com.ledga.app.data.parser.ParsedTransaction
import com.ledga.app.data.parser.TransactionType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryRuleDao: CategoryRuleDao
) {
    suspend fun insertTransaction(parsed: ParsedTransaction): Long {
        val categoryId = autoCategorize(parsed)
        val entity = TransactionEntity(
            transactionCode = parsed.transactionCode,
            type = parsed.type,
            amount = parsed.amount,
            transactionCost = parsed.transactionCost,
            recipientName = parsed.recipientName,
            recipientPhone = parsed.recipientPhone,
            accountNumber = parsed.accountNumber,
            destinationCountry = parsed.destinationCountry,
            balance = parsed.balance,
            direction = parsed.direction,
            categoryId = categoryId,
            fulizaAmount = parsed.fulizaAmount,
            fulizaOutstanding = parsed.fulizaOutstanding,
            reversedTransactionCode = parsed.reversedTransactionCode,
            rawSms = parsed.rawSms,
            timestamp = parsed.timestamp
        )
        return transactionDao.insert(entity)
    }

    fun getRecentTransactions(limit: Int = 10): Flow<List<TransactionWithCategory>> =
        transactionDao.getRecentTransactions(limit)

    fun getTransactions(startTime: Long, endTime: Long): Flow<List<TransactionWithCategory>> =
        transactionDao.getTransactions(startTime, endTime)

    fun getLatestTransaction(): Flow<TransactionEntity?> =
        transactionDao.getLatestTransaction()

    fun getLatestTransactionWithBalance(): Flow<TransactionEntity?> =
        transactionDao.getLatestTransactionWithBalance()

    fun getSpendingByCategory(startTime: Long, endTime: Long): Flow<List<CategorySpending>> =
        transactionDao.getSpendingByCategory(startTime, endTime)

    fun getTotalSpending(startTime: Long, endTime: Long): Flow<Double> =
        transactionDao.getTotalSpending(startTime, endTime)

    fun getTotalFees(startTime: Long, endTime: Long): Flow<Double> =
        transactionDao.getTotalFees(startTime, endTime)

    fun searchTransactions(query: String): Flow<List<TransactionWithCategory>> =
        transactionDao.searchTransactions(query)

    fun getTransactionsByType(types: List<TransactionType>): Flow<List<TransactionWithCategory>> =
        transactionDao.getTransactionsByType(types)

    fun getDailySpending(startTime: Long, endTime: Long): Flow<List<DailySpending>> =
        transactionDao.getDailySpending(startTime, endTime)

    fun getTopMerchants(startTime: Long, endTime: Long, limit: Int = 5): Flow<List<TopMerchant>> =
        transactionDao.getTopMerchants(startTime, endTime, limit)

    fun getUnparsedTransactions(): Flow<List<TransactionWithCategory>> =
        transactionDao.getUnparsedTransactions()

    fun getUnparsedCount(): Flow<Int> =
        transactionDao.getUnparsedCount()

    suspend fun updateCategory(transactionId: Long, categoryId: Long?) {
        transactionDao.updateCategory(transactionId, categoryId)
    }

    private suspend fun autoCategorize(parsed: ParsedTransaction): Long? {
        // 1. Type-based defaults
        val typeDefault = when (parsed.type) {
            TransactionType.AIRTIME_SELF, TransactionType.AIRTIME_OTHER -> 4L // Airtime & Data
            TransactionType.SEND -> 6L // Send Money
            TransactionType.RECEIVED -> 7L // Received
            TransactionType.WITHDRAW_AGENT, TransactionType.WITHDRAW_ATM -> 8L // Withdrawal
            TransactionType.DEPOSIT -> 9L // Deposit
            TransactionType.MPESA_GLOBAL -> 11L // International
            TransactionType.MSHWARI, TransactionType.KCB_MPESA -> 12L // Savings & Loans
            TransactionType.FULIZA, TransactionType.FULIZA_REPAYMENT, TransactionType.FULIZA_REVERSAL, TransactionType.FULIZA_AUTO_PAY -> 12L
            TransactionType.REVERSAL -> 13L // Other
            TransactionType.UNKNOWN -> 13L
            else -> null
        }

        // 2. Rule-based matching (overrides type default for BUY_GOODS, PAY_BILL, SEND)
        if (parsed.recipientName != null) {
            val rules = categoryRuleDao.getAllRulesSync()
            val nameUpper = parsed.recipientName.uppercase()

            // Check recipient name rules
            for (rule in rules) {
                if (rule.matchType == MatchType.RECIPIENT_NAME &&
                    nameUpper.contains(rule.matchValue.uppercase())
                ) {
                    return rule.categoryId
                }
            }

            // Check paybill rules
            if (parsed.accountNumber != null) {
                for (rule in rules) {
                    if (rule.matchType == MatchType.PAYBILL &&
                        parsed.accountNumber == rule.matchValue
                    ) {
                        return rule.categoryId
                    }
                }
            }
        }

        return typeDefault ?: 13L // Default to "Other"
    }
}
