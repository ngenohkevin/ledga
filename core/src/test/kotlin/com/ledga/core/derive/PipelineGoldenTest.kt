package com.ledga.core.derive

import com.ledga.core.model.Categories
import com.ledga.core.model.FlowKind
import com.ledga.core.money.Money
import com.ledga.core.parse.MpesaParser
import com.ledga.core.parse.ParseOutcome
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end: raw synthetic SMS -> MpesaParser -> SourceSms grouped by code -> Derivation with the
 * system rules plus one USER own-account rule -> Reversals -> Ledger and BalanceChain.
 * One line, one month (March 2026, Nairobi time). Every name, number and code is invented.
 */
class PipelineGoldenTest {
    private val nairobi = ZoneId.of("Africa/Nairobi")
    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0): Instant =
        ZonedDateTime.of(2026, 3, day, hour, minute, second, 0, nairobi).toInstant()

    private fun ksh(s: String) = Money.parse(s)!!
    private val groupOf: (String) -> com.ledga.core.model.CategoryGroup? = { Categories.seed(it)?.group }
    private val t0 = Instant.parse("2026-03-01T00:00:00Z")

    private val ownBank = Rule(900, RuleField.NAME_CONTAINS, "EXAMPLE BANK", RuleAction.MARK_OWN_ACCOUNT, null, RuleOrigin.USER, createdAt = t0)
    private val engine = RuleEngine(RuleEngine.systemRules(t0) + ownBank, groupOf)

    private val codeA = "TJK4AB12G1"
    private val codeB = "TJK4AB12G2"
    private val codeC = "TJK4AB12G3"
    private val codeD = "TJK4AB12G4"
    private val codeE = "TJK4AB12G5"
    private val codeRepay = "TJK4AB12G6"
    private val codeRev = "TJK4AB12G7"
    private val codeG = "TJK4AB12G8"

    /** (body, receivedAt) in arrival order. Balances are chained: 5000 -> 4493 -> 3493 -> 0 -> 2000 -> 1532.37 -> 2032.37 -> 1732.37. */
    private val inbox: List<Pair<String, Instant>> = listOf(
        // a. receive 5,000.00
        "$codeA Confirmed.You have received Ksh5,000.00 from SAMPLE EMPLOYER LTD on 2/3/26 at 9:00 AM New M-PESA balance is Ksh5,000.00. Earn interest daily, dial *334#." to at(2, 9, 0, 5),
        // b. send 500.00 + fee 7.00, then the same code again with a different promo tail
        "$codeB Confirmed. Ksh500.00 sent to JANE TESTER 0712345111 on 3/3/26 at 10:00 AM. New M-PESA balance is Ksh4,493.00. Transaction cost, Ksh7.00. Amount you can transact within the day is 499,493.00. Earn interest daily, dial *334#." to at(3, 10, 0, 5),
        "$codeB Confirmed. Ksh500.00 sent to JANE TESTER 0712345111 on 3/3/26 at 10:00 AM. New M-PESA balance is Ksh4,493.00. Transaction cost, Ksh7.00. Amount you can transact within the day is 499,493.00. Save more with Sample Bank." to at(3, 10, 0, 7),
        // c. paybill to KPLC PREPAID
        "$codeC Confirmed. Ksh1,000.00 sent to KPLC PREPAID for account 37100000001 on 5/3/26 at 11:00 AM New M-PESA balance is Ksh3,493.00. Transaction cost, Ksh0.00." to at(5, 11, 0, 5),
        // d. Fuliza-funded buy goods: wallet 3,493.00 + drawn 463.00 covers 3,956.00; companion arrives 3 s later
        "$codeD Confirmed. Ksh3,956.00 paid to SAMPLE SUPERMARKET. on 7/3/26 at 6:00 PM.New M-PESA balance is Ksh0.00. Transaction cost, Ksh0.00." to at(7, 18, 0, 5),
        "$codeD Confirmed. Fuliza M-PESA amount is Ksh 463.00. Access Fee charged Ksh 4.63. Total Fuliza M-PESA outstanding amount is Ksh467.63 due on 06/04/26. To check daily charges, Dial *334#OK Select Query Charges" to at(7, 18, 0, 8),
        // e. receive 2,000.00, then the full auto-repay (no body date: occurredAt = receivedAt)
        "$codeE Confirmed.You have received Ksh2,000.00 from SAMPLE EMPLOYER LTD on 9/3/26 at 8:00 AM New M-PESA balance is Ksh2,000.00." to at(9, 8, 0, 5),
        "$codeRepay Confirmed. Ksh 467.63 from your M-PESA has been used to fully pay your outstanding Fuliza M-PESA. Available Fuliza M-PESA limit is Ksh 1000.00. Your M-PESA balance is 1532.37." to at(9, 8, 30, 0),
        // f. reversal of (b)
        "$codeRev Confirmed. Transaction $codeB has been reversed on 10/3/26 at 3:00 PM and Ksh500.00 is credited to your M-PESA account. New M-PESA account balance is Ksh2,032.37." to at(10, 15, 0, 5),
        // g. paybill to the user's own bank
        "$codeG Confirmed. Ksh300.00 sent to EXAMPLE BANK LIMITED for account 123456 on 12/3/26 at 1:00 PM New M-PESA balance is Ksh1,732.37. Transaction cost, Ksh0.00." to at(12, 13, 0, 5),
    )

    private val derived: List<DerivedTx> by lazy {
        val sources = inbox.mapIndexed { i, (body, receivedAt) ->
            val outcome = MpesaParser.parse(body, receivedAt)
            assertTrue(outcome is ParseOutcome.Parsed, "message $i did not parse: $outcome")
            SourceSms(i + 1L, outcome.sms, receivedAt, 1L)
        }
        val txs = sources.groupBy { it.parsed.code }.values.map { Derivation.derive(it, null, engine) }
        val reversed = Reversals.reversedCodes(txs.map { it.reversesCode })
        txs.map { it.copy(isReversed = it.code in reversed) }
    }

    private fun tx(code: String) = derived.single { it.code == code }

    @Test
    fun `one transaction per code and the resend is counted once`() {
        assertEquals(8, derived.size)
        assertEquals(2, tx(codeB).smsCount)
        assertEquals(2, tx(codeD).smsCount)
        assertEquals(listOf(1), derived.filter { it.code !in setOf(codeB, codeD) }.map { it.smsCount }.distinct())
    }

    @Test
    fun `classification, reversal and own-account handling`() {
        assertEquals(Categories.ELECTRICITY, tx(codeC).categoryKey)
        assertEquals(FlowKind.OWN_OUT, tx(codeG).flow)
        assertEquals(Categories.OWN_ACCOUNTS, tx(codeG).categoryKey)
        assertTrue(tx(codeB).isReversed)
        assertFalse(tx(codeRev).isReversed)
        assertEquals(codeB, tx(codeRev).reversesCode)
        assertEquals(ksh("4.63"), tx(codeD).fee)
        assertEquals(ksh("463"), tx(codeD).fulizaDrawn)
    }

    @Test
    fun `ledger spent and money in follow the single spending definition`() {
        // SPEND: KPLC 1,000.00 + supermarket 3,956.00 + Fuliza access fee 4.63.
        // Excluded: the reversed send and its 7.00 fee, the own-account paybill (300.00), the Fuliza repayment.
        assertEquals(ksh("4960.63"), Ledger.spent(derived))
        // INCOME: 5,000.00 + 2,000.00. The reversal credit is not income.
        assertEquals(ksh("7000"), Ledger.moneyIn(derived))
    }

    @Test
    fun `balance chain has no breaks and no gaps`() {
        val report = BalanceChain.check(derived)
        assertEquals(emptyList(), report.breaks)
        assertEquals(0, report.gaps)
        // Eight transactions on one line. The first (receive) only seeds the running balance;
        // each of the other seven (send, paybill, Fuliza buy, receive, auto-repay, reversal, own paybill)
        // is checked against the previous balance + its wallet movement, so 7 steps are checked.
        assertEquals(7, report.checked)
        assertEquals(1, report.lines.size)
    }
}
