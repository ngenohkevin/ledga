package com.ledga.app.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import com.ledga.app.data.room.LedgerRow
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

/** One payment that counts in a category: when, what it counted (amount and fees) and who was paid. */
data class CategorySpend(val code: String, val categoryKey: String, val occurredAt: Instant, val cents: Long, val name: String?)

/** Spent in one period: [period] reads like `Period.key`: "2026-09-28" (a week, by its Monday) or "2026" (a year). */
data class PeriodSum(val period: String, val cents: Long, val count: Int)

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

    /** Trackers (R52): spent per category per Nairobi month, fees included, like `spentByCategory`. */
    @Query(
        "SELECT categoryKey, strftime('%Y-%m', (occurredAt + " + NAIROBI_OFFSET_MS + ") / 1000, 'unixepoch') AS month, " +
            "SUM(spendCents + feeCents) AS cents, SUM(spendCents + feeCents > 0) AS count FROM ledger " +
            "WHERE categoryKey IN (:keys) AND occurredAt >= :from AND (:to IS NULL OR occurredAt < :to) " +
            "AND (:lineId IS NULL OR lineId = :lineId) GROUP BY categoryKey, month",
    )
    fun spentByCategoryMonth(keys: List<String>, from: Instant, to: Instant?, lineId: Long?): Flow<List<CategoryMonthTotal>>

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
}
