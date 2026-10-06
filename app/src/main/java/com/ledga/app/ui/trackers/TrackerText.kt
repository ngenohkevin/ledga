package com.ledga.app.ui.trackers

import com.ledga.app.data.trackers.TrackerSummary
import com.ledga.app.ui.design.format.AmountFormat
import com.ledga.core.money.Decimals

/** What a tracker says, wherever it shows (Home's tile, the Trackers row, Tracker detail; R52). */
object TrackerText {
    fun ksh(cents: Long): String = "${AmountFormat.CURRENCY} ${AmountFormat.plain(cents, Decimals.NEVER)}"

    fun ordinal(day: Int): String = day.toString() + when {
        day % 100 in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }

    /** Home's tile (spec §10.4): "usually by the 12th" while a monthly bill is still unpaid, else "avg 1,780/mo", else "this month". */
    fun tileCaption(s: TrackerSummary): String = when {
        s.thisMonth.count == 0 && s.usualDay != null -> "usually by the ${ordinal(s.usualDay)}"
        s.averageCents != null -> "avg ${AmountFormat.plain(s.averageCents, Decimals.NEVER)}/mo"
        else -> "this month"
    }
}
