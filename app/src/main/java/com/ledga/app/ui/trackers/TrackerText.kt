package com.ledga.app.ui.trackers

import com.ledga.core.time.PeriodType
import com.ledga.core.model.TxKind
import com.ledga.core.derive.RuleField
import com.ledga.core.derive.RuleEngine
import com.ledga.core.chart.Bucket
import com.ledga.app.data.room.TxRow
import com.ledga.app.data.room.RuleRow
import com.ledga.app.data.edit.RulePreview
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

    /** "Name has KPLC", "KPLC Prepaid · account 37100000001" (R35), "Account 37100000001", "Phone 0712***111" (R49). */
    fun ruleLabel(rule: RuleRow): String = when (rule.field) {
        RuleField.NAME_CONTAINS -> "Name has ${NameFormat.display(rule.pattern)}"
        RuleField.NAME_AND_ACCOUNT -> RuleEngine.splitNameAndAccount(rule.pattern)
            ?.let { (name, account) -> "${NameFormat.display(name)} · account $account" } ?: "Name and account"
        RuleField.ACCOUNT_EQUALS -> "Account ${rule.pattern}"
        RuleField.PHONE_EQUALS -> "Phone ${rule.pattern}"
    }

    /** The chart's tooltip (spec §10.4): "Ksh 1,850 · Sep" over "2 payments"; the running period says "so far". */
    fun tooltip(bucket: Bucket, running: Boolean): Pair<String, String> {
        val p = bucket.period
        val name = if (p.type == PeriodType.YEAR) p.start.year.toString() else p.start.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        val n = bucket.count
        return "${ksh(bucket.total.cents)} · $name${if (running) " so far" else ""}" to "$n ${if (n == 1) "payment" else "payments"}"
    }

    /**
     * Tracker detail's payment rows (mockup): the category is the tracker, so the row leads with the day. A paybill adds
     * its account ("Today · Acc 37100000001", one subtitle that ellipsizes); anything else gets its time as the tail.
     */
    fun paymentLine(tx: TxRow, today: LocalDate): Pair<String, String?> {
        val day = DateLabels.nairobiDate(tx.occurredAt)
        val dayText = when {
            day == today -> "Today"
            day == today.minusDays(1) -> "Yesterday"
            day.year == today.year -> DateLabels.dayMonth(day)
            else -> DateLabels.date(day)
        }
        val account = tx.counterpartyAccount?.takeIf { tx.kind == TxKind.PAYBILL }
        return if (account != null) "$dayText · Acc $account" to null else dayText to DateLabels.clock(tx.occurredAt)
    }

    /** "+ Add rule"'s line under the fields (R48): what Save will do, before it does it. */
    /** While the text typed is being counted (R48). */
    val COUNTING = "Counting${Char(0x2026)}"

    fun rulePreview(preview: RulePreview?, name: String, categoryName: String): String {
        fun payments(n: Int) = "$n ${if (n == 1) "payment" else "payments"}"
        val counted = when {
            preview == null -> return if (name.isBlank()) "Type part of a name, like KPLC." else "Type at least two letters or numbers."
            preview.matches == 0 -> "No payments match yet. Future ones will go to $categoryName."
            preview.moving == 0 -> "Matches ${payments(preview.matches)}, all already in $categoryName."
            preview.handFiled == 0 -> "Matches ${payments(preview.matches)}. ${preview.moving} move to $categoryName."
            else -> "Matches ${payments(preview.matches)}. ${preview.moving} move to $categoryName, ${preview.handFiled} of them filed elsewhere by you."
        }
        return preview.replaces?.let { "$counted Replaces your rule for $it." } ?: counted
    }
}
