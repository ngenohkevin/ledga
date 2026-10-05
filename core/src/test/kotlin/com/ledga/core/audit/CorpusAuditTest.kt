package com.ledga.core.audit

import com.ledga.core.derive.BalanceChain
import com.ledga.core.derive.Derivation
import com.ledga.core.derive.LegacyAutoCategorizer
import com.ledga.core.derive.RuleEngine
import com.ledga.core.derive.SourceSms
import com.ledga.core.model.Categories
import com.ledga.core.model.TxKind
import com.ledga.core.money.Money
import com.ledga.core.parse.MpesaParser
import com.ledga.core.parse.ParseOutcome
import com.ledga.core.parse.ParsedSms
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Opt-in audit against the owner's real v1 export (spec §15.1). Skipped when the file is
 * absent (always in CI). Tolerated v1 defects (exactly these): (1) Fuliza rows with v1 fee 0,
 * (2) KCB/M-Shwari rows where v1 stored the savings balance as wallet balance, (3) <=1% date
 * disagreements (v1 fell back to receive time), (4) v1 balance 0.0 meaning "not stated".
 * Prints counts and masked skeletons only. Run with:
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
                // Tolerated v1 defect #4: v1 encoded "no balance stated" as 0.0 (e.g. airtime-for-other
                // "New balance is"), so a non-zero v2 balance against v1 0 cannot be checked from v1 data.
                v1Balance == 0L -> true
                v1Balance == v2Balance.cents -> true
                // v1 took the KCB/M-Shwari savings balance as the wallet balance (fixed later in v1)
                (row.type == "KCB_MPESA" || row.type == "MSHWARI") && v1Balance == p.savingsBalance?.cents -> true
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

    /**
     * Informational: the v1 export lacks every payment SMS that v1 dropped (every companion is an
     * orphans), so gaps and some breaks are expected. Record the numbers; Phase 7 compares them on device.
     */
    @Test
    fun `balance chain report on the real corpus`() {
        assumeTrue(Corpus.available(), "corpus not present at ${Corpus.file} — skipped")
        val engine = RuleEngine(RuleEngine.systemRules(Instant.EPOCH)) { Categories.seed(it)?.group }
        val txs = Corpus.load().mapIndexedNotNull { i, row ->
            val p = (MpesaParser.parse(row.rawSms, row.timestamp) as? ParseOutcome.Parsed)?.sms ?: return@mapIndexedNotNull null
            Derivation.derive(listOf(SourceSms(i.toLong(), p, row.timestamp, null)), null, engine)
        }
        val report = BalanceChain.check(txs)
        val kindByCode = txs.associate { it.code to it.kind }
        println("=== Balance chain: transactions=${txs.size} checked=${report.checked} gaps=${report.gaps} breaks=${report.breaks.size}")
        report.breaks.groupingBy { kindByCode.getValue(it.code) }.eachCount()
            .entries.sortedByDescending { it.value }
            .forEach { (kind, n) -> println("  breaks at %-20s %d".format(kind, n)) }
        assertTrue(report.checked > 0, "no chain steps could be checked at all")
    }

    @Test
    fun `legacy categoriser reproduces v1 stored categories`() {
        assumeTrue(Corpus.available(), "corpus not present at ${Corpus.file} — skipped")
        val rules = LegacyAutoCategorizer.DEFAULT_RULES
        val rows = Corpus.load().filter { it.categoryId != null }
        var agree = 0
        var drift = 0
        var userChoices = 0
        val disagreements = sortedMapOf<String, Int>()
        for (r in rows) {
            val auto = LegacyAutoCategorizer.categorize(r.type, r.recipientName, r.accountNumber, rules)
            if (auto == r.categoryId) { agree++; continue }
            if (LegacyAutoCategorizer.isUserChoice(r.categoryId, r.type, r.recipientName, r.accountNumber, rules)) userChoices++ else drift++
            disagreements.merge("${r.type}: stored ${r.categoryId}, auto $auto", 1) { a, b -> a + b }
        }
        println("=== Legacy categoriser: rows=${rows.size} agree=$agree drift=$drift userChoices=$userChoices")
        disagreements.forEach { (k, n) -> println("  $k  x$n") }
        assertTrue(agree * 100L >= rows.size * 99L, "v1 parity below 99%: $agree of ${rows.size}")
    }
}
