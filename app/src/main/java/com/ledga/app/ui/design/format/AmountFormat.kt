package com.ledga.app.ui.design.format

import com.ledga.core.money.Decimals
import com.ledga.core.money.Money
import kotlin.math.abs

/** Amount text for the UI. All input is `Long` cents. */
object AmountFormat {
    /** U+2212 MINUS SIGN: as wide as "+" in Inter's tabular figures; a hyphen is narrower. */
    val MINUS: Char = Char(0x2212)
    const val CURRENCY = "Ksh"

    /** "1,200" or "1,200.50": the magnitude, with no sign or currency. */
    fun plain(cents: Long, decimals: Decimals = Decimals.AUTO): String =
        Money(cents).amountText(decimals).removePrefix("-")

    /** A transaction amount (spec §10.1): "+5,000.00" in, "−1,200.00" out, "0.00" for zero. */
    fun signed(cents: Long, inflow: Boolean): String {
        val body = plain(cents, Decimals.ALWAYS)
        return when {
            cents == 0L -> body
            inflow -> "+$body"
            else -> "$MINUS$body"
        }
    }

    /** The transaction sheet's amount (mockup `txsheet`): [MINUS] then "Ksh 1,000.00" out, "+Ksh 5,000.00" in, "Ksh 0.00". */
    fun signedKsh(cents: Long, inflow: Boolean): String {
        val body = "$CURRENCY ${plain(cents, Decimals.ALWAYS)}"
        return when {
            cents == 0L -> body
            inflow -> "+$body"
            else -> "$MINUS$body"
        }
    }

    /** Chart labels in whole shillings, half-up: "950", "2.5k", "12k", "1.2M". Negative values get [MINUS]. */
    fun compact(cents: Long): String {
        val sign = if (cents < 0) MINUS.toString() else ""
        val shillings = (abs(cents.coerceAtLeast(-Long.MAX_VALUE)) + 50) / 100
        val body = when {
            shillings < 1_000 -> shillings.toString()
            shillings < 9_950 -> tenths(shillings, 100) + "k"
            shillings < 999_500 -> ((shillings + 500) / 1_000).toString() + "k"
            shillings < 9_950_000 -> tenths(shillings, 100_000) + "M"
            else -> ((shillings + 500_000) / 1_000_000).toString() + "M"
        }
        return sign + body
    }

    /** [value] in tenths of 10 × [unit], half-up, without a trailing ".0": tenths(2450, 100) = "2.5". */
    private fun tenths(value: Long, unit: Long): String {
        val t = (value + unit / 2) / unit
        return if (t % 10 == 0L) (t / 10).toString() else "${t / 10}.${t % 10}"
    }
}
