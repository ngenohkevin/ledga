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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryRuleDao: CategoryRuleDao,
    private val settingsRepository: SettingsRepository,
) {
    /**
     * Re-emit whenever the selected account changes. Every screen-facing
     * query in this repository composes with this so the UI scope updates
     * automatically when the user switches lines.
     */
    private val selectedAccount: Flow<Long?> get() = settingsRepository.getSelectedAccountId()
    suspend fun insertTransaction(parsed: ParsedTransaction, accountId: Long? = null): Long {
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
            timestamp = parsed.timestamp,
            accountId = accountId,
        )
        return transactionDao.insert(entity)
    }

    fun getRecentTransactions(limit: Int = 10): Flow<List<TransactionWithCategory>> =
        selectedAccount.flatMapLatest { transactionDao.getRecentTransactions(limit, it) }

    fun getTransactions(startTime: Long, endTime: Long): Flow<List<TransactionWithCategory>> =
        selectedAccount.flatMapLatest { transactionDao.getTransactions(startTime, endTime, it) }

    fun getLatestTransaction(): Flow<TransactionEntity?> =
        selectedAccount.flatMapLatest { transactionDao.getLatestTransaction(it) }

    fun getLatestTransactionWithBalance(): Flow<TransactionEntity?> =
        selectedAccount.flatMapLatest { transactionDao.getLatestTransactionWithBalance(it) }

    fun getSpendingByCategory(startTime: Long, endTime: Long): Flow<List<CategorySpending>> =
        selectedAccount.flatMapLatest { transactionDao.getSpendingByCategory(startTime, endTime, it) }

    fun getTotalSpending(startTime: Long, endTime: Long): Flow<Double> =
        selectedAccount.flatMapLatest { transactionDao.getTotalSpending(startTime, endTime, it) }

    fun getTotalFees(startTime: Long, endTime: Long): Flow<Double> =
        selectedAccount.flatMapLatest { transactionDao.getTotalFees(startTime, endTime, it) }

    fun searchTransactions(query: String): Flow<List<TransactionWithCategory>> =
        selectedAccount.flatMapLatest { transactionDao.searchTransactions(query, it) }

    fun getTransactionsByType(types: List<TransactionType>): Flow<List<TransactionWithCategory>> =
        selectedAccount.flatMapLatest { transactionDao.getTransactionsByType(types, it) }

    fun getDailySpending(startTime: Long, endTime: Long): Flow<List<DailySpending>> =
        selectedAccount.flatMapLatest { transactionDao.getDailySpending(startTime, endTime, it) }

    fun getTopMerchants(startTime: Long, endTime: Long, limit: Int = 5): Flow<List<TopMerchant>> =
        selectedAccount.flatMapLatest { transactionDao.getTopMerchants(startTime, endTime, it, limit) }

    fun getUnparsedTransactions(): Flow<List<TransactionWithCategory>> =
        transactionDao.getUnparsedTransactions()

    fun getUnparsedCount(): Flow<Int> =
        transactionDao.getUnparsedCount()

    suspend fun updateCategory(transactionId: Long, categoryId: Long?) {
        transactionDao.updateCategory(transactionId, categoryId)
    }

    /** Used by the per-transaction backfill UI in [TransactionDetailSheet]. */
    suspend fun updateAccount(transactionId: Long, accountId: Long?) {
        transactionDao.updateAccount(transactionId, accountId)
    }

    /** Bulk date-range backfill. Returns the number of rows updated. */
    suspend fun bulkAttribute(accountId: Long?, startTime: Long, endTime: Long): Int =
        transactionDao.updateAccountForRange(accountId, startTime, endTime)

    /** Bulk attribute by raw transaction-code list (used by SMS-DB backfill). */
    suspend fun bulkAttributeByCodes(accountId: Long, codes: List<String>): Int =
        if (codes.isEmpty()) 0 else transactionDao.updateAccountForCodes(accountId, codes)

    suspend fun reparseUnknownTransactions(): ReparseResult {
        val unknowns = transactionDao.getUnparsedSync()
        var fixed = 0
        var stillUnknown = 0

        for (entity in unknowns) {
            val result = com.ledga.app.data.parser.MpesaSmsParser.parse(entity.rawSms, entity.timestamp)
            when (result) {
                is com.ledga.app.data.parser.ParseResult.Success -> {
                    if (result.transaction.type != com.ledga.app.data.parser.TransactionType.UNKNOWN) {
                        // Delete old UNKNOWN entry, insert re-parsed one — carry the
                        // original accountId forward so re-parsing never loses
                        // hard-won multi-SIM attribution.
                        transactionDao.deleteById(entity.id)
                        insertTransaction(result.transaction, accountId = entity.accountId)
                        fixed++
                    } else {
                        stillUnknown++
                    }
                }
                is com.ledga.app.data.parser.ParseResult.Failure -> {
                    // Balance checks etc get filtered — delete the UNKNOWN entry
                    transactionDao.deleteById(entity.id)
                    fixed++
                }
            }
        }

        return ReparseResult(total = unknowns.size, fixed = fixed, stillUnknown = stillUnknown)
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

data class ReparseResult(val total: Int, val fixed: Int, val stillUnknown: Int)
