package com.ledga.app.data.derive

import com.ledga.app.data.room.dao.BiggestPayment
import com.ledga.app.data.room.dao.CategorySpend
import com.ledga.app.data.room.dao.SpentCount
import com.ledga.app.data.room.dao.CategoryMonthTotals
import com.ledga.app.data.trackers.CategoryMeasure
import com.ledga.core.time.PeriodType
import com.ledga.app.data.room.dao.PeriodSum
import com.ledga.app.data.room.FulizaReading
import com.ledga.app.data.room.BalanceReading
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
import java.time.Instant
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

    /**
     * Activity › Transactions (spec §7.5, R38): newest first, narrowed by [filter]. [today] turns a date filter into its
     * range (R69); it is needed only when there is one.
     */
    fun transactions(filter: TransactionFilter, today: LocalDate? = null): PagingSource<Int, TxRow> = Args(filter, today).run {
        db.transactionsDao().page(includeHidden, lineId, like, flow, anyCategory, categories, from, to, minCents, counterpartyKey)
    }

    /** Day header totals for [filter] by Nairobi day (R38, R45): the list's own filter, so they always agree. */
    fun dayTotals(filter: TransactionFilter, today: LocalDate? = null): Flow<Map<LocalDate, DayTotal>> = Args(filter, today).run {
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

    /** Each line's latest stated balance and its time (spec §7.5), live. */
    fun balances(): Flow<List<BalanceReading>> = db.transactionsDao().observeLatestBalances()

    /** Every Fuliza fact, oldest first, live (R58). */
    fun fulizaReadings(): Flow<List<FulizaReading>> = db.transactionsDao().observeFulizaReadings()

    /** The newest [limit] payments on [lineId] (null = all lines), hidden ones left out; or one category's. */
    fun recent(lineId: Long?, categoryKey: String? = null, limit: Int = RECENT): Flow<List<TxRow>> =
        db.transactionsDao().recent(lineId, categoryKey, limit)

    /** Spent per Nairobi week, month or year, keyed like `Period.key` (Home's spending card, R57). */
    fun spentByPeriod(type: PeriodType, range: InstantRange, lineId: Long? = null): Flow<Map<String, PeriodSum>> {
        val dao = db.ledgerDao()
        val rows: Flow<List<PeriodSum>> = when (type) {
            PeriodType.WEEK -> dao.spentByWeek(range.start, range.endExclusive, lineId)
            PeriodType.MONTH -> dao.spentByMonth(range.start, range.endExclusive, lineId).map { list -> list.map { PeriodSum(it.month, it.cents, it.count) } }
            PeriodType.YEAR -> dao.spentByYear(range.start, range.endExclusive, lineId)
            PeriodType.DAY -> error("a day's total is Activity's day header (dayTotals)")
        }
        return rows.map { list -> list.associateBy { it.period } }
    }

    /** Every measure per category per Nairobi month (4e §3.3; the trackers, a category's page, the Categories tab). */
    fun categoryMonthTotals(keys: List<String>, range: InstantRange, lineId: Long? = null): Flow<List<CategoryMonthTotals>> =
        db.ledgerDao().categoryMonthTotals(keys, range.start, range.endExclusive, lineId)

    /** D4: a category's biggest counterparties since [from]. */
    fun topPlaces(categoryKey: String, measure: CategoryMeasure, from: Instant, lineId: Long?, limit: Int = TOP_PLACES): Flow<List<PersonTotal>> =
        db.ledgerDao().topPlaces(categoryKey, measure.name, from, lineId, limit)

    /** A category's newest [limit] payments that counted. */
    fun latestSpends(categoryKey: String, lineId: Long?, limit: Int): Flow<List<CategorySpend>> =
        db.ledgerDao().latestSpends(categoryKey, lineId, limit)

    /** A summary's Spent and its count over a closed [range], every line (spec §11, R106). */
    suspend fun spentIn(range: InstantRange): SpentCount =
        db.ledgerDao().spentIn(range.start, checkNotNull(range.endExclusive) { "a summary's range is closed" })

    /** The payment that added most to Spent in a closed [range]; null when nothing did. */
    suspend fun biggest(range: InstantRange): BiggestPayment? =
        db.ledgerDao().biggest(range.start, checkNotNull(range.endExclusive) { "a summary's range is closed" })

    /** [TransactionFilter] as the shared SQL filter's arguments (`TX_FILTER`). */
    private class Args(f: TransactionFilter, today: LocalDate?) {
        val includeHidden = f.includeHidden
        val lineId = f.lineId
        val like = LedgerQueries.likePattern(f.query)
        val flow = f.flow.name
        val anyCategory = f.categoryKeys.isEmpty()
        val categories = f.categoryKeys.sorted()
        private val range = f.dates?.range(checkNotNull(today) { "a date filter is turned into a range against today (R69)" })
        val from = range?.start
        val to = range?.endExclusive
        val minCents = f.minAmountCents
        val counterpartyKey = f.counterpartyKey
    }

    companion object {
        /** Home's Recent and Tracker detail's payments (spec §10.4: "last 5"). */
        const val RECENT = 5

        /** D4: a category page's Top places. */
        const val TOP_PLACES = 5

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
