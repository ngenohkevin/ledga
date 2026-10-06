package com.ledga.app.ui.design.format

import com.ledga.core.time.Nairobi
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Dates and times as Ledga shows them: always Nairobi time (spec §6.4), whatever the device zone. */
object DateLabels {
    private val DAY = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val DAY_YEAR = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
    private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun nairobiDate(instant: Instant): LocalDate = instant.atZone(Nairobi.ZONE).toLocalDate()

    /** Day-card header (spec §10.4, audit #9): "TODAY", "YESTERDAY", "FRI, 2 OCT", "THU, 2 OCT 2025". */
    fun dayHeader(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "TODAY"
        today.minusDays(1) -> "YESTERDAY"
        else -> dayText(day, today).uppercase(Locale.ENGLISH)
    }

    /** "7:12 PM", Nairobi time. */
    fun clock(instant: Instant): String = CLOCK.format(instant.atZone(Nairobi.ZONE))

    /** TalkBack's day: "today", "yesterday", "Fri, 2 Oct", "Thu, 2 Oct 2025". */
    fun spokenDay(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "today"
        today.minusDays(1) -> "yesterday"
        else -> dayText(day, today)
    }

    /** Spec §10.5: "Ksh 1,200 spent at Naivas, yesterday 7:12 PM" / "Ksh 5,000 received from Jane Doe, today 11:02 AM". */
    fun txSpeech(cents: Long, inflow: Boolean, counterparty: String, at: Instant, today: LocalDate): String {
        val verb = if (inflow) "received from" else "spent at"
        val day = spokenDay(nairobiDate(at), today)
        return "${AmountFormat.CURRENCY} ${AmountFormat.plain(cents)} $verb $counterparty, $day ${clock(at)}"
    }

    private fun dayText(day: LocalDate, today: LocalDate): String =
        (if (day.year == today.year) DAY else DAY_YEAR).format(day)
}
