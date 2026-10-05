package com.ledga.core.derive

import com.ledga.core.model.Categories
import com.ledga.core.model.FlowKind
import com.ledga.core.model.TxKind
import com.ledga.core.money.Money
import com.ledga.core.parse.MpesaParser
import com.ledga.core.parse.ParseOutcome
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DerivationTest {
    private val t0 = Instant.parse("2026-06-09T16:48:10Z")
    private val groupOf: (String) -> com.ledga.core.model.CategoryGroup? = { Categories.seed(it)?.group }
    private val rules = RuleEngine(RuleEngine.systemRules(t0), groupOf)
    private fun ksh(s: String) = Money.parse(s)!!

    private fun derive(vararg bodies: String, override: Override? = null, engine: RuleEngine = rules): DerivedTx =
        Derivation.derive(
            bodies.mapIndexed { i, b ->
                val at = t0.plusSeconds(i.toLong())
                SourceSms(i + 1L, (MpesaParser.parse(b, at) as ParseOutcome.Parsed).sms, at, 1L)
            },
            override,
            engine,
        )

    private val purchase = "TJK4AB12EA Confirmed. Ksh2,500.00 paid to SAMPLE SUPERMARKET. on 9/6/26 at 7:48 PM.New M-PESA balance is Ksh0.00. Transaction cost, Ksh0.00."
    private val companion = "TJK4AB12EA Confirmed. Fuliza M-PESA amount is Ksh 463.00. Access Fee charged Ksh 4.63. Total Fuliza M-PESA outstanding amount is Ksh6,418.36 due on 02/11/26. To check daily charges, Dial *334#OK Select Query Charges"
    private val repay = "TJK4AB12FF Confirmed. Ksh 463.00 from your M-PESA has been used to fully pay your outstanding Fuliza M-PESA. Available Fuliza M-PESA limit is Ksh 1000.00. Your M-PESA balance is 537.00."
    private val kplc = "TJK4AB12FA Confirmed. Ksh1,000.00 sent to KPLC PREPAID for account 37100000001 on 21/3/26 at 3:00 PM New M-PESA balance is Ksh2,000.00. Transaction cost, Ksh0.00."
    private val send = "TJK4AB12FB Confirmed. Ksh500.00 sent to JANE TESTER 0712345111 on 21/3/26 at 1:30 PM. New M-PESA balance is Ksh1,200.00. Transaction cost, Ksh7.00."
    private val bankApp = "TJK4AB12FC Confirmed. You have received Ksh5,000.00 from EXAMPLE BANK LIMITED- APP on 2/4/26 at 9:15 AM. New M-PESA balance is Ksh5,100.00."
    private val globalPay = "TJK4AB12FD Confirmed.You have received Ksh1,500.00 from M-PESA GlobalPay 600200 on 2/4/26 at 9:15 AM New M-PESA balance is Ksh1,600.00."
    private val reversal = "TJK4AB12FE Confirmed. Transaction TJK4AB12FB has been reversed on 21/3/26 at 3:00 PM and Ksh500.00 is credited to your M-PESA account. New M-PESA account balance is Ksh1,700.00."

    @Test
    fun `a Fuliza-funded purchase is one spend counting the full payment plus the access fee`() {
        val tx = derive(purchase, companion)
        assertEquals(TxKind.BUY_GOODS, tx.kind)
        assertEquals(FlowKind.SPEND, tx.flow)
        assertEquals(Categories.OTHER, tx.categoryKey)
        assertEquals(ksh("2500"), tx.amount)
        assertEquals(ksh("4.63"), tx.fee)
        assertEquals(ksh("463"), tx.fulizaDrawn)
        assertEquals(ksh("4.63"), tx.fulizaFee)
        assertEquals(ksh("6418.36"), tx.fulizaOutstanding)
        assertEquals(LocalDate.of(2026, 11, 2), tx.fulizaDueDate)
        assertEquals("SAMPLE SUPERMARKET", tx.counterpartyKey)
        assertEquals(2, tx.smsCount)
        assertEquals(1L, tx.lineId)
        assertFalse(tx.isReversed)
        assertEquals(ksh("2504.63"), Ledger.spent(listOf(tx)))

        val repaid = derive(repay)
        assertEquals(FlowKind.LOAN_REPAY, repaid.flow)
        assertEquals(Money.ZERO, repaid.fulizaOutstanding)
        assertEquals(ksh("1000"), repaid.fulizaLimit)
        assertEquals(ksh("2504.63"), Ledger.spent(listOf(tx, repaid)), "the repayment is not spent again")
    }

    @Test
    fun `system rules categorise paybills by business name`() {
        val tx = derive(kplc)
        assertEquals(TxKind.PAYBILL, tx.kind)
        assertEquals(Categories.ELECTRICITY, tx.categoryKey)
        assertEquals("37100000001", tx.counterpartyAccount)
    }

    @Test
    fun `overrides set category, note, line and hide`() {
        val rent = derive(send, override = Override("TJK4AB12FB", categoryKey = Categories.RENT, note = "March rent", lineId = 2))
        assertEquals(Categories.RENT, rent.categoryKey)
        assertEquals("March rent", rent.note)
        assertEquals(2L, rent.lineId)
        assertTrue("march rent" in rent.searchText)
        assertEquals(ksh("507"), Ledger.spent(listOf(rent)))

        val hidden = derive(send, override = Override("TJK4AB12FB", hidden = true))
        assertTrue(hidden.isHidden)
        assertEquals(Money.ZERO, Ledger.spent(listOf(hidden)))
    }

    @Test
    fun `own-account movements are neither spending nor income, but their fees are spent`() {
        val own = derive(send, override = Override("TJK4AB12FB", ownAccount = true))
        assertEquals(FlowKind.OWN_OUT, own.flow)
        assertEquals(Categories.OWN_ACCOUNTS, own.categoryKey)
        assertEquals(ksh("7"), Ledger.spent(listOf(own)))

        val ownRule = Rule(500, RuleField.NAME_CONTAINS, "EXAMPLE BANK LIMITED", RuleAction.MARK_OWN_ACCOUNT, null, RuleOrigin.USER, createdAt = t0)
        val engine = RuleEngine(RuleEngine.systemRules(t0) + ownRule, groupOf)
        val fromBank = derive(bankApp, engine = engine)
        assertEquals(FlowKind.OWN_IN, fromBank.flow)
        assertEquals(Categories.OWN_ACCOUNTS, fromBank.categoryKey)
        assertEquals(Money.ZERO, Ledger.moneyIn(listOf(fromBank)))

        val overruled = derive(bankApp, override = Override("TJK4AB12FC", ownAccount = false), engine = engine)
        assertEquals(FlowKind.INCOME, overruled.flow)
        assertEquals(Categories.RECEIVED, overruled.categoryKey)
        assertEquals(ksh("5000"), Ledger.moneyIn(listOf(overruled)))
    }

    @Test
    fun `GlobalPay receipts are income`() {
        val tx = derive(globalPay)
        assertEquals(TxKind.GLOBAL_RECEIVE, tx.kind)
        assertEquals(FlowKind.INCOME, tx.flow)
        assertEquals(Categories.RECEIVED, tx.categoryKey)
        assertEquals(ksh("1500"), Ledger.moneyIn(listOf(tx)))
        assertEquals(Money.ZERO, Ledger.spent(listOf(tx)))
    }

    @Test
    fun `a reversed send drops out of spending including its fee`() {
        val a = derive(send)
        val b = derive(reversal)
        assertEquals(FlowKind.REVERSAL_IN, b.flow)
        assertEquals(Categories.REVERSALS, b.categoryKey)
        val reversed = Reversals.reversedCodes(listOf(a, b).map { it.reversesCode })
        val linked = listOf(a, b).map { it.copy(isReversed = it.code in reversed) }
        assertTrue(linked[0].isReversed)
        assertEquals(Money.ZERO, Ledger.spent(linked))
        assertEquals(Money.ZERO, Ledger.moneyIn(linked))
    }

    @Test
    fun `search text covers name, phone, code, account, note and amount spellings`() {
        val s = derive(send).searchText
        listOf("jane tester", "0712345111", "tjk4ab12fb", "500.00").forEach { assertTrue(it in s, "'$it' not in '$s'") }
        val k = derive(kplc).searchText
        listOf("kplc prepaid", "37100000001", "1,000", "1000.00", "1,000.00").forEach { assertTrue(it in k, "'$it' not in '$k'") }
        assertEquals("1,200 naivas", SearchText.normalizeQuery("  1,200   NAIVAS "))
    }

    @Test
    fun `reclassify applies a new rule without re-parsing`() {
        val plain = derive(send, engine = RuleEngine(emptyList(), groupOf))
        assertEquals(Categories.SENT_TO_PEOPLE, plain.categoryKey)
        val rentRule = Rule(600, RuleField.NAME_CONTAINS, "JANE TESTER", RuleAction.SET_CATEGORY, Categories.RENT, RuleOrigin.USER, createdAt = t0)
        val updated = Derivation.reclassify(plain, null, RuleEngine(listOf(rentRule), groupOf))
        assertEquals(Categories.RENT, updated.categoryKey)
        assertEquals(FlowKind.SPEND, updated.flow)
        assertEquals(plain.searchText, updated.searchText)
    }

    @Test
    fun `orphan companion is a FULIZA_ONLY spend of the drawn amount`() {
        val tx = derive(companion)
        assertEquals(TxKind.FULIZA_ONLY, tx.kind)
        assertEquals(FlowKind.SPEND, tx.flow)
        assertEquals(Categories.OTHER, tx.categoryKey)
        assertEquals(ksh("467.63"), Ledger.spent(listOf(tx)))
    }

    @Test
    fun `an override for another code is rejected`() {
        assertFailsWith<IllegalArgumentException> { derive(send, override = Override("TJK4AB12ZZ", note = "x")) }
        assertEquals(1, Derivation.VERSION)
    }
}
