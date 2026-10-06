package com.ledga.app.ui.tx

import com.ledga.app.data.room.LineRow
import com.ledga.app.data.room.TxRow
import com.ledga.app.ui.design.components.ChipTone
import com.ledga.app.ui.design.components.Leading
import com.ledga.app.ui.design.format.AmountFormat
import com.ledga.app.ui.design.format.DateLabels
import com.ledga.app.ui.design.format.NameFormat
import com.ledga.core.model.FlowKind
import com.ledga.core.model.KindDefaults
import com.ledga.core.model.TxKind
import com.ledga.core.money.Decimals
import java.time.LocalDate

/** A chip on the transaction sheet ("Paybill · Acc 37100000001", "Fuliza covered Ksh 463.00"). */
data class TxChip(val text: String, val tone: ChipTone = ChipTone.Neutral)

enum class FactTone { Normal, Danger }

/** A key-value on the transaction sheet ("Code" TJK4AB12FA). [isLine] marks the line fact, which can move the payment. */
data class Fact(
    val label: String,
    val value: String,
    val tone: FactTone = FactTone.Normal,
    val copyable: Boolean = false,
    val isLine: Boolean = false,
)

/**
 * What Ledga says about one transaction, wherever it shows (Activity rows, the sheet, the person sheet, Home in 4c):
 * its title, row subtitle and leading icon, the sheet's chips and facts, TalkBack's phrase and the shared text.
 */
object TxText {
    private val INFLOWS = setOf(FlowKind.INCOME, FlowKind.SAVINGS_IN, FlowKind.OWN_IN, FlowKind.REVERSAL_IN)
    private val PEOPLE_KINDS = setOf(TxKind.SEND, TxKind.RECEIVE, TxKind.GLOBAL_SEND, TxKind.GLOBAL_RECEIVE)
    private val STAR = Char(0x2605)

    /** Money coming into the wallet shows "+" in green (spec §10.1). */
    fun isInflow(flow: FlowKind): Boolean = flow in INFLOWS

    /** The counterparty as people write it (R44), or what the payment was when the SMS names no one. */
    fun title(tx: TxRow): String = tx.counterpartyName?.takeIf { it.isNotBlank() }?.let(NameFormat::display) ?: kindLabel(tx.kind)

    fun kindLabel(kind: TxKind): String = when (kind) {
        TxKind.SEND -> "Sent"
        TxKind.PAYBILL -> "Paybill"
        TxKind.BUY_GOODS -> "Buy goods"
        TxKind.WITHDRAW_AGENT -> "Cash withdrawal"
        TxKind.WITHDRAW_ATM -> "ATM withdrawal"
        TxKind.DEPOSIT -> "Cash deposit"
        TxKind.RECEIVE -> "Received"
        TxKind.AIRTIME_SELF -> "Airtime"
        TxKind.AIRTIME_OTHER -> "Airtime for others"
        TxKind.GLOBAL_SEND -> "Sent abroad"
        TxKind.GLOBAL_RECEIVE -> "Received from abroad"
        TxKind.SAVINGS_OUT -> "To savings"
        TxKind.SAVINGS_IN -> "From savings"
        TxKind.FULIZA_REPAY_AUTO, TxKind.FULIZA_REPAY_MANUAL -> "Fuliza repayment"
        TxKind.FULIZA_REVERSAL -> "Fuliza reversal"
        TxKind.REVERSAL -> "Reversal"
        TxKind.FULIZA_ONLY -> "Fuliza payment"
    }

    /** A row's subtitle (spec §10.4): the category, or "Fuliza Ksh 463" for a payment Fuliza helped with. */
    fun subtitle(tx: TxRow, categoryName: String): String =
        tx.fulizaDrawnCents?.takeIf { it > 0 }?.let { "Fuliza ${AmountFormat.CURRENCY} ${AmountFormat.plain(it)}" } ?: categoryName

    /** People get initials (spec §10.3) while their category is their kind's own; everything else its category's icon. */
    fun leading(tx: TxRow, icon3d: String): Leading =
        if (tx.kind in PEOPLE_KINDS && tx.counterpartyName != null && tx.categoryKey == KindDefaults.categoryFor(tx.kind)) {
            Leading.Avatar(title(tx), isInflow(tx.flow))
        } else {
            Leading.Icon(icon3d)
        }

    /** Spec §10.5: "Ksh 1,200 spent at Naivas, yesterday 7:12 PM". */
    fun speech(tx: TxRow, today: LocalDate): String = DateLabels.txSpeech(tx.amountCents, tx.flow, title(tx), tx.occurredAt, today)

    /** "Personal ··23": a line's name and the last two digits of its number (mockup `txsheet`). */
    fun lineLabel(line: LineRow): String {
        val tail = line.phoneNumber?.filter(Char::isDigit)?.takeLast(2)?.takeIf { it.length == 2 }
        return if (tail == null) line.displayName else "${line.displayName} ··$tail"
    }

    /** "Electricity ★": a tracked category carries a star (mockups `txsheet`, `picker`). */
    fun categoryLabel(name: String, tracked: Boolean): String = if (tracked) "$name $STAR" else name

    /** The sheet's chips: what the payment was, what it cost, and anything unusual about it. */
    fun chips(tx: TxRow): List<TxChip> = buildList {
        val account = tx.counterpartyAccount
        val phone = tx.counterpartyPhone
        if (tx.kind == TxKind.PAYBILL && account != null) {
            add(TxChip("Paybill · Acc $account"))
        } else if (phone != null) {
            add(TxChip("${kindLabel(tx.kind)} · $phone"))
        }
        tx.destinationCountry?.let { add(TxChip("To $it")) }
        tx.fulizaDrawnCents?.takeIf { it > 0 }?.let { add(TxChip("Fuliza covered ${ksh(it)}", ChipTone.Danger)) }
        // The transaction cost alone: Fuliza's access fee is a fact of its own (both count in Spent).
        if (!isInflow(tx.flow)) add(TxChip("Fee ${ksh(tx.feeCents - (tx.fulizaFeeCents ?: 0L))}"))
        if (tx.isReversed) add(TxChip("Reversed · not counted"))
        if (tx.kind == TxKind.FULIZA_ONLY) add(TxChip("Payment details missing", ChipTone.Warning))
        if (tx.occurredAtApprox) add(TxChip("Time approximate", ChipTone.Warning))
        if (tx.isHidden) add(TxChip("Hidden"))
    }

    /** The sheet's key-values (mockups `txsheet`, `txsheet-fz`). */
    fun facts(tx: TxRow, line: LineRow?): List<Fact> = buildList {
        add(Fact("Code", tx.code, copyable = true))
        add(Fact("Date", DateLabels.dateTime(tx.occurredAt)))
        val drawn = tx.fulizaDrawnCents?.takeIf { it > 0 }
        if (drawn != null) {
            add(Fact("From your balance", ksh(maxOf(0L, tx.amountCents - drawn))))
            tx.fulizaFeeCents?.let { add(Fact("Fuliza access fee", ksh(it), FactTone.Danger)) }
            tx.fulizaOutstandingCents?.let { owed ->
                val due = tx.fulizaDueDate?.let { " · due ${DateLabels.dayMonth(it)}" }.orEmpty()
                add(Fact("Fuliza owed after", ksh(owed) + due))
            }
        }
        if (tx.kind == TxKind.PAYBILL) tx.counterpartyAccount?.let { add(Fact("Account", it)) }
        tx.balanceCents?.let { add(Fact("Balance after", ksh(it))) }
        tx.reversesCode?.let { add(Fact("Reverses", it)) }
        add(Fact("Line", line?.let(::lineLabel) ?: "Not set", isLine = true))
    }

    /**
     * The Share action's plain text (spec §10.4): the payment itself, as proof of it. Nothing about the person's wallet
     * or private notes goes with it: no balance, no Fuliza borrowing, no line, no note.
     */
    fun shareText(tx: TxRow, categoryName: String): String = buildList {
        add(title(tx))
        add("${AmountFormat.signedKsh(tx.amountCents, isInflow(tx.flow))} · $categoryName")
        add(DateLabels.dateTime(tx.occurredAt))
        add("M-Pesa code ${tx.code}")
        if (tx.kind == TxKind.PAYBILL) tx.counterpartyAccount?.let { add("Account $it") }
        if (tx.feeCents > 0) add("Fees ${ksh(tx.feeCents)}")
        add("Shared from Ledga")
    }.joinToString("\n")

    private fun ksh(cents: Long): String = "${AmountFormat.CURRENCY} ${AmountFormat.plain(cents, Decimals.ALWAYS)}"
}
