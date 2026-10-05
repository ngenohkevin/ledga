package com.ledga.core.parse

import com.ledga.core.model.TxKind
import com.ledga.core.money.Money
import java.time.Instant
import java.time.LocalDate

sealed interface ParseOutcome {
    /** A money event. */
    data class Parsed(val sms: ParsedSms) : ParseOutcome

    /** Not a transaction (no code, failed, balance check). Still stored by Phase 2 with this reason. */
    data class Ignored(val reason: IgnoreReason) : ParseOutcome

    /** Has a transaction code but no shape matched. Listed in "Messages Ledga couldn't read". */
    data class Unreadable(val reason: String) : ParseOutcome
}

enum class IgnoreReason { NO_CODE, FAILED, BALANCE_CHECK }

/** Fuliza figures an SMS states. Any SMS shape may carry some of them. */
data class FulizaFacts(
    val drawn: Money? = null,
    val accessFee: Money? = null,
    val outstanding: Money? = null,
    val availableLimit: Money? = null,
    val dueDate: LocalDate? = null,
) {
    val isEmpty: Boolean
        get() = drawn == null && accessFee == null && outstanding == null && availableLimit == null && dueDate == null

    fun mergedWith(newer: FulizaFacts?): FulizaFacts = if (newer == null) this else FulizaFacts(
        drawn = newer.drawn ?: drawn,
        accessFee = newer.accessFee ?: accessFee,
        outstanding = newer.outstanding ?: outstanding,
        availableLimit = newer.availableLimit ?: availableLimit,
        dueDate = newer.dueDate ?: dueDate,
    )
}

data class ParsedSms(
    val code: String,
    val kind: TxKind,
    /** Shape id that matched, e.g. "paybill", "send.phone", "fuliza.companion" (debug + audit histogram). */
    val shape: String,
    val amount: Money,
    /** M-Pesa transaction cost only. The Fuliza access fee is [FulizaFacts.accessFee]. */
    val fee: Money,
    /** Wallet balance after the transaction; null when the SMS states none (never 0). */
    val balance: Money?,
    val occurredAt: Instant,
    val occurredAtFromBody: Boolean,
    /** True only when this shape normally prints a date but it could not be parsed. */
    val occurredAtApprox: Boolean,
    val counterparty: Counterparty?,
    val destinationCountry: String?,
    val reversesCode: String?,
    val fuliza: FulizaFacts?,
    val savingsBalance: Money?,
)
