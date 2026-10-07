package com.ledga.app.ui.you

import com.ledga.app.data.settings.Settings
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.ui.design.format.AmountFormat
import com.ledga.core.money.Decimals
import com.ledga.core.money.Money
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What You → Notifications says (spec §11, R75). */
object NotificationText {
    private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val HOUR = DateTimeFormatter.ofPattern("h a", Locale.ENGLISH)
    private val AT_LEAST = Char(0x2265)

    const val EXPLANATION = "Ledga works out every notification on this phone, from your M-Pesa messages. Nothing is sent anywhere."
    const val WEEKLY_DETAIL = "Sundays at 7 PM: the week's total against the week before, and where most of it went."
    const val FULIZA_DETAIL = "When Fuliza covers a payment, 3 days before it's due, and on the due date."
    const val OFF_BANNER = "Notifications are off for Ledga, so none of these can reach you."

    /** "12:00 AM", "8:00 PM". */
    fun time(minute: Int): String = LocalTime.of(minute / 60, minute % 60).format(CLOCK)

    /** "8 PM", "8:30 PM" (You's row). */
    fun shortTime(minute: Int): String = LocalTime.of(minute / 60, minute % 60).let { if (it.minute == 0) it.format(HOUR) else it.format(CLOCK) }

    fun threshold(cents: Long): String = "${AmountFormat.CURRENCY} ${AmountFormat.plain(cents, Decimals.NEVER)}"

    /** A time before noon sums up the day before (owner 2026-10-07, final review I2). */
    fun dailyDetail(minute: Int): String {
        val day = if (minute < 12 * 60) "the day before" else "that day"
        return "What you spent $day, at ${time(minute)}. Skipped on days with no spending."
    }

    fun largeDetail(cents: Long): String = "When a payment of ${threshold(cents)} or more arrives."

    /** The threshold as typed, in cents; null when unreadable or outside Ksh 100 to Ksh 1,000,000 (R75). */
    fun parseThreshold(text: String): Long? =
        Money.parse(text)?.cents?.takeIf { it in SettingsStore.LARGE_MIN_CENTS..SettingsStore.LARGE_MAX_CENTS }

    /** You's row: "Daily 8 PM · Weekly Sun · Large ≥ 5k · Fuliza", "All off", or why nothing can arrive. */
    fun summary(s: Settings, allowed: Boolean): String {
        if (!allowed) return "Off for Ledga in Android settings"
        val on = buildList {
            if (s.notifyDaily) add("Daily ${shortTime(s.dailySummaryMinute)}")
            if (s.notifyWeekly) add("Weekly Sun")
            if (s.notifyLarge) add("Large $AT_LEAST ${AmountFormat.compact(s.largeThresholdCents)}")
            if (s.notifyFuliza) add("Fuliza")
        }
        return on.joinToString(" · ").ifEmpty { "All off" }
    }
}
