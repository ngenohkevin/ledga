package com.ledga.app.data.trackers

import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.TxRow
import com.ledga.app.data.room.dao.CategoryMonthTotal
import com.ledga.app.data.room.dao.CategorySpend
import com.ledga.app.data.room.dao.PersonTotal
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
import kotlinx.coroutines.flow.map
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

/** A category's page (4e §3.2): its summary, every month since the first payment, this year so far, top places, the latest payments. */
data class CategoryDetail(
    val summary: TrackerSummary,
    val measure: CategoryMeasure,
    /** From the first payment's month (the running month when there is none) to the running one. */
    val allMonths: List<Bucket>,
    val yearSoFarCents: Long,
    val topPlaces: List<PersonTotal>,
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
            ledger.categoryMonthTotals(keys, InstantRange(periods.first().startInstant, null), lineId).map { rows -> rows.map { it.pick(CategoryMeasure.SPENT) } },
            combine(keys.map { ledger.latestSpends(it, lineId, LATEST) }) { it.toList() },
        ) { monthly, latest ->
            val byCategory = monthly.groupBy { it.categoryKey }
            tracked.mapIndexed { i, category -> summary(category, periods, byCategory[category.key].orEmpty(), latest[i], now) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun category(categoryKey: String, lineId: Long?, now: Instant): Flow<CategoryDetail?> = db.categoriesDao().observe(categoryKey).flatMapLatest { category ->
        if (category == null) return@flatMapLatest flowOf(null)
        val measure = CategoryMeasure.of(category.groupKey)
        val periods = Periods.lastN(PeriodType.MONTH, now, MONTHS)
        combine(
            ledger.categoryMonthTotals(listOf(categoryKey), InstantRange(Instant.EPOCH, null), lineId).map { rows -> rows.map { it.pick(measure) } },
            ledger.latestSpends(categoryKey, lineId, LATEST),
            ledger.topPlaces(categoryKey, measure, periods.first().startInstant, lineId),
            ledger.recent(lineId, categoryKey),
        ) { monthly, latest, places, payments ->
            val first = monthly.filter { it.count > 0 }.minByOrNull { it.month }
                ?.let { YearMonth.parse(it.month).atDay(1).atStartOfDay(Nairobi.ZONE).toInstant() }
            val all = buckets(monthly, Periods.since(PeriodType.MONTH, first ?: now, now))
            val year = Periods.dateOf(now).year
            CategoryDetail(
                summary = summary(category, periods, monthly, latest, now),
                measure = measure,
                allMonths = all,
                yearSoFarCents = all.filter { it.period.start.year == year }.sumOf { it.total.cents },
                topPlaces = places,
                payments = payments,
            )
        }
    }

    /** R95: this month's amount for every category, each in its own measure; a category with nothing has no entry. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun monthTotals(lineId: Long?, now: Instant): Flow<Map<String, Long>> = db.categoriesDao().observeAll().flatMapLatest { categories ->
        val measures = categories.associate { it.key to CategoryMeasure.of(it.groupKey) }
        val month = Periods.current(PeriodType.MONTH, now)
        ledger.categoryMonthTotals(categories.map { it.key }, InstantRange(month.startInstant, null), lineId).map { rows ->
            rows.mapNotNull { r -> measures[r.categoryKey]?.let { r.categoryKey to r.pick(it).cents } }.filter { it.second != 0L }.toMap()
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
