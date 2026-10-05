package com.ledga.core.parse

import com.ledga.core.money.Money
import kotlin.text.RegexOption.IGNORE_CASE

/** Figures that can appear in any message shape. Every pattern is anchored on its own phrase. */
internal object Extract {
    /** A number as M-Pesa prints it: grouped "1,200.00" or plain "1612.45" / "463". */
    const val NUM = """\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?"""

    // Wallet patterns first; the generic "account balance" phrasing is only the
    // short-reversal "Your account balance is", so savings balances
    // ("KCB M-PESA Account balance is") are never taken as the wallet balance.
    private val WALLET_BALANCE = listOf(
        Regex("""M-?PESA balance is\s*(?:Ksh\s?)?(?<v>$NUM)""", IGNORE_CASE),
        Regex("""New balance is\s*(?:Ksh\s?)?(?<v>$NUM)""", IGNORE_CASE),
        Regex("""New M-?PESA account balance is\s*(?:Ksh\s?)?(?<v>$NUM)""", IGNORE_CASE),
        Regex("""Your account balance is\s*(?:Ksh\s?)?(?<v>$NUM)"""),
    )
    private val FEE = Regex("""Transaction cost,?\s*Ksh\s?(?<v>$NUM)""", IGNORE_CASE)
    private val F_DRAWN = Regex("""Fuliza M-?PESA amount is\s*Ksh\s?(?<v>$NUM)""", IGNORE_CASE)
    private val F_FEE = Regex("""(?:Interest|Access Fee) charged,?\s*(?:of\s*)?Ksh\s?(?<v>$NUM)""", IGNORE_CASE)
    private val F_OUTSTANDING = Regex("""Fuliza M-?PESA outstanding amount is\s*Ksh\s?(?<v>$NUM)""", IGNORE_CASE)
    private val F_LIMIT = Regex("""available Fuliza M-?PESA limit is\s*Ksh\s?(?<v>$NUM)""", IGNORE_CASE)
    private val F_DUE = Regex("""due on\s*(?<v>\d{1,2}/\d{1,2}/\d{2,4})""", IGNORE_CASE)
    private val SAVINGS_BALANCE = listOf(
        Regex("""KCB M-?PESA (?:Saving )?account balance is\s*(?:Ksh\s?)?(?<v>$NUM)""", IGNORE_CASE),
        Regex("""M-?Shwari (?:saving )?(?:account )?balance is\s*(?:Ksh\s?)?(?<v>$NUM)""", IGNORE_CASE),
    )

    fun walletBalance(text: String): Money? = WALLET_BALANCE.firstNotNullOfOrNull { it.money(text) }

    fun fee(text: String): Money = FEE.money(text) ?: Money.ZERO

    fun fuliza(text: String): FulizaFacts? = FulizaFacts(
        drawn = F_DRAWN.money(text),
        accessFee = F_FEE.money(text),
        outstanding = F_OUTSTANDING.money(text),
        availableLimit = F_LIMIT.money(text),
        dueDate = F_DUE.find(text)?.groups?.get("v")?.value?.let(MpesaDates::parseDueDate),
    ).takeUnless { it.isEmpty }

    fun savingsBalance(text: String): Money? = SAVINGS_BALANCE.firstNotNullOfOrNull { it.money(text) }

    private fun Regex.money(text: String): Money? = find(text)?.groups?.get("v")?.value?.let(Money::parse)
}
