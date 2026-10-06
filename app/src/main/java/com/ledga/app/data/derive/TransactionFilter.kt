package com.ledga.app.data.derive

import com.ledga.core.time.InstantRange

/** Activity's flow chips (R38): the spending definition's own classes. Transfers show under [ALL]. */
enum class FlowFilter { ALL, OUT, IN, FULIZA }

/** A date filter and the name the filter sheet shows for it ("This month", "September 2026"). */
data class DateFilter(val label: String, val range: InstantRange)

/**
 * What the Transactions list shows (spec §10.4): the search, the chips, the filter sheet, and the person sheet's
 * [counterpartyKey]. The list and its day totals read the same filter (`TX_FILTER`), so a header always sums the rows
 * under it. [query] is as typed; `LedgerQueries.likePattern` normalises and escapes it.
 */
data class TransactionFilter(
    val query: String = "",
    val flow: FlowFilter = FlowFilter.ALL,
    val lineId: Long? = null,
    val categoryKeys: Set<String> = emptySet(),
    val dates: DateFilter? = null,
    val minAmountCents: Long? = null,
    val includeHidden: Boolean = false,
    val counterpartyKey: String? = null,
) {
    /** The filter sheet's filters that are on: the badge on Activity's filter button. */
    val sheetCount: Int get() = listOf(categoryKeys.isNotEmpty(), dates != null, minAmountCents != null, includeHidden).count { it }

    /** Nothing narrows the list. */
    val isEverything: Boolean get() = this == TransactionFilter()
}
