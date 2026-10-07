package com.ledga.app.notify

import com.ledga.core.time.Nairobi
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** When each scheduled alert fires (spec §11): Nairobi time whatever the phone's zone, always strictly after now. */
object AlertTimes {
    /** Spec §11: the weekly summary, Sundays at 7 PM. */
    val WEEKLY_DAY: DayOfWeek = DayOfWeek.SUNDAY
    val WEEKLY_AT: LocalTime = LocalTime.of(19, 0)

    /** Spec §11: Fuliza reminders at 9 AM (R105: one check a day). */
    val FULIZA_AT: LocalTime = LocalTime.of(9, 0)

    fun nextFuliza(now: Instant): Instant = nextAt(now, FULIZA_AT)

    /** The daily summary at the person's time ([minuteOfDay] after midnight). */
    fun nextDaily(now: Instant, minuteOfDay: Int): Instant = nextAt(now, LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))

    fun nextWeekly(now: Instant): Instant {
        val first = now.atZone(Nairobi.ZONE).toLocalDate().with(TemporalAdjusters.nextOrSame(WEEKLY_DAY)).atTime(WEEKLY_AT).atZone(Nairobi.ZONE)
        return (if (first.toInstant().isAfter(now)) first else first.plusWeeks(1)).toInstant()
    }

    /** The next [time] in Nairobi strictly after [now]: today's while it is still ahead, else tomorrow's. */
    fun nextAt(now: Instant, time: LocalTime): Instant {
        val today = now.atZone(Nairobi.ZONE).toLocalDate().atTime(time).atZone(Nairobi.ZONE)
        return (if (today.toInstant().isAfter(now)) today else today.plusDays(1)).toInstant()
    }
}
