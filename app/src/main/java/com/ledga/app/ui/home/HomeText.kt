package com.ledga.app.ui.home

import com.ledga.app.data.derive.FulizaStatus
import com.ledga.app.ui.design.format.AmountFormat
import com.ledga.app.ui.design.format.DateLabels
import com.ledga.core.money.Decimals
import com.ledga.core.time.Nairobi
import java.time.Instant
import java.time.LocalDate

/** What Home says (spec §10.4): always Nairobi time, and only what it knows (R58, R60). */
object HomeText {
    /** R60: by the Nairobi hour. */
    fun greeting(now: Instant): String = when (now.atZone(Nairobi.ZONE).hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    /** "Updated 2:15 PM", "Updated yesterday 2:15 PM", "Updated 3 Oct, 2:15 PM", "Updated 3 Oct 2025, 2:15 PM". */
    fun updated(at: Instant, today: LocalDate): String {
        val day = DateLabels.nairobiDate(at)
        val time = DateLabels.clock(at)
        val whenText = when (day) {
            today -> time
            today.minusDays(1) -> "yesterday $time"
            else -> (if (day.year == today.year) DateLabels.dayMonth(day) else DateLabels.date(day)) + ", $time"
        }
        return "Updated $whenText"
    }

    /** "Personal ··11 · Ksh 3,175.57 · 7:42 PM": a line's part of the All lines total, with Recent's time or day. */
    fun lineBalance(line: BalanceLine, today: LocalDate): String =
        "${line.label} · ${AmountFormat.CURRENCY} ${AmountFormat.plain(line.cents)} · ${rowTime(line.at, today)}"

    /** "Due 2 Nov", or "Was due 2 Nov" once the date has passed (R58). */
    fun due(due: LocalDate, today: LocalDate?): String =
        if (today != null && due < today) "Was due ${DateLabels.dayMonth(due)}" else "Due ${DateLabels.dayMonth(due)}"

    /** The Fuliza strip's title and detail (R58): an "available" only when a limit is known. */
    fun fuliza(s: FulizaStatus, today: LocalDate?): Pair<String, String?> {
        val owed = s.outstanding.cents
        if (owed <= 0) return "Fuliza · nothing owed" to s.available?.let { "${ksh(it.cents)} available to borrow" }
        val detail = listOfNotNull(s.dueDate?.let { due(it, today) }, s.available?.let { "${ksh(it.cents)} still available" })
        return "Fuliza · ${AmountFormat.CURRENCY} ${AmountFormat.plain(owed)} owed" to detail.joinToString(" · ").ifEmpty { null }
    }

    /** A Recent row's tail (it never truncates, so it stays short): "8:40 AM" today, then "Yesterday", "2 Oct", "2 Oct 2025". */
    fun rowTime(at: Instant, today: LocalDate): String {
        val day = DateLabels.nairobiDate(at)
        return when {
            day == today -> DateLabels.clock(at)
            day == today.minusDays(1) -> "Yesterday"
            day.year == today.year -> DateLabels.dayMonth(day)
            else -> DateLabels.date(day)
        }
    }

    /** "Ksh 8,735": whole shillings, as the cards show them. */
    fun ksh(cents: Long): String = "${AmountFormat.CURRENCY} ${AmountFormat.plain(cents, Decimals.NEVER)}"
}
