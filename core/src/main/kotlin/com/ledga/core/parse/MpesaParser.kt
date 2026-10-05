package com.ledga.core.parse

import java.time.Instant

object MpesaParser {
    /** Bump on ANY behaviour change: Phase 2 re-parses all stored SMS when it changes. */
    const val VERSION: Int = 1

    private val CODE = Regex("""^([A-Z0-9]{10})(?=[\s.]|$)""")
    private val FAILED = Regex("""^[A-Z0-9]{10}\s+Failed\b""", RegexOption.IGNORE_CASE)
    private val BALANCE_CHECK = Regex("""account balance was""", RegexOption.IGNORE_CASE)

    fun isMpesaSender(address: String): Boolean = SmsText.isMpesaSender(address)

    fun parse(body: String, receivedAt: Instant): ParseOutcome {
        val text = SmsText.normalize(body)
        val code = CODE.find(text)?.groupValues?.get(1)?.takeIf { c -> c.any { it in 'A'..'Z' } }
            ?: return ParseOutcome.Ignored(IgnoreReason.NO_CODE)
        if (FAILED.containsMatchIn(text)) return ParseOutcome.Ignored(IgnoreReason.FAILED)
        if (BALANCE_CHECK.containsMatchIn(text)) return ParseOutcome.Ignored(IgnoreReason.BALANCE_CHECK)

        val facts = Extract.fuliza(text)
        for (shape in Shapes.ALL) {
            val m = shape.match(text, facts) ?: continue
            val bodyTime = MpesaDates.findDateTime(text)
            return ParseOutcome.Parsed(
                ParsedSms(
                    code = code,
                    kind = m.kind,
                    shape = m.subShape ?: shape.id,
                    amount = m.amount,
                    fee = Extract.fee(text),
                    balance = Extract.walletBalance(text),
                    occurredAt = bodyTime ?: receivedAt,
                    occurredAtFromBody = bodyTime != null,
                    occurredAtApprox = bodyTime == null && m.carriesDate,
                    counterparty = m.counterparty,
                    destinationCountry = m.destinationCountry,
                    reversesCode = m.reversesCode,
                    fuliza = m.fuliza ?: facts,
                    savingsBalance = Extract.savingsBalance(text),
                ),
            )
        }
        return ParseOutcome.Unreadable("no message shape matched")
    }
}
