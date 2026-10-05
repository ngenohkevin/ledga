package com.ledga.app.testing

import androidx.sqlite.db.SupportSQLiteDatabase
import com.ledga.core.derive.LegacyAutoCategorizer
import java.time.Instant

/** Inserts v1-era rows using only the columns schema [version] had (synthetic data). */
class LegacyDbWriter(private val db: SupportSQLiteDatabase, private val version: Int) {
    private fun b(v: Boolean) = if (v) 1 else 0

    /** v1 DefaultData ids 1..13, plus 14 "My Accounts" from schema 4 on. */
    fun defaultCategories() {
        listOf(
            "Groceries", "Transport", "Bills & Utilities", "Airtime & Data", "Food & Dining", "Send Money", "Received",
            "Withdrawal", "Deposit", "Shopping", "International", "Savings & Loans", "Other",
        ).forEachIndexed { i, name -> category(i + 1L, name) }
        if (version >= 4) category(14, "My Accounts", transfer = true)
    }

    fun category(id: Long, name: String, isDefault: Boolean = true, transfer: Boolean = false) {
        if (version >= 4) {
            db.execSQL(
                "INSERT INTO categories (id, name, icon, color, isDefault, isTransfer) VALUES (?, ?, 'category', '#9E9E9E', ?, ?)",
                arrayOf<Any?>(id, name, b(isDefault), b(transfer)),
            )
        } else {
            db.execSQL("INSERT INTO categories (id, name, icon, color, isDefault) VALUES (?, ?, 'category', '#9E9E9E', ?)", arrayOf<Any?>(id, name, b(isDefault)))
        }
    }

    fun defaultRules() = LegacyAutoCategorizer.DEFAULT_RULES.forEach { rule(it.id, it.categoryId, it.matchType, it.matchValue) }

    fun rule(id: Long, categoryId: Long, matchType: String, matchValue: String) =
        db.execSQL("INSERT INTO category_rules (id, categoryId, matchType, matchValue) VALUES (?, ?, ?, ?)", arrayOf<Any?>(id, categoryId, matchType, matchValue))

    fun account(id: Long, subscriptionId: Int, name: String) {
        require(version >= 2) { "mpesa_accounts arrived in schema 2" }
        db.execSQL(
            "INSERT INTO mpesa_accounts (id, subscriptionId, phoneNumber, displayName, colorHex, isPrimary, createdAt) VALUES (?, ?, NULL, ?, '#00A86B', 0, 1767225600000)",
            arrayOf<Any?>(id, subscriptionId, name),
        )
    }

    fun goalAndContribution() {
        require(version >= 2)
        db.execSQL("INSERT INTO goals (id, name, targetAmount, targetDate, contributionRule, colorHex, createdAt, completedAt) VALUES (1, 'Sample goal', 1000.0, NULL, 'MANUAL', '#00A86B', 0, NULL)")
        db.execSQL("INSERT INTO goal_contributions (goalId, transactionId, markedAt) VALUES (1, 1, 0)")
    }

    fun insight() {
        require(version >= 3)
        db.execSQL("INSERT INTO insights (naturalKey, type, severity, typeLabel, headline, generatedAt) VALUES ('k1', 'FEE_TIP', 'INFO', 'Tip', 'Sample', 0)")
    }

    fun budget() = db.execSQL("INSERT INTO budgets (categoryId, monthlyLimit, isActive) VALUES (5, 3000.0, 1)")

    /** One v1 `transactions` row. Amounts are irrelevant to the migration (v2 re-parses rawSms), so they are 0. */
    fun tx(
        id: Long,
        code: String,
        type: String,
        direction: String,
        rawSms: String,
        timestamp: Instant,
        categoryId: Long?,
        recipientName: String? = null,
        accountNumber: String? = null,
        accountId: Long? = null,
        note: String? = null,
        carTag: String? = null,
    ) {
        val cols = mutableListOf(
            "id", "transactionCode", "type", "amount", "transactionCost", "recipientName", "recipientPhone", "accountNumber",
            "destinationCountry", "balance", "direction", "categoryId", "fulizaAmount", "fulizaOutstanding",
            "reversedTransactionCode", "rawSms", "timestamp", "createdAt",
        )
        val vals = mutableListOf<Any?>(
            id, code, type, 0.0, 0.0, recipientName, null, accountNumber, null, 0.0, direction, categoryId, null, null,
            null, rawSms, timestamp.toEpochMilli(), timestamp.toEpochMilli(),
        )
        if (version >= 2) { cols.addAll(listOf("accountId", "note")); vals.addAll(listOf(accountId, note)) }
        if (version >= 4) { cols.add("fulizaLimit"); vals.add(null) }
        if (version >= 5) { cols.add("carTag"); vals.add(carTag) }
        db.execSQL(
            "INSERT INTO transactions (${cols.joinToString()}) VALUES (${cols.joinToString { "?" }})",
            vals.toTypedArray(),
        )
    }
}
