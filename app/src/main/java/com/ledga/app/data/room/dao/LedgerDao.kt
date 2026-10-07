package com.ledga.app.data.room.dao

import com.ledga.app.data.trackers.CategoryMeasure

import androidx.room.Dao
import androidx.room.Query
import com.ledga.app.data.room.LedgerRow
import com.ledga.core.model.TxKind
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class CategoryTotal(val categoryKey: String, val cents: Long, val count: Int)

/** One Nairobi day's header totals (R38): Out = spent including fees, In = money in. [day] is the epoch day. */
data class DayTotal(val day: Long, val outCents: Long, val inCents: Long)

/** One person in People (R42), with the name and phone from their latest payment. */
data class PersonTotal(
    val counterpartyKey: String,
    val name: String?,
    val phone: String?,
    val count: Int,
    val totalCents: Long,
    val lastAt: Instant,
)

/** The person sheet's two totals. */
data class PersonSummary(val sentCents: Long, val sentCount: Int, val receivedCents: Long, val receivedCount: Int)

/** Spent in one Nairobi month; [month] reads "2026-09", like `Period.key` (R45). */
data class MonthTotal(val month: String, val cents: Long, val count: Int)

/** A period's spent (including fees), its fees on their own, and money in. */
data class PeriodTotals(val spentCents: Long, val feeCents: Long, val inCents: Long)

/** Spent in one category in one Nairobi month ([month] reads "2026-09"): the trackers (R52). */
data class CategoryMonthTotal(val categoryKey: String, val month: String, val cents: Long, val count: Int)

/** A category's Nairobi month in all three measures (4e §3.3); `pick` takes the category's own. */
data class CategoryMonthTotals(
    val categoryKey: String,
    val month: String,
    val spentCents: Long,
    val spentCount: Int,
    val receivedCents: Long,
    val receivedCount: Int,
    val movedCents: Long,
    val movedCount: Int,
) {
    fun pick(measure: CategoryMeasure): CategoryMonthTotal = when (measure) {
        CategoryMeasure.SPENT -> CategoryMonthTotal(categoryKey, month, spentCents, spentCount)
        CategoryMeasure.RECEIVED -> CategoryMonthTotal(categoryKey, month, receivedCents, receivedCount)
        CategoryMeasure.MOVED -> CategoryMonthTotal(categoryKey, month, movedCents, movedCount)
    }
}

/** One payment that counts in a category: when, what it counted (amount and fees) and who was paid. */
data class CategorySpend(val code: String, val categoryKey: String, val occurredAt: Instant, val cents: Long, val name: String?)

/** Spent in one period: [period] reads like `Period.key`: "2026-09-28" (a week, by its Monday) or "2026" (a year). */
data class PeriodSum(val period: String, val cents: Long, val count: Int)

/** A summary's total over a range and the payments that added to it (spec §11; R106 counts like Spending's chart). */
data class SpentCount(val cents: Long, val count: Int)

/** The payment that added most to Spent in a range: a summary's "biggest" (spec §11). */
data class BiggestPayment(val code: String, val kind: TxKind, val name: String?, val cents: Long)

/** Every aggregate reads the `ledger` view: the spending definition exists once. [to] null = a live, open-ended range. */
@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger")
    suspend fun all(): List<LedgerRow>

    @Query(
        "SELECT COALESCE(SUM(spendCents + feeCents), 0) FROM ledger " +
            "WHERE occurredAt >= :from AND (:to IS NULL OR occurredAt < :to) AND (:lineId IS NULL OR lineId = :lineId)",
    )
    fun spent(from: Instant, to: Instant?, lineId: Long?): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(inCents), 0) FROM ledger " +
            "WHERE occurredAt >= :from AND (:to IS NULL OR occurredAt < :to) AND (:lineId IS NULL OR lineId = :lineId)",
    )
    fun moneyIn(from: Instant, to: Instant?, lineId: Long?): Flow<Long>

    @Query(
        "SELECT categoryKey, SUM(spendCents + feeCents) AS cents, COUNT(*) AS count FROM ledger " +
            "WHERE occurredAt >= :from AND (:to IS NULL OR occurredAt < :to) AND (:lineId IS NULL OR lineId = :lineId) " +
            "AND spendCents + feeCents > 0 GROUP BY categoryKey ORDER BY cents DESC, categoryKey",
    )
    fun spentByCategory(from: Instant, to: Instant?, lineId: Long?): Flow<List<CategoryTotal>>

    /** Day headers (R38): the list's own filter (`TX_FILTER`, same parameters as `TransactionsDao.page`). */
    @Query(
        "SELECT (t.occurredAt + " + NAIROBI_OFFSET_MS + ") / 86400000 AS day, " +
            "SUM(l.spendCents + l.feeCents) AS outCents, SUM(l.inCents) AS inCents " +
            "FROM ledger l JOIN transactions t ON t.code = l.code WHERE " + TX_FILTER + " GROUP BY day",
    )
    fun dayTotals(
        includeHidden: Boolean,
        lineId: Long?,
        like: String?,
        flow: String,
        anyCategory: Boolean,
        categories: List<String>,
        from: Instant?,
        to: Instant?,
        minCents: Long?,
        counterpartyKey: String?,
    ): Flow<List<DayTotal>>

    /** People (R42): [flow] and [kinds] by name; reversed payments excluded; biggest total first. */
    @Query(
        "SELECT t.counterpartyKey AS counterpartyKey, " +
            "(SELECT n.counterpartyName FROM transactions n WHERE n.counterpartyKey = t.counterpartyKey " +
            "AND n.counterpartyName IS NOT NULL ORDER BY n.occurredAt DESC LIMIT 1) AS name, " +
            "(SELECT p.counterpartyPhone FROM transactions p WHERE p.counterpartyKey = t.counterpartyKey " +
            "AND p.counterpartyPhone IS NOT NULL ORDER BY p.occurredAt DESC LIMIT 1) AS phone, " +
            "COUNT(*) AS count, SUM(l.spendCents + l.inCents) AS totalCents, MAX(l.occurredAt) AS lastAt " +
            "FROM ledger l JOIN transactions t ON t.code = l.code " +
            "WHERE t.counterpartyKey IS NOT NULL AND t.isReversed = 0 AND l.flow = :flow AND l.kind IN (:kinds) " +
            "AND (:lineId IS NULL OR l.lineId = :lineId) " +
            "GROUP BY t.counterpartyKey ORDER BY totalCents DESC, t.counterpartyKey",
    )
    fun people(flow: String, kinds: List<String>, lineId: Long?): Flow<List<PersonTotal>>

    @Query(
        "SELECT COALESCE(SUM(spendCents), 0) AS sentCents, COALESCE(SUM(spendCents > 0), 0) AS sentCount, " +
            "COALESCE(SUM(inCents), 0) AS receivedCents, COALESCE(SUM(inCents > 0), 0) AS receivedCount " +
            "FROM ledger WHERE counterpartyKey = :counterpartyKey AND (:lineId IS NULL OR lineId = :lineId)",
    )
    fun personSummary(counterpartyKey: String, lineId: Long?): Flow<PersonSummary>

    /** Spending's chart (R45): spent per Nairobi month. */
    @Query(
        "SELECT strftime('%Y-%m', (occurredAt + " + NAIROBI_OFFSET_MS + ") / 1000, 'unixepoch') AS month, " +
            "SUM(spendCents + feeCents) AS cents, SUM(spendCents + feeCents > 0) AS count FROM ledger " +
            "WHERE occurredAt >= :from AND (:to IS NULL OR occurredAt < :to) AND (:lineId IS NULL OR lineId = :lineId) " +
            "GROUP BY month",
    )
    fun spentByMonth(from: Instant, to: Instant?, lineId: Long?): Flow<List<MonthTotal>>

    /** Home's week bars (R57): spent per Nairobi week, keyed by its Monday ('weekday 0' then '-6 days'). */
    @Query(
        "SELECT date((occurredAt + " + NAIROBI_OFFSET_MS + ") / 1000, 'unixepoch', 'weekday 0', '-6 days') AS period, " +
            "SUM(spendCents + feeCents) AS cents, SUM(spendCents + feeCents > 0) AS count FROM ledger " +
            "WHERE occurredAt >= :from AND (:to IS NULL OR occurredAt < :to) AND (:lineId IS NULL OR lineId = :lineId) " +
            "GROUP BY period",
    )
    fun spentByWeek(from: Instant, to: Instant?, lineId: Long?): Flow<List<PeriodSum>>

    /** Home's year bars (R57): spent per Nairobi year. */
    @Query(
        "SELECT strftime('%Y', (occurredAt + " + NAIROBI_OFFSET_MS + ") / 1000, 'unixepoch') AS period, " +
            "SUM(spendCents + feeCents) AS cents, SUM(spendCents + feeCents > 0) AS count FROM ledger " +
            "WHERE occurredAt >= :from AND (:to IS NULL OR occurredAt < :to) AND (:lineId IS NULL OR lineId = :lineId) " +
            "GROUP BY period",
    )
    fun spentByYear(from: Instant, to: Instant?, lineId: Long?): Flow<List<PeriodSum>>

    /**
     * Every measure per category per Nairobi month (4e §3.3): spent like `spentByCategory` (fees included), received
     * like `moneyIn`, and moved = the amount of rows not reversed. The `ledger` view keeps hidden rows out.
     */
    @Query(
        "SELECT l.categoryKey AS categoryKey, strftime('%Y-%m', (l.occurredAt + " + NAIROBI_OFFSET_MS + ") / 1000, 'unixepoch') AS month, " +
            "SUM(l.spendCents + l.feeCents) AS spentCents, SUM(l.spendCents + l.feeCents > 0) AS spentCount, " +
            "SUM(l.inCents) AS receivedCents, SUM(l.inCents > 0) AS receivedCount, " +
            "SUM(CASE WHEN t.isReversed = 0 THEN l.amountCents ELSE 0 END) AS movedCents, " +
            "SUM(t.isReversed = 0 AND l.amountCents > 0) AS movedCount " +
            "FROM ledger l JOIN transactions t ON t.code = l.code " +
            "WHERE l.categoryKey IN (:keys) AND l.occurredAt >= :from AND (:to IS NULL OR l.occurredAt < :to) " +
            "AND (:lineId IS NULL OR l.lineId = :lineId) GROUP BY l.categoryKey, month",
    )
    fun categoryMonthTotals(keys: List<String>, from: Instant, to: Instant?, lineId: Long?): Flow<List<CategoryMonthTotals>>

    /** D4, R96: a category's biggest counterparties since [from], in its own measure, names as People shows them. */
    @Query(
        "SELECT t.counterpartyKey AS counterpartyKey, " +
            "(SELECT n.counterpartyName FROM transactions n WHERE n.counterpartyKey = t.counterpartyKey " +
            "AND n.counterpartyName IS NOT NULL ORDER BY n.occurredAt DESC LIMIT 1) AS name, " +
            "(SELECT p.counterpartyPhone FROM transactions p WHERE p.counterpartyKey = t.counterpartyKey " +
            "AND p.counterpartyPhone IS NOT NULL ORDER BY p.occurredAt DESC LIMIT 1) AS phone, " +
            "COUNT(*) AS count, " +
            "SUM(CASE :measure WHEN 'SPENT' THEN l.spendCents + l.feeCents WHEN 'RECEIVED' THEN l.inCents ELSE l.amountCents END) AS totalCents, " +
            "MAX(l.occurredAt) AS lastAt " +
            "FROM ledger l JOIN transactions t ON t.code = l.code " +
            "WHERE l.categoryKey = :categoryKey AND t.counterpartyKey IS NOT NULL AND t.isReversed = 0 AND l.occurredAt >= :from " +
            "AND (:lineId IS NULL OR l.lineId = :lineId) " +
            "GROUP BY t.counterpartyKey ORDER BY totalCents DESC, t.counterpartyKey LIMIT :limit",
    )
    fun topPlaces(categoryKey: String, measure: String, from: Instant, lineId: Long?, limit: Int): Flow<List<PersonTotal>>

    /** A category's newest payments that counted: "usually by the Nth", the last payment, who was paid. */
    @Query(
        "SELECT l.code AS code, l.categoryKey AS categoryKey, l.occurredAt AS occurredAt, l.spendCents + l.feeCents AS cents, " +
            "t.counterpartyName AS name FROM ledger l JOIN transactions t ON t.code = l.code " +
            "WHERE l.categoryKey = :categoryKey AND l.spendCents + l.feeCents > 0 AND (:lineId IS NULL OR l.lineId = :lineId) " +
            "ORDER BY l.occurredAt DESC, l.code DESC LIMIT :limit",
    )
    fun latestSpends(categoryKey: String, lineId: Long?, limit: Int): Flow<List<CategorySpend>>

    @Query(
        "SELECT COALESCE(SUM(spendCents + feeCents), 0) AS spentCents, COALESCE(SUM(feeCents), 0) AS feeCents, " +
            "COALESCE(SUM(inCents), 0) AS inCents FROM ledger " +
            "WHERE occurredAt >= :from AND (:to IS NULL OR occurredAt < :to) AND (:lineId IS NULL OR lineId = :lineId)",
    )
    fun totals(from: Instant, to: Instant?, lineId: Long?): Flow<PeriodTotals>

    /** A summary's Spent and its count (spec §11, R106): every line, [from] to [to]. */
    @Query(
        "SELECT COALESCE(SUM(spendCents + feeCents), 0) AS cents, COALESCE(SUM(spendCents + feeCents > 0), 0) AS count " +
            "FROM ledger WHERE occurredAt >= :from AND occurredAt < :to",
    )
    suspend fun spentIn(from: Instant, to: Instant): SpentCount

    /** A summary's biggest payment: the row that added most to Spent, the newest on a tie. */
    @Query(
        "SELECT l.code AS code, l.kind AS kind, t.counterpartyName AS name, l.spendCents + l.feeCents AS cents " +
            "FROM ledger l JOIN transactions t ON t.code = l.code " +
            "WHERE l.occurredAt >= :from AND l.occurredAt < :to AND l.spendCents + l.feeCents > 0 " +
            "ORDER BY cents DESC, l.occurredAt DESC, l.code DESC LIMIT 1",
    )
    suspend fun biggest(from: Instant, to: Instant): BiggestPayment?
}
