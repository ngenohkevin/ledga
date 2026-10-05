package com.ledga.core.derive

import com.ledga.core.money.Money
import com.ledga.core.parse.Counterparty

/**
 * The lower-cased haystack Phase 2 stores in `transactions.searchText`, queried with
 * `searchText LIKE '%' || normalizeQuery(q) || '%'`.
 */
object SearchText {
    private val WS = Regex("\\s+")

    fun build(code: String, counterparty: Counterparty?, note: String?, amount: Money): String {
        val phone = counterparty?.phone
        val parts = listOfNotNull(
            counterparty?.name,
            phone,
            phone?.filter { it.isDigit() },
            code,
            counterparty?.accountRef,
            counterparty?.businessNumber,
            note,
        ) + amount.searchForms()
        return normalizeQuery(parts.joinToString(" "))
    }

    fun normalizeQuery(q: String): String = q.replace(WS, " ").trim().lowercase()
}
