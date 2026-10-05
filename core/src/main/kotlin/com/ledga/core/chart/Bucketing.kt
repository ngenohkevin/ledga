package com.ledga.core.chart

import com.ledga.core.money.Money
import com.ledga.core.time.Period
import com.ledga.core.time.Periods
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

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

    fun usualDayOfMonth(paymentTimes: Iterable<Instant>): Int? {
        val days = paymentTimes.sortedDescending().take(6).map { Periods.dateOf(it).dayOfMonth }.sorted()
        return if (days.isEmpty()) null else days[(days.size - 1) / 2]
    }

    fun deltaPercent(current: Money, previous: Money): Int? {
        if (previous.isZero) return null
        return BigDecimal(current.cents - previous.cents)
            .multiply(BigDecimal(100))
            .divide(BigDecimal(previous.cents), 0, RoundingMode.HALF_UP)
            .intValueExact()
    }
}
