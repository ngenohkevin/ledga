package com.ledga.app.work

import com.ledga.app.data.settings.Settings
import com.ledga.app.notify.AlertTimes
import java.time.Instant

/** The alerts that fire at a time (spec §11, R107): each one self-rescheduling job under its own unique name. */
enum class Scheduled(val uniqueName: String) {
    DAILY("ledga-daily"),
    WEEKLY("ledga-weekly"),
    ;

    /** Its switch in You → Notifications. */
    fun isOn(s: Settings): Boolean = when (this) {
        DAILY -> s.notifyDaily
        WEEKLY -> s.notifyWeekly
    }

    /** Its next time, strictly after [now]. */
    fun next(now: Instant, s: Settings): Instant = when (this) {
        DAILY -> AlertTimes.nextDaily(now, s.dailySummaryMinute)
        WEEKLY -> AlertTimes.nextWeekly(now)
    }
}
