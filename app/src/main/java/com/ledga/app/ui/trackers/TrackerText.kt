package com.ledga.app.ui.trackers

import java.util.Locale
import java.time.format.TextStyle
import java.time.LocalDate
import com.ledga.app.ui.design.format.NameFormat
import com.ledga.app.ui.design.format.DateLabels
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

    /**
     * A Trackers row's context (spec §10.4, R52): "KPLC Prepaid · 2 payments this month", "… · usually by the 12th",
     * "Last: Jul · Ksh 5,200" (the year when it isn't this one), or "No payments yet".
     */
    fun rowContext(s: TrackerSummary, today: LocalDate): String {
        val name = s.last?.name?.let(NameFormat::display)
        val paid = s.thisMonth.count
        val last = s.last
        return when {
            paid > 0 -> listOfNotNull(name, "$paid ${if (paid == 1) "payment" else "payments"} this month").joinToString(" · ")
            s.usualDay != null -> listOfNotNull(name, "usually by the ${ordinal(s.usualDay)}").joinToString(" · ")
            last != null -> {
                val day = DateLabels.nairobiDate(last.occurredAt)
                val month = day.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + if (day.year == today.year) "" else " ${day.year}"
                "Last: $month · ${ksh(last.cents)}"
            }
            else -> "No payments yet"
        }
    }

    /** A Trackers row's detail under the amount (R52): last month while this one is unpaid, else the average, else "this month". */
    fun rowDetail(s: TrackerSummary): String = when {
        s.thisMonth.count == 0 && s.lastMonth.total.cents > 0 ->
            s.lastMonth.period.start.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + AmountFormat.plain(s.lastMonth.total.cents, Decimals.NEVER)
        s.averageCents != null -> "avg ${AmountFormat.plain(s.averageCents, Decimals.NEVER)}"
        else -> "this month"
    }
}
