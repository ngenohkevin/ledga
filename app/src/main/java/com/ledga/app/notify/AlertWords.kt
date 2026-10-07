package com.ledga.app.notify

import com.ledga.app.data.alerts.AlertType
import com.ledga.app.data.room.TxRow
import com.ledga.app.data.room.dao.BiggestPayment
import com.ledga.app.data.room.dao.SpentCount
import com.ledga.app.ui.app.grouped
import com.ledga.app.ui.design.format.DateLabels
import com.ledga.app.ui.design.format.NameFormat
import com.ledga.app.ui.tx.TxText
import com.ledga.core.model.TxKind
import com.ledga.core.money.Money
import java.time.LocalDate
import kotlin.math.abs

/**
 * What every alert says (spec §11, R111), with its dedupe key (spec §7.1) and what its tap opens (R100). Android shows a
 * title on one line and expands the body. Amounts read like the rest of Ledga: "Ksh 1,000", "Ksh 6,418.36".
 */
object AlertWords {
    private const val DOT = " · "

    /** R104: "Ksh 1,000 to KPLC Prepaid" / "Large payment · Electricity". */
    fun large(tx: TxRow, categoryName: String?): Alert {
        val amount = ksh(tx.amountCents)
        val title = when (tx.kind) {
            TxKind.WITHDRAW_AGENT, TxKind.WITHDRAW_ATM -> "$amount withdrawn"
            TxKind.AIRTIME_SELF, TxKind.AIRTIME_OTHER -> "$amount of airtime"
            else -> name(tx.counterpartyName)?.let { "$amount to $it" } ?: "$amount paid"
        }
        val body = listOfNotNull("Large payment", categoryName).joinToString(DOT)
        return Alert("large:${tx.code}", AlertType.LARGE, title, body, tx.code, NotificationTap.Payment(tx.code))
    }

    /** R104: "Fuliza covered Ksh 463" / "Of a Ksh 2,500 payment to Sample Supermarket · you owe Ksh 6,418.36, due 2 Nov". */
    fun fulizaDraw(tx: TxRow): Alert {
        val payment = if (tx.kind == TxKind.FULIZA_ONLY) {
            null // the payment's own SMS never came: only what Fuliza covered is known
        } else {
            "Of a ${ksh(tx.amountCents)} payment" + (name(tx.counterpartyName)?.let { " to $it" } ?: "")
        }
        val owed = tx.fulizaOutstandingCents?.let { o ->
            "you owe ${ksh(o)}" + (tx.fulizaDueDate?.let { ", due ${DateLabels.dayMonth(it)}" } ?: "")
        }
        val body = listOfNotNull(payment, owed).joinToString(DOT).replaceFirstChar { it.uppercaseChar() }
        val title = "Fuliza covered ${ksh(tx.fulizaDrawnCents ?: 0L)}"
        return Alert("fuliza-draw:${tx.code}", AlertType.FULIZA_DRAW, title, body, tx.code, NotificationTap.Payment(tx.code))
    }

    /** A weekly summary's biggest category, by name. */
    data class CategoryShare(val name: String, val cents: Long)

    /**
     * R106: "Spent Ksh 3,533 today" / "Across 3 payments · biggest: Jane Tester Ksh 2,500". An early summary's day is
     * "yesterday" (I2); a late one names its day.
     */
    fun daily(day: LocalDate, today: LocalDate, spent: SpentCount, biggest: BiggestPayment): Alert {
        val title = "Spent ${ksh(spent.cents)} " + when (day) {
            today -> "today"
            today.minusDays(1) -> "yesterday"
            else -> "on ${DateLabels.dayMonth(day)}"
        }
        val top = "${name(biggest.name) ?: TxText.kindLabel(biggest.kind)} ${ksh(biggest.cents)}"
        val body = if (spent.count <= 1) "One payment: $top" else "Across ${grouped(spent.count)} payments${DOT}biggest: $top"
        return Alert("daily:$day", AlertType.DAILY, title, body, null, NotificationTap.Spending(day, day))
    }

    /** R106: "Spent Ksh 8,000 this week" / "20% less than last week · most on Groceries (Ksh 6,000)". */
    fun weekly(monday: LocalDate, spentCents: Long, previousCents: Long, top: CategoryShare?): Alert {
        val parts = buildList {
            if (previousCents > 0) add(change(spentCents, previousCents))
            top?.let { add("most on ${it.name} (${ksh(it.cents)})") }
        }
        val body = parts.joinToString(DOT).replaceFirstChar { it.uppercaseChar() }
        val tap = NotificationTap.Spending(monday, monday.plusDays(6))
        return Alert("weekly:$monday", AlertType.WEEKLY, "Spent ${ksh(spentCents)} this week", body, null, tap)
    }

    /** "20% less than last week", "5% more than last week", or "about the same as last week" under half a percent. */
    fun change(now: Long, before: Long): String {
        val percent = (abs(now - before) * 100 + before / 2) / before
        return when {
            percent == 0L -> "about the same as last week"
            now > before -> "$percent% more than last week"
            else -> "$percent% less than last week"
        }
    }

    /** R105: "Fuliza Ksh 6,418.36 due in 3 days" / "Personal ··11 · due 2 Nov" (or "Due 2 Nov" without a line). */
    fun fulizaDue(due: FulizaDue, lineLabel: String?): Alert {
        val owed = ksh(due.outstandingCents)
        val title = when (due.daysLeft) {
            0 -> "Fuliza $owed due today"
            1 -> "Fuliza $owed due tomorrow"
            else -> "Fuliza $owed due in ${due.daysLeft} days"
        }
        val body = (lineLabel?.let { "$it${DOT}due " } ?: "Due ") + DateLabels.dayMonth(due.dueDate)
        return Alert(FulizaReminders.key(due), AlertType.FULIZA_DUE, title, body, null, NotificationTap.Fuliza)
    }

    internal fun ksh(cents: Long): String = Money(cents).kshText()

    private fun name(raw: String?): String? = raw?.takeIf { it.isNotBlank() }?.let(NameFormat::display)
}
