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

/** Ordered list of message shapes. First match wins. Filled in Tasks 6–7. */
internal object Shapes {
    val ALL: List<Shape> = emptyList()
}
