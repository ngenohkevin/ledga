package com.ledga.core.audit

import com.ledga.core.model.TxKind
import com.ledga.core.money.Money
import com.ledga.core.parse.MpesaParser
import com.ledga.core.parse.ParseOutcome
import com.ledga.core.parse.ParsedSms
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Opt-in audit against the owner's real v1 export (spec §15.1). Skipped when the file is
 * absent (always in CI). Prints counts and masked skeletons only. Run with:
 *   ./gradlew :core:test --tests 'com.ledga.core.audit.CorpusAuditTest' --rerun
 */
class CorpusAuditTest {

    /** v2 kinds acceptable for each v1 type (v2 deliberately reclassifies some, spec §4 #5/#8). */
    private val compatible: Map<String, Set<TxKind>> = mapOf(
        "SEND" to setOf(TxKind.SEND, TxKind.PAYBILL),
        "PAY_BILL" to setOf(TxKind.PAYBILL),
        "BUY_GOODS" to setOf(TxKind.BUY_GOODS),
        "WITHDRAW_AGENT" to setOf(TxKind.WITHDRAW_AGENT),
        "WITHDRAW_ATM" to setOf(TxKind.WITHDRAW_ATM),
        "DEPOSIT" to setOf(TxKind.DEPOSIT),
        "RECEIVED" to setOf(TxKind.RECEIVE, TxKind.GLOBAL_RECEIVE),
        "AIRTIME_SELF" to setOf(TxKind.AIRTIME_SELF),
        "AIRTIME_OTHER" to setOf(TxKind.AIRTIME_OTHER),
        "MPESA_GLOBAL" to setOf(TxKind.GLOBAL_SEND, TxKind.GLOBAL_RECEIVE),
        "FULIZA" to setOf(TxKind.FULIZA_ONLY, TxKind.SEND, TxKind.PAYBILL, TxKind.BUY_GOODS),
        "FULIZA_REPAYMENT" to setOf(TxKind.FULIZA_REPAY_MANUAL),
        "FULIZA_REVERSAL" to setOf(TxKind.FULIZA_REVERSAL),
        "FULIZA_AUTO_PAY" to setOf(TxKind.FULIZA_REPAY_AUTO),
        "MSHWARI" to setOf(TxKind.SAVINGS_OUT, TxKind.SAVINGS_IN),
        "KCB_MPESA" to setOf(TxKind.SAVINGS_OUT, TxKind.SAVINGS_IN),
        "REVERSAL" to setOf(TxKind.REVERSAL),
    )
    private val mustBeNamed = setOf(
        TxKind.SEND, TxKind.PAYBILL, TxKind.BUY_GOODS, TxKind.WITHDRAW_AGENT,
        TxKind.GLOBAL_SEND, TxKind.RECEIVE, TxKind.GLOBAL_RECEIVE,
    )

    @Test
    fun `parser handles the real corpus and agrees with v1 wherever v1 was right`() {
        assumeTrue(Corpus.available(), "corpus not present at ${Corpus.file} — skipped")
        val rows = Corpus.load()
        val problems = mutableListOf<String>()
        val shapes = sortedMapOf<String, Int>()
        var dated = 0
        var dateDisagreements = 0

        for (row in rows) {
            val outcome = MpesaParser.parse(row.rawSms, row.timestamp)
            val p: ParsedSms = when (outcome) {
                is ParseOutcome.Parsed -> outcome.sms
                is ParseOutcome.Ignored -> { problems += "IGNORED(${outcome.reason}) ${Corpus.skeleton(row.rawSms)}"; continue }
                is ParseOutcome.Unreadable -> { problems += "UNREADABLE ${Corpus.skeleton(row.rawSms)}"; continue }
            }
            shapes.merge(p.shape, 1) { a, b -> a + b }
            val sk by lazy { Corpus.skeleton(row.rawSms) }

            compatible[row.type]?.let { if (p.kind !in it) problems += "KIND ${row.type}->${p.kind} $sk" }

            val comparableAmount = if (row.type == "FULIZA" && p.kind != TxKind.FULIZA_ONLY) p.fuliza?.drawn ?: p.amount else p.amount
            if (Corpus.cents(row.amount) != comparableAmount.cents) problems += "AMOUNT ${row.type}->${p.kind} $sk"

            val v2Fee = if (row.type == "FULIZA") p.fee + (p.fuliza?.accessFee ?: Money.ZERO) else p.fee
            val v1Fee = Corpus.cents(row.fee)
            val v1StaleFulizaFee = row.type == "FULIZA" && v1Fee == 0L
            if (v1Fee != v2Fee.cents && !v1StaleFulizaFee) problems += "FEE ${row.type}->${p.kind} $sk"

            val v1Balance = Corpus.cents(row.balance)
            val v2Balance = p.balance
            val balanceOk = when {
                v2Balance == null -> v1Balance == 0L
                v1Balance == 0L -> true // v1 missed it (e.g. "New balance is") or it really is zero
                v1Balance == v2Balance.cents -> true
                v1Balance == p.savingsBalance?.cents -> true // v1 took the savings balance (fixed later in v1)
                else -> false
            }
            if (!balanceOk) problems += "BALANCE ${row.type}->${p.kind} $sk"

            if (p.kind in mustBeNamed && p.counterparty?.name == null) problems += "UNNAMED ${p.kind} $sk"

            row.fulizaOutstanding?.let { v1 ->
                if (p.fuliza?.outstanding?.cents != Corpus.cents(v1)) problems += "OUTSTANDING ${p.kind} $sk"
            }
            row.reversedCode?.let { if (p.reversesCode != it) problems += "REVERSES ${p.kind} $sk" }

            if (p.occurredAtFromBody) {
                dated++
                if (p.occurredAt != row.timestamp) dateDisagreements++
            }
        }

        println("=== Ledga corpus audit (parser) — ${rows.size} messages ===")
        shapes.forEach { (shape, n) -> println("  %-22s %6d".format(shape, n)) }
        println("dated: $dated, date disagreements with v1: $dateDisagreements")
        if (dateDisagreements * 100 > dated) problems += "DATES $dateDisagreements of $dated disagree with v1 (>1%)"

        val grouped = problems.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
        grouped.take(40).forEach { (p, n) -> println("PROBLEM x$n  $p") }
        assertTrue(problems.isEmpty(), "${problems.size} corpus problems (${grouped.size} distinct); see printed skeletons")
    }
}
