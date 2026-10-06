package com.ledga.core.chart

import com.ledga.core.money.Money
import com.ledga.core.time.Period
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate

data class AmountPoint(val at: Instant, val amount: Money)

data class Bucket(val period: Period, val total: Money, val count: Int)

/** Month/week/day bucketing for every chart (spec §10.6). All boundaries are Nairobi time. */
object Bucketing {

    fun sumByPeriod(points: Iterable<AmountPoint>, periods: List<Period>): List<Bucket> {
        if (periods.isEmpty()) return emptyList()
        val type = periods.first().type
        require(periods.all { it.type == type }) { "mixed period types" }
        val byStart = points.groupBy { Periods.of(type, it.at).start }
        return periods.map { p ->
            val pts = byStart[p.start].orEmpty()
            Bucket(p, Money(pts.sumOf { it.amount.cents }), pts.size)
        }
    }

    fun zeroFill(totals: Map<String, Money>, counts: Map<String, Int>, periods: List<Period>): List<Bucket> =
        periods.map { Bucket(it, totals[it.key] ?: Money.ZERO, counts[it.key] ?: 0) }

    fun averageOfCompleted(buckets: List<Bucket>, now: Instant): Money? {
        val fromFirstPayment = buckets.filter { it.period.endInstant <= now }.dropWhile { it.count == 0 }
        if (fromFirstPayment.isEmpty()) return null
        val sum = fromFirstPayment.sumOf { it.total.cents }
        return Money(BigDecimal(sum).divide(BigDecimal(fromFirstPayment.size), 0, RoundingMode.HALF_UP).longValueExact())
    }

    /**
     * The average per completed year, from month buckets: each completed year counts only the months since the first
     * payment, so seven months of a first year are 7/12 of a year, not a whole one. Null until a year has completed.
     */
    fun averagePerYear(months: List<Bucket>, now: Instant): Money? {
        require(months.all { it.period.type == PeriodType.MONTH }) { "averagePerYear reads months" }
        val running = Periods.dateOf(now).year
        val counted = months.dropWhile { it.count == 0 }.filter { it.period.start.year < running }
        if (counted.isEmpty()) return null
        val perYear = BigDecimal(counted.sumOf { it.total.cents }).multiply(BigDecimal(12)).divide(BigDecimal(counted.size), 0, RoundingMode.HALF_UP)
        return Money(perYear.longValueExact())
    }

    fun usualDayOfMonth(paymentTimes: Iterable<Instant>): Int? {
        val days = paymentTimes.sortedDescending().take(6).map { Periods.dateOf(it).dayOfMonth }.sorted()
        return if (days.isEmpty()) null else days[(days.size - 1) / 2]
    }

    /**
     * Whether "usually by the Nth" means anything (R52): each of the last [months] completed MONTH buckets had a
     * payment. A car service or any irregular cost has no usual day.
     */
    fun isMonthly(buckets: List<Bucket>, now: Instant, months: Int = 3): Boolean {
        val completed = buckets.filter { it.period.type == PeriodType.MONTH && it.period.endInstant <= now }
        return completed.size >= months && completed.takeLast(months).all { it.count > 0 }
    }

    /** MONTH buckets added up into YEAR buckets, oldest first. */
    fun byYear(months: List<Bucket>): List<Bucket> {
        require(months.all { it.period.type == PeriodType.MONTH }) { "byYear adds up months" }
        return months.groupBy { it.period.start.year }.toSortedMap().map { (year, list) ->
            Bucket(Period(PeriodType.YEAR, LocalDate.of(year, 1, 1)), Money(list.sumOf { it.total.cents }), list.sumOf { it.count })
        }
    }

    /**
     * Tracker detail's "All" (R54): one bar per month for up to [maxMonths] months of history, else one per year.
     * `ColumnChart` fits about 13 bars, and a year's label reads whole where a quarter's would not.
     */
    fun allTime(months: List<Bucket>, maxMonths: Int = 12): List<Bucket> = if (months.size <= maxMonths) months else byYear(months)

    fun deltaPercent(current: Money, previous: Money): Int? {
        if (previous.isZero) return null
        return BigDecimal(current.cents - previous.cents)
            .multiply(BigDecimal(100))
            .divide(BigDecimal(previous.cents), 0, RoundingMode.HALF_UP)
            .intValueExact()
    }
}
