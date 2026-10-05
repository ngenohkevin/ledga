package com.ledga.core.parse

import com.ledga.core.model.TxKind
import com.ledga.core.money.Money
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/** All fixtures are synthetic: invented names, numbers, codes and amounts reproducing real shapes. */
class MoneyInShapesTest {
    private val received = Instant.parse("2026-03-21T10:45:12Z")
    private fun ksh(s: String) = Money.parse(s)!!
    private fun parse(body: String): ParsedSms {
        val outcome = MpesaParser.parse(body, received)
        assertIs<ParseOutcome.Parsed>(outcome, "expected Parsed for: $body, got $outcome")
        return outcome.sms
    }

    @Test
    fun `receipt from a person with a full phone`() {
        val s = parse(
            "TJK4AB12DA Confirmed.You have received Ksh2,000.00 from JANE TESTER 0712345111 on 21/3/26 at 11:00 AM New M-PESA balance is Ksh7,500.00. Earn interest daily, dial *334#.",
        )
        assertEquals(TxKind.RECEIVE, s.kind)
        assertEquals("receive", s.shape)
        assertEquals(ksh("2000"), s.amount)
        assertEquals(Money.ZERO, s.fee)
        assertEquals(ksh("7500"), s.balance)
        assertEquals(Counterparty("JANE TESTER", "0712345111", null, null), s.counterparty)
        assertEquals(Instant.parse("2026-03-21T08:00:00Z"), s.occurredAt)
    }

    @Test
    fun `receipt from a masked phone keeps the same counterparty key as a full one`() {
        val s = parse(
            "TJK4AB12DB Confirmed.You have received Ksh2,000.00 from JANE TESTER 0712***111 on 21/3/26 at 11:00 AM New M-PESA balance is Ksh7,500.00.",
        )
        assertEquals("0712***111", s.counterparty?.phone)
        assertEquals("JANE TESTER|0712111", s.counterparty?.key)
    }

    @Test
    fun `receipts from bank apps and B2C accounts are named`() {
        val app = parse(
            "TJK4AB12DC Confirmed. You have received Ksh5,000.00 from EXAMPLE BANK LIMITED- APP on 2/4/26 at 9:15 AM. New M-PESA balance is Ksh5,100.00. Buy goods with M-PESA.",
        )
        assertEquals(Counterparty("EXAMPLE BANK LIMITED", null, null, null), app.counterparty)

        val b2c = parse(
            "TJK4AB12DD Confirmed.You have received Ksh3,000.00 from SAMPLE BANK LIMITED B2C 600100 on 2/4/26 at 9:15 AM New M-PESA balance is Ksh3,100.00.",
        )
        assertEquals(Counterparty("SAMPLE BANK LIMITED B2C", null, null, "600100"), b2c.counterparty)
    }

    @Test
    fun `GlobalPay receipt is money in, not an international send`() {
        val s = parse(
            "TJK4AB12DE Confirmed.You have received Ksh1,500.00 from M-PESA GlobalPay 600200 on 2/4/26 at 9:15 AM New M-PESA balance is Ksh1,600.00.",
        )
        assertEquals(TxKind.GLOBAL_RECEIVE, s.kind)
        assertEquals("global.receive", s.shape)
        assertEquals(Counterparty("M-PESA GLOBALPAY", null, null, "600200"), s.counterparty)
    }

    @Test
    fun `receipt from a named business without a number`() {
        val s = parse(
            "TJK4AB12DF Confirmed. You have received Ksh1,000.00 from Hustler Fund on 14/05/2024 at 04:41 PM. New MPESA balance is Ksh1,050.00.",
        )
        assertEquals(TxKind.RECEIVE, s.kind)
        assertEquals("HUSTLER FUND", s.counterparty?.name)
        assertEquals(ksh("1050"), s.balance)
    }

    @Test
    fun `cash deposits in both formats`() {
        val give = parse(
            "TJK4AB12DG Confirmed. On 16/7/25 at 10:25 AM Give Ksh400.00 cash to SAMPLE AGENCIES Main Street Town New M-PESA balance is Ksh400.00. You can now access M-PESA via *334#",
        )
        assertEquals(TxKind.DEPOSIT, give.kind)
        assertEquals("deposit.givecash", give.shape)
        assertEquals("SAMPLE AGENCIES MAIN STREET TOWN", give.counterparty?.name)
        assertEquals(Instant.parse("2025-07-16T07:25:00Z"), give.occurredAt)

        val deposited = parse(
            "TJK4AB12DH Confirmed.You have deposited Ksh5,000.00 to your M-PESA account on 21/3/26 at 10:00 AM.New M-PESA balance is Ksh5,500.00.",
        )
        assertEquals(TxKind.DEPOSIT, deposited.kind)
        assertNull(deposited.counterparty)
        assertEquals(ksh("5500"), deposited.balance)
    }

    @Test
    fun `Fuliza companion parses as FULIZA_ONLY with the drawn amount and exact received time`() {
        val s = parse(
            "TJK4AB12DJ Confirmed. Fuliza M-Pesa amount is Ksh 45.00. Access Fee charged Ksh 0.45. Total Fuliza M-Pesa outstanding amount is Ksh2875.30 due on 09/07/26. " +
                "To check daily charges, Dial *334#OK Select Query Charges",
        )
        assertEquals(TxKind.FULIZA_ONLY, s.kind)
        assertEquals("fuliza.companion", s.shape)
        assertEquals(ksh("45"), s.amount)
        assertEquals(Money.ZERO, s.fee)
        assertNull(s.balance)
        assertEquals(received, s.occurredAt)
        assertFalse(s.occurredAtFromBody)
        assertFalse(s.occurredAtApprox)
        assertEquals(FulizaFacts(ksh("45"), ksh("0.45"), ksh("2875.30"), null, LocalDate.of(2026, 7, 9)), s.fuliza)

        val interest = parse(
            "TJK4AB12DK Confirmed. Fuliza M-PESA amount is Ksh 840.00. Interest charged Ksh 8.40. Total Fuliza M-PESA outstanding amount is Ksh 1325.60 due on 18/08/25. To check daily charges, Dial *334#OK Select Fuliza M-PESA to Query Charges.",
        )
        assertEquals(ksh("8.40"), interest.fuliza?.accessFee)
    }

    @Test
    fun `Fuliza auto-repay full and partial`() {
        val full = parse(
            "TJK4AB12DL  Confirmed. Ksh 1612.45 from your M-PESA has been used to fully pay your outstanding Fuliza M-PESA. Available Fuliza M-PESA limit is Ksh 7300.00. Your M-PESA balance is 7360.55.",
        )
        assertEquals(TxKind.FULIZA_REPAY_AUTO, full.kind)
        assertEquals(ksh("1612.45"), full.amount)
        assertEquals(ksh("7360.55"), full.balance)
        assertEquals(FulizaFacts(outstanding = Money.ZERO, availableLimit = ksh("7300")), full.fuliza)
        assertFalse(full.occurredAtApprox)

        val partial = parse(
            "TJK4AB12DM Confirmed. Ksh 150.00 from your M-PESA has been used to partially pay your outstanding Fuliza M-PESA. Your available Fuliza M-PESA limit is Ksh 6250.40. M-PESA balance is Ksh0.00.",
        )
        assertEquals(FulizaFacts(availableLimit = ksh("6250.40")), partial.fuliza)
        assertEquals(Money.ZERO, partial.balance)
    }

    @Test
    fun `Fuliza manual repayment and Fuliza reversal`() {
        val manual = parse(
            "TJK4AB12DN Confirmed. You have paid Ksh200.00 to Fuliza M-PESA on 21/3/26 at 2:00 PM. Fuliza M-PESA outstanding amount is Ksh300.00.",
        )
        assertEquals(TxKind.FULIZA_REPAY_MANUAL, manual.kind)
        assertEquals(ksh("300"), manual.fuliza?.outstanding)
        assertNull(manual.balance)

        val reversal = parse(
            "TJK4AB12DP Confirmed. Fuliza M-PESA of Ksh500.00 has been reversed on 21/3/26 at 3:00 PM. Fuliza M-PESA outstanding amount is Ksh0.00.",
        )
        assertEquals(TxKind.FULIZA_REVERSAL, reversal.kind)
        assertEquals(ksh("500"), reversal.amount)
        assertEquals(Money.ZERO, reversal.fuliza?.outstanding)
    }

    @Test
    fun `KCB M-PESA out and in, with savings balance kept apart`() {
        val out = parse(
            "TJK4AB12DQ Confirmed. Ksh4,000.00 transfered to KCB M-PESA account on 3/6/26 at 8:20 AM. New M-PESA balance is Ksh10,000.00, new KCB M-PESA Saving account balance is Ksh4,000.42.",
        )
        assertEquals(TxKind.SAVINGS_OUT, out.kind)
        assertEquals(ksh("10000"), out.balance)
        assertEquals(ksh("4000.42"), out.savingsBalance)

        val inn = parse(
            "TJK4AB12DR Confirmed. You have transfered Ksh12,000.00 from your KCB M-PESA account on 3/6/26 at 8:20 AM. KCB M-PESA Account balance is Ksh0.42. New M-PESA balance is Ksh12,000.00.",
        )
        assertEquals(TxKind.SAVINGS_IN, inn.kind)
        assertEquals(ksh("12000"), inn.amount)
        assertEquals(ksh("12000"), inn.balance)
        assertEquals(ksh("0.42"), inn.savingsBalance)
    }

    @Test
    fun `M-Shwari out and in`() {
        val out = parse(
            "TJK4AB12DS Confirmed. Ksh1,000.00 transferred to M-Shwari account on 21/3/26 at 5:00 PM. M-Shwari balance is Ksh3,000.00. New M-PESA balance is Ksh500.00.",
        )
        assertEquals(TxKind.SAVINGS_OUT, out.kind)
        assertEquals(ksh("500"), out.balance)
        assertEquals(ksh("3000"), out.savingsBalance)

        listOf("from M-Shwari account", "from your M-Shwari account").forEach { phrase ->
            val inn = parse(
                "TJK4AB12DT Confirmed. Ksh1,000.00 transferred $phrase on 21/3/26 at 5:00 PM. M-Shwari balance is Ksh2,000.00. New M-PESA balance is Ksh1,500.00.",
            )
            assertEquals(TxKind.SAVINGS_IN, inn.kind, phrase)
        }
    }

    @Test
    fun `reversals link to the reversed code in all three formats`() {
        val successful = parse(
            "TJK4AB12DU confirmed. Reversal of transaction TJK4AB12CD has been successfully reversed on 6/8/24 at 1:10 PM and Ksh350.00 is credited to your M-PESA account. New M-PESA account balance is Ksh1,915.00.",
        )
        assertEquals(TxKind.REVERSAL, successful.kind)
        assertEquals("TJK4AB12CD", successful.reversesCode)
        assertEquals(ksh("350"), successful.amount)
        assertEquals(ksh("1915"), successful.balance)
        assertEquals(Instant.parse("2024-08-06T10:10:00Z"), successful.occurredAt)

        val credited = parse(
            "TJK4AB12DV Confirmed. Transaction TJK4AB12CE has been reversed on 21/3/26 at 3:00 PM and Ksh750.00 is credited to your M-PESA account. New M-PESA account balance is Ksh2,750.00.",
        )
        assertEquals("TJK4AB12CE", credited.reversesCode)
        assertEquals(ksh("750"), credited.amount)

        val short = parse("TJK4AB12DW Confirmed. Transaction TJK4AB12CF has been reversed. Your account balance is Ksh2,000.00.")
        assertEquals("TJK4AB12CF", short.reversesCode)
        assertEquals(Money.ZERO, short.amount)
        assertEquals(ksh("2000"), short.balance)
        assertFalse(short.occurredAtApprox)
    }
}
