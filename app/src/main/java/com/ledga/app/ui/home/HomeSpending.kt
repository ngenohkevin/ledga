package com.ledga.app.ui.home

import com.ledga.app.data.room.dao.PeriodSum
import com.ledga.app.data.room.dao.PeriodTotals
import com.ledga.app.ui.design.format.DateLabels
import com.ledga.core.chart.Bucket
import com.ledga.core.chart.Bucketing
import com.ledga.core.money.Money
import com.ledga.core.time.Period
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Home's spending card (spec §10.4, R57): the running period so far, six bars, received and the average. */
data class SpendingCardUi(
    val type: PeriodType = PeriodType.MONTH,
    val spentCents: Long = 0,
    val feeCents: Long = 0,
    val inCents: Long = 0,
    /** "1–6 Oct": the running period so far. */
    val span: String = "",
    val deltaPercent: Int? = null,
    /** "same days Sep", "same days last week", "same days 2025" (spec §7.6). */
    val comparedWith: String = "",
    val bars: List<Long> = emptyList(),
    val labels: List<String> = emptyList(),
    val shortLabels: List<String> = emptyList(),
    val averageCents: Long? = null,
    /** TalkBack's reading of the bars (`MiniBars` has no per-bar nodes). */
    val summary: String = "",
) {
    val title: String
        get() = when (type) {
            PeriodType.WEEK -> "Spent this week"
            PeriodType.YEAR -> "Spent this year"
            else -> "Spent this month"
        }

    val averageLabel: String get() = "Avg/" + unit(type)
}

private fun unit(type: PeriodType) = when (type) {
    PeriodType.WEEK -> "week"
    PeriodType.YEAR -> "year"
    else -> "month"
}

object HomeSpending {
    const val BARS = 6
    private val DASH = Char(0x2013)

    /**
     * The card for the running [type] period at [now]: [before] is the same elapsed time of the previous one (spec §7.6).
     * For Year, [months] (the same span by month) lets the average count a first year only for its months.
     */
    fun card(
        type: PeriodType,
        now: Instant,
        totals: PeriodTotals,
        before: Money,
        sums: Map<String, PeriodSum>,
        months: Map<String, PeriodSum>? = null,
    ): SpendingCardUi {
        val periods = Periods.lastN(type, now, BARS)
        val buckets = Bucketing.zeroFill(sums.mapValues { Money(it.value.cents) }, sums.mapValues { it.value.count }, periods)
        val labels = periods.map(::label)
        return SpendingCardUi(
            type = type,
            spentCents = totals.spentCents,
            feeCents = totals.feeCents,
            inCents = totals.inCents,
            span = span(periods.last().start, Periods.dateOf(now)),
            deltaPercent = Bucketing.deltaPercent(Money(totals.spentCents), before),
            comparedWith = comparedWith(periods.last()),
            bars = buckets.map { it.total.cents },
            labels = labels,
            shortLabels = periods.map(::shortLabel),
            averageCents = if (type == PeriodType.YEAR && months != null) {
                val monthly = Periods.since(PeriodType.MONTH, periods.first().startInstant, now)
                Bucketing.averagePerYear(Bucketing.zeroFill(months.mapValues { Money(it.value.cents) }, months.mapValues { it.value.count }, monthly), now)?.cents
            } else {
                Bucketing.averageOfCompleted(buckets, now)?.cents
            },
            summary = summary(type, labels, buckets),
        )
    }

    /** "6 Oct" on a period's first day, "1–6 Oct" within a month, "28 Sep–4 Oct" or "1 Jan–6 Oct" across months. */
    fun span(start: LocalDate, today: LocalDate): String = when {
        start == today -> DateLabels.dayMonth(today)
        start.year == today.year && start.month == today.month -> "${start.dayOfMonth}$DASH${DateLabels.dayMonth(today)}"
        else -> "${DateLabels.dayMonth(start)}$DASH${DateLabels.dayMonth(today)}"
    }

    fun comparedWith(current: Period): String = when (current.type) {
        PeriodType.WEEK -> "same days last week"
        PeriodType.YEAR -> "same days ${current.start.year - 1}"
        else -> "same days " + current.start.minusMonths(1).month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    }

    private fun label(p: Period): String = when (p.type) {
        PeriodType.WEEK -> DateLabels.dayMonth(p.start)
        PeriodType.YEAR -> p.start.year.toString()
        else -> p.start.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    }

    private fun shortLabel(p: Period): String = when (p.type) {
        PeriodType.WEEK -> p.start.dayOfMonth.toString()
        PeriodType.YEAR -> p.start.year.toString()
        else -> label(p).take(1)
    }

    private fun summary(type: PeriodType, labels: List<String>, buckets: List<Bucket>): String =
        "Spent by ${unit(type)}: " + buckets.mapIndexed { i, b ->
            "${labels[i]}${if (i == buckets.lastIndex) " so far" else ""} ${HomeText.ksh(b.total.cents)}"
        }.joinToString(", ")
}
