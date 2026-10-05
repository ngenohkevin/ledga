package com.ledga.core.parse

import com.ledga.core.model.TxKind
import com.ledga.core.money.Money

/** What one message shape recognised. Fee, balance, date and Fuliza facts are filled in by MpesaParser. */
internal data class ShapeMatch(
    val kind: TxKind,
    val amount: Money,
    val counterparty: Counterparty? = null,
    val destinationCountry: String? = null,
    val reversesCode: String? = null,
    /** False for shapes that never print a date (Fuliza companion, auto-repay, reversals). */
    val carriesDate: Boolean = true,
    /** Replaces the generically extracted Fuliza facts when non-null. */
    val fuliza: FulizaFacts? = null,
    /** Finer shape id when one regex covers several shapes (e.g. "paybill" vs "send.phone"). */
    val subShape: String? = null,
)

internal class Shape(
    val id: String,
    pattern: String,
    private val build: (MatchResult, FulizaFacts?) -> ShapeMatch?,
) {
    private val regex = Regex(pattern, RegexOption.IGNORE_CASE)
    fun match(text: String, facts: FulizaFacts?): ShapeMatch? = regex.find(text)?.let { build(it, facts) }
}

internal fun MatchResult.text(name: String): String? = groups[name]?.value
internal fun MatchResult.money(name: String): Money? = text(name)?.let(Money::parse)

/** "21/3/26 at 1:30 PM" as a regex anchor (no groups; MpesaDates does the parsing). */
internal const val DT = """\d{1,2}/\d{1,2}/(?:\d{4}|\d{2})\s*at\s*\d{1,2}:\d{2}\s*[AP]M"""

/** "Ksh1,200.00" / "Ksh 1612.45" with the number captured as [group]. */
internal fun amt(group: String = "amount"): String = """Ksh\s?(?<$group>${Extract.NUM})"""

/**
 * Ordered list of message shapes. First match wins.
 * Ordering rules: global send before send (both read "sent to … on");
 * old paybill before buy goods (both read "paid to … on"); ATM before the
 * named-agent withdrawal. Name boundaries are anchored on " on <date> at <time>",
 * never a bare "on", so "CAFE ON THE GO" and "… online …" survive.
 */
internal object Shapes {
    private val PAYBILL_TO = Regex("""^(?<name>.+?)\s+for account(?:\s+(?<account>.+))?$""", RegexOption.IGNORE_CASE)
    private val PHONE_TAIL = Regex("""^(?:(?<name>.+?)\s+)?(?<phone>${Phones.FULL}|${Phones.MASKED})$""")
    private val NUMBER_TAIL = Regex("""^(?<name>.+?)\s+(?<num>\d{3,9})$""")

    private fun named(name: String?, phone: String? = null, account: String? = null, number: String? = null) =
        Counterparty(Names.normalize(name), phone, normalizeAccount(account), number)

    /** Recipient: "JANE TESTER 0712345111", "0712***111" or a bare name. */
    private fun person(raw: String): Counterparty =
        PHONE_TAIL.matchEntire(raw)?.let { named(it.text("name"), phone = it.text("phone")) } ?: named(raw)

    /** Receipt sender: a person with a phone, a business with a trailing number, or a bare name. */
    private fun sender(raw: String): Counterparty =
        PHONE_TAIL.matchEntire(raw)?.let { named(it.text("name"), phone = it.text("phone")) }
            ?: NUMBER_TAIL.matchEntire(raw)?.let { named(it.text("name"), number = it.text("num")) }
            ?: named(raw)

    // ---- Money out ----

    private val GLOBAL_SEND = Shape(
        "global.send",
        """${amt()}\s+sent to\s+(?<name>.+?)\s+(?<phone>\+\d{7,15})\s+\((?<country>[^)]+)\)\s+via M-?PESA Global""",
    ) { m, _ ->
        ShapeMatch(
            TxKind.GLOBAL_SEND,
            m.money("amount") ?: return@Shape null,
            named(m.text("name"), phone = m.text("phone")),
            destinationCountry = m.text("country")?.trim(),
        )
    }

    private val SEND_AMOUNT_FIRST = Shape("send.amount-first", """You have sent\s+${amt()}\s+to\s+(?<name>.+?)\s+on\s+$DT""") { m, _ ->
        ShapeMatch(TxKind.SEND, m.money("amount") ?: return@Shape null, named(m.text("name")))
    }

    private val SEND = Shape("send", """${amt()}\s+sent to\s+(?<to>.+?)\s+on\s+$DT""") { m, _ ->
        val amount = m.money("amount") ?: return@Shape null
        val to = m.text("to") ?: return@Shape null
        val paybill = PAYBILL_TO.matchEntire(to)
        if (paybill != null) {
            ShapeMatch(TxKind.PAYBILL, amount, named(paybill.text("name"), account = paybill.text("account")), subShape = "paybill")
        } else {
            val cp = person(to)
            ShapeMatch(TxKind.SEND, amount, cp, subShape = if (cp.phone != null) "send.phone" else "send.name")
        }
    }

    private val PAYBILL_OLD = Shape(
        "paybill.old",
        """${amt()}\s+paid to\s+(?<name>.+?)\.\s*Account Number\s+(?<account>.+?)\.?\s+on\s+$DT""",
    ) { m, _ ->
        ShapeMatch(TxKind.PAYBILL, m.money("amount") ?: return@Shape null, named(m.text("name"), account = m.text("account")))
    }

    private val BUY_GOODS = Shape("buygoods", """${amt()}\s+paid to\s+(?<name>.+?)\s+on\s+$DT""") { m, _ ->
        ShapeMatch(TxKind.BUY_GOODS, m.money("amount") ?: return@Shape null, named(m.text("name")))
    }

    private val WITHDRAW_ATM = Shape("withdraw.atm", """withdrawn\s+${amt()}\s+from an ATM""") { m, _ ->
        ShapeMatch(TxKind.WITHDRAW_ATM, m.money("amount") ?: return@Shape null, named("ATM"))
    }

    /** "…at 6:42 PMWithdraw Ksh4,500.00 from 012345 - AGENT … New M-PESA balance…" */
    private val WITHDRAW_GLUED = Shape(
        "withdraw.agent",
        """Withdraw\s*${amt()}\s+from\s+(?<agent>\d+)\s*-\s*(?<name>.+?)\s+New M-?PESA balance""",
    ) { m, _ ->
        ShapeMatch(TxKind.WITHDRAW_AGENT, m.money("amount") ?: return@Shape null, named(m.text("name"), number = m.text("agent")))
    }

    private val WITHDRAW_DASH = Shape(
        "withdraw.agent",
        """withdrawn\s+${amt()}\s+from\s+(?<agent>\d+)\s*-\s*(?<name>.+?)\s+on\s+$DT""",
    ) { m, _ ->
        ShapeMatch(TxKind.WITHDRAW_AGENT, m.money("amount") ?: return@Shape null, named(m.text("name"), number = m.text("agent")))
    }

    private val WITHDRAW_NAMED = Shape(
        "withdraw.agent",
        """withdrawn\s+${amt()}\s+from\s+(?<name>.+?)\s+(?<agent>\d{4,})\s+on\s+$DT""",
    ) { m, _ ->
        ShapeMatch(TxKind.WITHDRAW_AGENT, m.money("amount") ?: return@Shape null, named(m.text("name"), number = m.text("agent")))
    }

    private val AIRTIME_OTHER = Shape("airtime.other", """bought\s+${amt()}\s+of airtime for\s+(?<phone>\+?\d{9,12})""") { m, _ ->
        ShapeMatch(TxKind.AIRTIME_OTHER, m.money("amount") ?: return@Shape null, Counterparty(null, m.text("phone"), null, null))
    }

    private val AIRTIME_SELF_BOUGHT = Shape("airtime.self", """bought\s+${amt()}\s+of airtime(?!\s+for)""") { m, _ ->
        ShapeMatch(TxKind.AIRTIME_SELF, m.money("amount") ?: return@Shape null)
    }

    private val AIRTIME_SELF_PURCHASED = Shape("airtime.self", """${amt()}\s+of airtime purchased""") { m, _ ->
        ShapeMatch(TxKind.AIRTIME_SELF, m.money("amount") ?: return@Shape null)
    }

    val ALL: List<Shape> = listOf(
        GLOBAL_SEND, SEND_AMOUNT_FIRST, SEND, PAYBILL_OLD, BUY_GOODS,
        WITHDRAW_ATM, WITHDRAW_GLUED, WITHDRAW_DASH, WITHDRAW_NAMED,
        AIRTIME_OTHER, AIRTIME_SELF_BOUGHT, AIRTIME_SELF_PURCHASED,
    )
}
