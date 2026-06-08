package com.ledga.app.data.repository

import com.ledga.app.data.db.dao.CategoryDao
import com.ledga.app.data.db.dao.CategoryRuleDao
import com.ledga.app.data.db.dao.CategorySpending
import com.ledga.app.data.db.dao.DailySpending
import com.ledga.app.data.db.dao.MonthlySpending
import com.ledga.app.data.db.dao.TopMerchant
import com.ledga.app.data.db.dao.TransactionDao
import com.ledga.app.data.db.entity.CategoryRule
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
    private val categoryDao: CategoryDao,
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
            fulizaLimit = parsed.fulizaLimit,
            reversedTransactionCode = parsed.reversedTransactionCode,
            rawSms = parsed.rawSms,
            timestamp = parsed.timestamp,
            accountId = accountId,
        )

        // M-PESA now splits a Fuliza-funded transaction into TWO SMS that
        // share one transaction code: the payment ("…sent to X…") and a
        // Fuliza companion ("…Total Fuliza M-PESA outstanding amount is…").
        // The unique index on transactionCode would otherwise drop the
        // second one — silently losing the outstanding/limit. Merge instead.
        val existing = transactionDao.getTransactionByCode(parsed.transactionCode)
        if (existing == null) {
            return transactionDao.insert(entity)
        }
        // Same code already stored. Return existing.id when we actually merged
        // new Fuliza data (so callers count it), -1 for a genuine duplicate.
        return if (mergeFulizaCompanion(existing, entity)) existing.id else -1L
    }

    /**
     * Reconcile two same-code SMS (payment + Fuliza companion) into one row.
     * The "companion" is a FULIZA-typed SMS with no recipient that carries
     * the outstanding/limit; the "payment" is the real spend with a recipient.
     * Works regardless of which arrived first. Returns true if a merge was
     * performed (new Fuliza data folded in), false for a genuine duplicate.
     */
    private suspend fun mergeFulizaCompanion(existing: TransactionEntity, incoming: TransactionEntity): Boolean {
        fun isCompanion(t: TransactionEntity) =
            t.type == TransactionType.FULIZA && t.recipientName == null &&
                (t.fulizaOutstanding != null || t.fulizaAmount != null)

        val incomingIsCompanion = isCompanion(incoming)
        val existingIsCompanion = isCompanion(existing)

        return when {
            // Companion arrived after the payment: enrich the payment row.
            incomingIsCompanion && !existingIsCompanion -> {
                // Nothing new to add if the payment already carries this.
                if (existing.fulizaOutstanding != null && existing.fulizaLimit != null) return false
                transactionDao.update(
                    existing.copy(
                        fulizaAmount = incoming.fulizaAmount ?: existing.fulizaAmount,
                        fulizaOutstanding = incoming.fulizaOutstanding ?: existing.fulizaOutstanding,
                        fulizaLimit = incoming.fulizaLimit ?: existing.fulizaLimit,
                    )
                )
                true
            }
            // Payment arrived after a companion: adopt the payment identity,
            // keep the companion's Fuliza facts + any user-set fields.
            !incomingIsCompanion && existingIsCompanion -> {
                transactionDao.update(
                    incoming.copy(
                        id = existing.id,
                        createdAt = existing.createdAt,
                        categoryId = existing.categoryId ?: incoming.categoryId,
                        accountId = existing.accountId ?: incoming.accountId,
                        note = existing.note,
                        fulizaAmount = existing.fulizaAmount ?: incoming.fulizaAmount,
                        fulizaOutstanding = existing.fulizaOutstanding ?: incoming.fulizaOutstanding,
                        fulizaLimit = existing.fulizaLimit ?: incoming.fulizaLimit,
                    )
                )
                true
            }
            // Otherwise a genuine duplicate — leave the stored row untouched.
            else -> false
        }
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

    fun getLargeTransactions(threshold: Double): Flow<List<TransactionWithCategory>> =
        selectedAccount.flatMapLatest { transactionDao.getLargeTransactions(threshold, it) }

    fun getPeopleByTypes(types: List<TransactionType>, query: String, minTotal: Double): Flow<List<TopMerchant>> =
        selectedAccount.flatMapLatest { transactionDao.getPeopleByTypes(types, query, minTotal, it) }

    fun getTransactionsForRecipient(name: String, types: List<TransactionType>): Flow<List<TransactionWithCategory>> =
        selectedAccount.flatMapLatest { transactionDao.getTransactionsForRecipient(name, types, it) }

    fun getDailySpending(startTime: Long, endTime: Long): Flow<List<DailySpending>> =
        selectedAccount.flatMapLatest { transactionDao.getDailySpending(startTime, endTime, it) }

    fun getMonthlySpending(limit: Int = 12): Flow<List<MonthlySpending>> =
        selectedAccount.flatMapLatest { transactionDao.getMonthlySpending(it, limit) }

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

    /**
     * Re-runs [MpesaSmsParser] over every stored transaction's rawSms and
     * updates parser-derived fields in place. User-managed fields
     * (categoryId, accountId, note, id, createdAt) are preserved. Use when a
     * parser fix has landed and you want existing rows to inherit it without
     * a destructive clear-and-reimport.
     */
    suspend fun reparseAllTransactions(): ReparseResult {
        val all = transactionDao.getAllSync()
        var fixed = 0
        var stillUnknown = 0
        for (entity in all) {
            val result = com.ledga.app.data.parser.MpesaSmsParser.parse(entity.rawSms, entity.timestamp)
            if (result !is com.ledga.app.data.parser.ParseResult.Success) {
                stillUnknown++
                continue
            }
            val p = result.transaction
            val updated = entity.copy(
                type = p.type,
                amount = p.amount,
                transactionCost = p.transactionCost,
                recipientName = p.recipientName,
                recipientPhone = p.recipientPhone,
                accountNumber = p.accountNumber,
                destinationCountry = p.destinationCountry,
                balance = p.balance,
                direction = p.direction,
                // Preserve Fuliza facts merged in from a same-code companion
                // SMS — the payment's own rawSms doesn't contain them, so a
                // naive reparse would wipe the outstanding/limit back to null.
                fulizaAmount = p.fulizaAmount ?: entity.fulizaAmount,
                fulizaOutstanding = p.fulizaOutstanding ?: entity.fulizaOutstanding,
                fulizaLimit = p.fulizaLimit ?: entity.fulizaLimit,
                reversedTransactionCode = p.reversedTransactionCode,
                // Deliberately keep id, categoryId, accountId, note, createdAt.
            )
            transactionDao.update(updated)
            fixed++
        }
        return ReparseResult(total = all.size, fixed = fixed, stillUnknown = stillUnknown)
    }

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

    // ---- Own-account (transfer) recipients ----

    /** Latest known available Fuliza limit / outstanding, from any SMS that carried them. */
    fun getLatestFulizaLimit(): Flow<TransactionEntity?> = transactionDao.getLatestFulizaLimit()
    fun getLatestFulizaOutstanding(): Flow<TransactionEntity?> = transactionDao.getLatestFulizaOutstanding()

    /** Recipient fragments the user marked as their own accounts. */
    fun getOwnAccountFragments(): Flow<List<String>> = categoryRuleDao.getOwnAccountFragments()

    /**
     * Mark a recipient as the user's own account: create a recipient rule
     * pointing at the transfer category and retroactively move every
     * matching transaction into it — excluding them from spending.
     *
     * The stored fragment strips a trailing "for account …" so rows that
     * embed per-transfer account numbers in the name still match
     * (e.g. "CREDIT BANK LTD INVESTMENTS 1 for account 07…").
     */
    suspend fun markRecipientAsOwnAccount(recipientName: String): Int {
        val transferCategory = categoryDao.getTransferCategory() ?: return 0
        val fragment = deriveRecipientFragment(recipientName)
        if (fragment.isBlank()) return 0
        categoryRuleDao.insertAll(
            listOf(
                CategoryRule(
                    categoryId = transferCategory.id,
                    matchType = MatchType.RECIPIENT_NAME,
                    matchValue = fragment,
                )
            )
        )
        return transactionDao.updateCategoryForRecipientFragment(fragment, transferCategory.id)
    }

    /** Undo [markRecipientAsOwnAccount]: drop the rule, re-categorize matches. */
    suspend fun unmarkRecipientAsOwnAccount(recipientName: String) {
        val transferCategory = categoryDao.getTransferCategory() ?: return
        val fragment = deriveRecipientFragment(recipientName)
        categoryRuleDao.deleteByCategoryAndValue(transferCategory.id, fragment)
        // Re-run auto-categorization for the affected rows so they fall back
        // to their type default or another matching rule.
        transactionDao.getByRecipientFragmentSync(fragment).forEach { entity ->
            if (entity.categoryId == transferCategory.id) {
                val categoryId = autoCategorize(entity.type, entity.recipientName, entity.accountNumber)
                transactionDao.updateCategory(entity.id, categoryId)
            }
        }
    }

    companion object {
        /**
         * The match fragment for an own-account rule. Strips the
         * "for account …" tail some bank-transfer SMS embed in the name.
         */
        fun deriveRecipientFragment(recipientName: String): String =
            recipientName
                .split(Regex("""\s+for account\s+""", RegexOption.IGNORE_CASE))
                .first()
                .trim()
    }

    private suspend fun autoCategorize(parsed: ParsedTransaction): Long? =
        autoCategorize(parsed.type, parsed.recipientName, parsed.accountNumber)

    private suspend fun autoCategorize(
        type: TransactionType,
        recipientName: String?,
        accountNumber: String?,
    ): Long? {
        // 1. Type-based defaults
        val typeDefault = when (type) {
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
        if (recipientName != null) {
            val rules = categoryRuleDao.getAllRulesSync()
            val nameUpper = recipientName.uppercase()

            // Check recipient name rules
            for (rule in rules) {
                if (rule.matchType == MatchType.RECIPIENT_NAME &&
                    nameUpper.contains(rule.matchValue.uppercase())
                ) {
                    return rule.categoryId
                }
            }

            // Check paybill rules
            if (accountNumber != null) {
                for (rule in rules) {
                    if (rule.matchType == MatchType.PAYBILL &&
                        accountNumber == rule.matchValue
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
