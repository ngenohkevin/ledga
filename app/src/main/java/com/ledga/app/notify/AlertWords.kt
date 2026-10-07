package com.ledga.app.notify

import com.ledga.app.data.alerts.AlertType
import com.ledga.app.data.room.TxRow
import com.ledga.app.ui.design.format.DateLabels
import com.ledga.app.ui.design.format.NameFormat
import com.ledga.core.model.TxKind
import com.ledga.core.money.Money

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

    internal fun ksh(cents: Long): String = Money(cents).kshText()

    private fun name(raw: String?): String? = raw?.takeIf { it.isNotBlank() }?.let(NameFormat::display)
}
