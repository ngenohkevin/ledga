package com.ledga.app.data.trackers

import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.RuleRow
import com.ledga.app.data.room.TxRow
import com.ledga.app.data.room.dao.CategoryMonthTotal
import com.ledga.app.data.room.dao.CategorySpend
import com.ledga.core.chart.Bucket
import com.ledga.core.chart.Bucketing
import com.ledga.core.money.Money
import com.ledga.core.time.InstantRange
import com.ledga.core.time.Nairobi
import com.ledga.core.time.Period
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.YearMonth

/**
 * One tracked category's numbers (spec §10.4, R52):
 * - [months]: the last 13 Nairobi months, oldest first, the running one last;
 * - [averageCents]: the average of the completed ones from the first payment;
 * - [usualDay]: "usually by the Nth", only for a bill paid in each of the last three months;
 * - [last]: the newest payment that counted.
 */
data class TrackerSummary(
    val category: CategoryRow,
    val months: List<Bucket>,
    val averageCents: Long?,
    val usualDay: Int?,
    val last: CategorySpend?,
) {
    val thisMonth: Bucket get() = months.last()
    val lastMonth: Bucket get() = months[months.lastIndex - 1]
}

/** Tracker detail (spec §10.4): the summary, every month since the first payment, this year so far, the rules, the latest payments. */
data class TrackerDetail(
    val summary: TrackerSummary,
    /** From the first payment's month (the running month when there is none) to the running one. */
    val allMonths: List<Bucket>,
    val yearSoFarCents: Long,
    val rules: List<RuleRow>,
    val payments: List<TxRow>,
)

/** The one reader behind Home's tracker strip, the Trackers tab and Tracker detail, so they always agree (R52). */
class Trackers(private val db: LedgaDatabase, private val ledger: LedgerQueries) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun summaries(lineId: Long?, now: Instant): Flow<List<TrackerSummary>> = db.categoriesDao().observeTracked().flatMapLatest { tracked ->
        if (tracked.isEmpty()) return@flatMapLatest flowOf(emptyList())
        val periods = Periods.lastN(PeriodType.MONTH, now, MONTHS)
        val keys = tracked.map { it.key }
        combine(
            ledger.spentByCategoryMonth(keys, InstantRange(periods.first().startInstant, null), lineId),
            combine(keys.map { ledger.latestSpends(it, lineId, LATEST) }) { it.toList() },
        ) { monthly, latest ->
            val byCategory = monthly.groupBy { it.categoryKey }
            tracked.mapIndexed { i, category -> summary(category, periods, byCategory[category.key].orEmpty(), latest[i], now) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun detail(categoryKey: String, lineId: Long?, now: Instant): Flow<TrackerDetail?> = db.categoriesDao().observe(categoryKey).flatMapLatest { category ->
        if (category == null) return@flatMapLatest flowOf(null)
        combine(
            ledger.spentByCategoryMonth(listOf(categoryKey), InstantRange(Instant.EPOCH, null), lineId),
            ledger.latestSpends(categoryKey, lineId, LATEST),
            db.rulesDao().observeForCategory(categoryKey),
            ledger.recent(lineId, categoryKey),
        ) { monthly, latest, rules, payments ->
            val first = monthly.filter { it.count > 0 }.minByOrNull { it.month }
                ?.let { YearMonth.parse(it.month).atDay(1).atStartOfDay(Nairobi.ZONE).toInstant() }
            val all = buckets(monthly, Periods.since(PeriodType.MONTH, first ?: now, now))
            val year = Periods.dateOf(now).year
            TrackerDetail(
                summary = summary(category, Periods.lastN(PeriodType.MONTH, now, MONTHS), monthly, latest, now),
                allMonths = all,
                yearSoFarCents = all.filter { it.period.start.year == year }.sumOf { it.total.cents },
                rules = rules,
                payments = payments,
            )
        }
    }

    private fun summary(category: CategoryRow, periods: List<Period>, monthly: List<CategoryMonthTotal>, latest: List<CategorySpend>, now: Instant): TrackerSummary {
        val months = buckets(monthly, periods)
        return TrackerSummary(
            category = category,
            months = months,
            averageCents = Bucketing.averageOfCompleted(months, now)?.cents,
            usualDay = if (Bucketing.isMonthly(months, now)) Bucketing.usualDayOfMonth(latest.map { it.occurredAt }) else null,
            last = latest.firstOrNull(),
        )
    }

    private fun buckets(monthly: List<CategoryMonthTotal>, periods: List<Period>): List<Bucket> =
        Bucketing.zeroFill(monthly.associate { it.month to Money(it.cents) }, monthly.associate { it.month to it.count }, periods)

    companion object {
        /** 12 completed months for the average, plus the running one. */
        const val MONTHS = 13

        /** Spec §10.6: "usually by the Nth" reads the last 6 payments. */
        const val LATEST = 6
    }
}
