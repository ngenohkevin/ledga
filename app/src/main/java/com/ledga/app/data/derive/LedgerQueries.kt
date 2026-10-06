package com.ledga.app.data.derive

import androidx.paging.PagingSource
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.LineBalance
import com.ledga.app.data.room.TxRow
import com.ledga.app.data.room.dao.CategoryTotal
import com.ledga.app.data.room.dao.DayTotal
import com.ledga.app.data.room.dao.MonthTotal
import com.ledga.app.data.room.dao.PeriodTotals
import com.ledga.app.data.room.dao.PersonSummary
import com.ledga.app.data.room.dao.PersonTotal
import com.ledga.core.derive.SearchText
import com.ledga.core.money.Money
import com.ledga.core.time.InstantRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** The read side screens use (spec §7.5). Ranges are `:core` InstantRanges; an open range (live period) has a null end. */
class LedgerQueries(private val db: LedgaDatabase) {

    fun spent(range: InstantRange, lineId: Long? = null): Flow<Money> =
        db.ledgerDao().spent(range.start, range.endExclusive, lineId).map(::Money)

    fun moneyIn(range: InstantRange, lineId: Long? = null): Flow<Money> =
        db.ledgerDao().moneyIn(range.start, range.endExclusive, lineId).map(::Money)

    fun spentByCategory(range: InstantRange, lineId: Long? = null): Flow<List<CategoryTotal>> =
        db.ledgerDao().spentByCategory(range.start, range.endExclusive, lineId)

    suspend fun combinedBalance(): Money? = combine(db.transactionsDao().latestBalances())

    /** Activity › Transactions (spec §7.5, R38): newest first, narrowed by [filter]. */
    fun transactions(filter: TransactionFilter): PagingSource<Int, TxRow> = Args(filter).run {
        db.transactionsDao().page(includeHidden, lineId, like, flow, anyCategory, categories, from, to, minCents, counterpartyKey)
    }

    /** Day header totals for [filter] by Nairobi day (R38, R45): the list's own filter, so they always agree. */
    fun dayTotals(filter: TransactionFilter): Flow<Map<LocalDate, DayTotal>> = Args(filter).run {
        db.ledgerDao().dayTotals(includeHidden, lineId, like, flow, anyCategory, categories, from, to, minCents, counterpartyKey)
    }.map { rows -> rows.associateBy { LocalDate.ofEpochDay(it.day) } }

    /** People (R42): everyone you sent to, or received from, biggest total first; on one line (R47) or all. */
    fun people(direction: PeopleDirection, lineId: Long? = null): Flow<List<PersonTotal>> =
        db.ledgerDao().people(direction.flow.name, direction.kinds.map { it.name }, lineId)

    fun personSummary(counterpartyKey: String, lineId: Long? = null): Flow<PersonSummary> = db.ledgerDao().personSummary(counterpartyKey, lineId)

    /** Spending's chart: spent per Nairobi month, keyed "2026-09" like `Period.key` (R45). */
    fun spentByMonth(range: InstantRange, lineId: Long? = null): Flow<Map<String, MonthTotal>> =
        db.ledgerDao().spentByMonth(range.start, range.endExclusive, lineId).map { rows -> rows.associateBy { it.month } }

    /** Spending's card: spent (including fees), the fees on their own, and money in. */
    fun totals(range: InstantRange, lineId: Long? = null): Flow<PeriodTotals> =
        db.ledgerDao().totals(range.start, range.endExclusive, lineId)

    /** [TransactionFilter] as the shared SQL filter's arguments (`TX_FILTER`). */
    private class Args(f: TransactionFilter) {
        val includeHidden = f.includeHidden
        val lineId = f.lineId
        val like = LedgerQueries.likePattern(f.query)
        val flow = f.flow.name
        val anyCategory = f.categoryKeys.isEmpty()
        val categories = f.categoryKeys.sorted()
        val from = f.dates?.range?.start
        val to = f.dates?.range?.endExclusive
        val minCents = f.minAmountCents
        val counterpartyKey = f.counterpartyKey
    }

    companion object {
        /** Σ of each line's latest balance; with no line attribution at all, the latest overall (§7.5). */
        fun combine(latest: List<LineBalance>): Money? {
            val attributed = latest.filter { it.lineId != null }
            val pick = attributed.ifEmpty { latest }
            return if (pick.isEmpty()) null else Money(pick.sumOf { it.balanceCents })
        }

        /** Normalised like `searchText`, with LIKE's wildcards escaped; null for a blank query. */
        fun likePattern(query: String): String? {
            val q = SearchText.normalizeQuery(query).takeIf { it.isNotEmpty() } ?: return null
            val escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            return "%$escaped%"
        }
    }
}
