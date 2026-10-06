package com.ledga.app.data.derive

import com.ledga.core.time.InstantRange
import com.ledga.core.time.Nairobi
import com.ledga.core.time.Period
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
import java.time.LocalDate
import java.time.YearMonth

/** Activity's flow chips (R38): the spending definition's own classes. Transfers show under [ALL]. */
enum class FlowFilter { ALL, OUT, IN, FULIZA }

/**
 * A date filter as the person chose it (R69). It stays a choice, not a range: each load turns it into a range against
 * today (Nairobi), so "This month" moves on at midnight instead of keeping the month it was applied in.
 */
sealed interface DateFilter {
    fun range(today: LocalDate): InstantRange

    data class Preset(val preset: DatePreset) : DateFilter {
        override fun range(today: LocalDate): InstantRange = preset.range(today)
    }

    /** One calendar month (Spending's "tap → filtered transactions"): open while it is the current month (spec §7.6). */
    data class Month(val month: YearMonth) : DateFilter {
        override fun range(today: LocalDate): InstantRange {
            val period = Period(PeriodType.MONTH, month.atDay(1))
            return if (YearMonth.from(today) == month) InstantRange(period.startInstant, null) else period.range()
        }
    }

    /** R70: whole Nairobi days, [from] to [to], both included. */
    data class Custom(val from: LocalDate, val to: LocalDate) : DateFilter {
        init {
            require(!to.isBefore(from)) { "a custom range ends before it starts" }
        }

        override fun range(today: LocalDate): InstantRange =
            InstantRange(from.atStartOfDay(Nairobi.ZONE).toInstant(), to.plusDays(1).atStartOfDay(Nairobi.ZONE).toInstant())

        companion object {
            /** Either order: a "To" picked before the "From" swaps them. */
            fun of(a: LocalDate, b: LocalDate): Custom = if (b.isBefore(a)) Custom(b, a) else Custom(a, b)
        }
    }
}

/** The filter sheet's date presets (R39). "Last 3 months" is this month and the two before it. */
enum class DatePreset(val label: String) {
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    LAST_3_MONTHS("Last 3 months"),
    THIS_YEAR("This year"),
    ;

    fun range(today: LocalDate): InstantRange {
        val month = Period(PeriodType.MONTH, Periods.startOf(PeriodType.MONTH, today))
        return when (this) {
            THIS_MONTH -> InstantRange(month.startInstant, null)
            LAST_MONTH -> month.previous().range()
            LAST_3_MONTHS -> InstantRange(month.previous().previous().startInstant, null)
            THIS_YEAR -> InstantRange(Period(PeriodType.YEAR, Periods.startOf(PeriodType.YEAR, today)).startInstant, null)
        }
    }
}

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
