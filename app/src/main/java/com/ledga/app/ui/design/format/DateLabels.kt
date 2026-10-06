package com.ledga.app.ui.design.format

import com.ledga.core.model.FlowKind
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

    /**
     * Spec §10.5: "Ksh 1,200 spent at Naivas, yesterday 7:12 PM". The verb follows the transaction's [flow]: only
     * SPEND is "spent" (spec §5.3), so a savings deposit is "moved to savings at M-Shwari", not spending.
     */
    fun txSpeech(cents: Long, flow: FlowKind, counterparty: String, at: Instant, today: LocalDate): String {
        val verb = when (flow) {
            FlowKind.SPEND -> "spent at"
            FlowKind.INCOME -> "received from"
            FlowKind.SAVINGS_OUT -> "moved to savings at"
            FlowKind.SAVINGS_IN -> "taken from savings at"
            FlowKind.OWN_OUT -> "moved to your account at"
            FlowKind.OWN_IN -> "moved from your account at"
            FlowKind.LOAN_REPAY -> "repaid to"
            FlowKind.REVERSAL_IN -> "reversed from"
        }
        val day = spokenDay(nairobiDate(at), today)
        return "${AmountFormat.CURRENCY} ${AmountFormat.plain(cents)} $verb $counterparty, $day ${clock(at)}"
    }

    private fun dayText(day: LocalDate, today: LocalDate): String =
        (if (day.year == today.year) DAY else DAY_YEAR).format(day)
}
