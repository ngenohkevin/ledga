package com.ledga.app.ui.design.charts

import androidx.compose.runtime.Immutable

/**
 * One column, with:
 * - its label ("SEP");
 * - its segment values in cents, bottom first (one value for a single series);
 * - the phrase TalkBack reads for it;
 * - whether its period is still running, which draws it hatched.
 */
@Immutable
data class Bar(val label: String, val segments: List<Long>, val speech: String, val inProgress: Boolean = false) {
    /** Sum of the positive segments, saturating instead of overflowing. */
    val total: Long
        get() = segments.fold(0L) { acc, value ->
            val v = value.coerceAtLeast(0L)
            if (acc > Long.MAX_VALUE - v) Long.MAX_VALUE else acc + v
        }
}

/** Pure chart arithmetic (Review Focus #2: no NaN, no overflow, no division by zero). */
object ChartMath {

    /** value / max as 0..1. Negatives and a non-positive max give 0. */
    fun fraction(value: Long, max: Long): Float =
        if (max <= 0L || value <= 0L) 0f else (value.toDouble() / max).coerceAtMost(1.0).toFloat()

    /** What the plot's top edge stands for: the tallest bar, or the average if it is higher. */
    fun scaleMax(totals: List<Long>, average: Long?): Long = maxOf(totals.maxOrNull() ?: 0L, average ?: 0L, 0L)

    /** A 1-2-5 step that gives at most three gridlines up to [max]. */
    fun gridStep(max: Long): Long {
        if (max <= 0L) return 0L
        val raw = max / 3 + if (max % 3 == 0L) 0 else 1
        var magnitude = 1L
        while (magnitude <= raw / 10) magnitude *= 10
        return longArrayOf(1, 2, 5, 10).map { it * magnitude }.first { it >= raw }
    }

    fun gridLines(max: Long): List<Long> {
        val step = gridStep(max)
        return if (step <= 0L) emptyList() else (1..3).map { it * step }.filter { it <= max }
    }
}
