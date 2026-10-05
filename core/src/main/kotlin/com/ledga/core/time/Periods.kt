package com.ledga.core.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** [start, endExclusive). A null end is a live, open-ended current period: new transactions always land in it. */
data class InstantRange(val start: Instant, val endExclusive: Instant?) {
    init {
        require(endExclusive == null || endExclusive > start) { "empty range $start..$endExclusive" }
    }

    val isOpen: Boolean get() = endExclusive == null

    operator fun contains(t: Instant): Boolean = t >= start && (endExclusive == null || t < endExclusive)
}

enum class PeriodType { DAY, WEEK, MONTH, YEAR }

/** A calendar period in Nairobi time. [start] must be the period's first day (weeks start Monday). */
data class Period(val type: PeriodType, val start: LocalDate) {
    init {
        require(Periods.startOf(type, start) == start) { "$start is not the start of a $type" }
    }

    val endExclusive: LocalDate
        get() = when (type) {
            PeriodType.DAY -> start.plusDays(1)
            PeriodType.WEEK -> start.plusWeeks(1)
            PeriodType.MONTH -> start.plusMonths(1)
            PeriodType.YEAR -> start.plusYears(1)
        }

    val lengthInDays: Int get() = ChronoUnit.DAYS.between(start, endExclusive).toInt()

    /** Stable bucket key: "2026-10-05" (DAY, WEEK = its Monday), "2026-10" (MONTH), "2026" (YEAR). */
    val key: String
        get() = when (type) {
            PeriodType.DAY, PeriodType.WEEK -> start.toString()
            PeriodType.MONTH -> YearMonth.from(start).toString()
            PeriodType.YEAR -> start.year.toString()
        }

    val startInstant: Instant get() = start.atStartOfDay(Nairobi.ZONE).toInstant()
    val endInstant: Instant get() = endExclusive.atStartOfDay(Nairobi.ZONE).toInstant()

    fun range(): InstantRange = InstantRange(startInstant, endInstant)
    fun next(): Period = Period(type, endExclusive)
    fun previous(): Period = Period(
        type,
        when (type) {
            PeriodType.DAY -> start.minusDays(1)
            PeriodType.WEEK -> start.minusWeeks(1)
            PeriodType.MONTH -> start.minusMonths(1)
            PeriodType.YEAR -> start.minusYears(1)
        },
    )

    operator fun contains(t: Instant): Boolean = t in range()
}

object Periods {
    fun startOf(type: PeriodType, date: LocalDate): LocalDate = when (type) {
        PeriodType.DAY -> date
        PeriodType.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        PeriodType.MONTH -> date.withDayOfMonth(1)
        PeriodType.YEAR -> date.withDayOfYear(1)
    }

    fun dateOf(t: Instant): LocalDate = t.atZone(Nairobi.ZONE).toLocalDate()

    fun of(type: PeriodType, t: Instant): Period = Period(type, startOf(type, dateOf(t)))

    fun current(type: PeriodType, now: Instant): Period = of(type, now)

    fun liveRange(period: Period, now: Instant): InstantRange =
        if (period == current(period.type, now)) InstantRange(period.startInstant, null) else period.range()

    /** The last [n] periods, oldest first, ending with the current one. */
    fun lastN(type: PeriodType, now: Instant, n: Int): List<Period> {
        require(n >= 1) { "n must be >= 1" }
        return generateSequence(current(type, now)) { it.previous() }.take(n).toList().reversed()
    }

    /** The previous period's first k whole days, k = days elapsed in the current period including today. */
    fun comparisonWindow(type: PeriodType, now: Instant): InstantRange {
        val current = current(type, now)
        val elapsed = ChronoUnit.DAYS.between(current.start, dateOf(now)) + 1
        val previous = current.previous()
        val days = minOf(elapsed, previous.lengthInDays.toLong())
        return InstantRange(
            previous.startInstant,
            previous.start.plusDays(days).atStartOfDay(Nairobi.ZONE).toInstant(),
        )
    }

    fun nextMidnight(now: Instant): Instant = dateOf(now).plusDays(1).atStartOfDay(Nairobi.ZONE).toInstant()
}
