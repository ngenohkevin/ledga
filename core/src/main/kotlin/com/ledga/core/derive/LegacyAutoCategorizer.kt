package com.ledga.core.derive

import com.ledga.core.model.Categories
import com.ledga.core.model.CategoryGroup

/**
 * Re-implementation of v1.6's auto-categoriser (app TransactionRepository.autoCategorize +
 * DefaultData), used once by the Phase 2 migration to tell user choices apart from what v1
 * assigned on its own. Deliberately bug-compatible: plain substring matching, id order,
 * paybill rules compared against the customer account number.
 */
object LegacyAutoCategorizer {
    data class LegacyRule(val id: Long, val categoryId: Long, val matchType: String, val matchValue: String)

    const val BILLS = 3L
    const val OTHER = 13L
    const val MY_ACCOUNTS = 14L

    private fun name(id: Long, category: Long, value: String) = LegacyRule(id, category, "RECIPIENT_NAME", value)
    private fun paybill(id: Long, category: Long, value: String) = LegacyRule(id, category, "PAYBILL", value)

    val DEFAULT_RULES: List<LegacyRule> = listOf(
        name(1, 1, "NAIVAS"), name(2, 1, "QUICKMART"), name(3, 1, "CARREFOUR"), name(4, 1, "CLEANSHELF"),
        name(5, 2, "UBER"), name(6, 2, "BOLT"), name(7, 2, "LITTLE"),
        name(8, 3, "KPLC"), name(9, 3, "KENYA POWER"), paybill(10, 3, "888880"), paybill(11, 3, "888888"),
        name(12, 3, "NAIROBI WATER"), paybill(13, 3, "444400"), name(14, 3, "DSTV"), name(15, 3, "GOTV"),
        name(16, 3, "SHOWMAX"),
        name(17, 5, "JAVA"), name(18, 5, "KFC"), name(19, 5, "CHICKEN INN"), name(20, 5, "PIZZA INN"),
    )

    fun typeDefault(type: String): Long? = when (type) {
        "AIRTIME_SELF", "AIRTIME_OTHER" -> 4L
        "SEND" -> 6L
        "RECEIVED" -> 7L
        "WITHDRAW_AGENT", "WITHDRAW_ATM" -> 8L
        "DEPOSIT" -> 9L
        "MPESA_GLOBAL" -> 11L
        "MSHWARI", "KCB_MPESA", "FULIZA", "FULIZA_REPAYMENT", "FULIZA_REVERSAL", "FULIZA_AUTO_PAY" -> 12L
        "REVERSAL", "UNKNOWN" -> 13L
        else -> null
    }

    fun categorize(type: String, recipientName: String?, accountNumber: String?, rules: List<LegacyRule>): Long {
        if (recipientName != null) {
            val ordered = rules.sortedBy { it.id }
            val upper = recipientName.uppercase()
            ordered.firstOrNull { it.matchType == "RECIPIENT_NAME" && upper.contains(it.matchValue.uppercase()) }
                ?.let { return it.categoryId }
            if (accountNumber != null) {
                ordered.firstOrNull { it.matchType == "PAYBILL" && accountNumber == it.matchValue }
                    ?.let { return it.categoryId }
            }
        }
        return typeDefault(type) ?: OTHER
    }

    /**
     * True only when the stored category is something v1 could not have produced by itself:
     * not its auto result, not the type default (a rule began matching after a v1 re-parse),
     * and not Other (the UNKNOWN default, kept when v1 later re-parsed the row). Refinement R1.
     */
    fun isUserChoice(
        storedCategoryId: Long?,
        type: String,
        recipientName: String?,
        accountNumber: String?,
        rules: List<LegacyRule>,
    ): Boolean {
        if (storedCategoryId == null) return false
        if (storedCategoryId == categorize(type, recipientName, accountNumber, rules)) return false
        if (storedCategoryId == typeDefault(type)) return false
        return storedCategoryId != OTHER
    }
}

sealed interface LegacyMapping {
    data class ToCategory(val key: String) : LegacyMapping
    data object OwnAccount : LegacyMapping
    /** A user-created v1 category; Phase 2 creates a USER category with the same name. */
    data class Custom(val legacyId: Long) : LegacyMapping
}

object LegacyCategoryMap {
    fun map(legacyId: Long): LegacyMapping = when (legacyId) {
        1L -> LegacyMapping.ToCategory(Categories.GROCERIES)
        2L -> LegacyMapping.ToCategory(Categories.TRANSPORT)
        LegacyAutoCategorizer.BILLS -> LegacyMapping.ToCategory(Categories.OTHER)
        4L -> LegacyMapping.ToCategory(Categories.AIRTIME_DATA)
        5L -> LegacyMapping.ToCategory(Categories.FOOD)
        6L -> LegacyMapping.ToCategory(Categories.SENT_TO_PEOPLE)
        7L -> LegacyMapping.ToCategory(Categories.RECEIVED)
        8L -> LegacyMapping.ToCategory(Categories.CASH_WITHDRAWAL)
        9L -> LegacyMapping.ToCategory(Categories.CASH_DEPOSIT)
        10L -> LegacyMapping.ToCategory(Categories.SHOPPING)
        11L -> LegacyMapping.ToCategory(Categories.INTERNATIONAL)
        12L -> LegacyMapping.ToCategory(Categories.SAVINGS)
        LegacyAutoCategorizer.OTHER -> LegacyMapping.ToCategory(Categories.OTHER)
        LegacyAutoCategorizer.MY_ACCOUNTS -> LegacyMapping.OwnAccount
        else -> LegacyMapping.Custom(legacyId)
    }

    /**
     * The override to write for a legacy user choice, or null when none should be written:
     * a v1 "Bills & Utilities" choice yields to any v2 rule whose category is in the
     * [CategoryGroup.BILLS_UTILITIES] group (electricity, water, internet, tv, rent): spec §8 widened to its intent.
     */
    fun overrideFor(legacyId: Long, v2RuleCategory: String?): LegacyMapping? =
        if (legacyId == LegacyAutoCategorizer.BILLS &&
            v2RuleCategory != null && Categories.seed(v2RuleCategory)?.group == CategoryGroup.BILLS_UTILITIES
        ) {
            null
        } else {
            map(legacyId)
        }

    /** v1 carTag → v2 category key. */
    fun carTag(tag: String): String? = when (tag) {
        "FUEL" -> Categories.FUEL
        "SERVICE" -> Categories.CAR_SERVICE
        else -> null
    }
}
