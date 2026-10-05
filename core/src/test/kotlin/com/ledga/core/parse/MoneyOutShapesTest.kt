package com.ledga.core.parse

import com.ledga.core.model.TxKind
import com.ledga.core.money.Money
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** All fixtures are synthetic: invented names, numbers, codes and amounts reproducing real shapes. */
class MoneyOutShapesTest {
    private val received = Instant.parse("2026-03-21T10:45:12Z")
    private fun ksh(s: String) = Money.parse(s)!!
    private fun parse(body: String): ParsedSms {
        val outcome = MpesaParser.parse(body, received)
        assertIs<ParseOutcome.Parsed>(outcome, "expected Parsed for: $body, got $outcome")
        return outcome.sms
    }

    @Test
    fun `send to a person with a phone`() {
        val s = parse(
            "TJK4AB12CD Confirmed. Ksh500.00 sent to JANE TESTER 0712345111 on 21/3/26 at 1:30 PM. New M-PESA balance is Ksh1,200.00. " +
                "Transaction cost, Ksh7.00. Amount you can transact within the day is 499,493.00. Earn interest daily, dial *334#.",
        )
        assertEquals("TJK4AB12CD", s.code)
        assertEquals(TxKind.SEND, s.kind)
        assertEquals("send.phone", s.shape)
        assertEquals(ksh("500"), s.amount)
        assertEquals(ksh("7"), s.fee)
        assertEquals(ksh("1200"), s.balance)
        assertEquals(Counterparty("JANE TESTER", "0712345111", null, null), s.counterparty)
        assertEquals(Instant.parse("2026-03-21T10:30:00Z"), s.occurredAt)
        assertTrue(s.occurredAtFromBody)
        assertFalse(s.occurredAtApprox)
        assertNull(s.fuliza)
        assertNull(s.reversesCode)
    }

    @Test
    fun `send to a till or pochi without a phone, digit-less code`() {
        val s = parse(
            "TJKABCDEFG Confirmed. Ksh250.00 sent to SAMPLE STORES on 21/3/26 at 1:30 PM. New M-PESA balance is Ksh950.00. Transaction cost, Ksh0.00.",
        )
        assertEquals("TJKABCDEFG", s.code)
        assertEquals("send.name", s.shape)
        assertEquals(Counterparty("SAMPLE STORES", null, null, null), s.counterparty)
    }

    @Test
    fun `amount-first send with doubled spacing and a four digit year`() {
        val s = parse(
            "TJK4AB12CF Confirmed. You have sent Ksh1,503.17 to Hustler Fund on 14/05/2024  at 04:41 PM. New MPESA balance is Ksh8,212.40.",
        )
        assertEquals(TxKind.SEND, s.kind)
        assertEquals(ksh("1503.17"), s.amount)
        assertEquals("HUSTLER FUND", s.counterparty?.name)
        assertEquals(ksh("8212.40"), s.balance)
        assertEquals(Instant.parse("2024-05-14T13:41:00Z"), s.occurredAt)
    }

    @Test
    fun `paybill in the current for-account format`() {
        val s = parse(
            "TJK4AB12CG Confirmed. Ksh1,000.00 sent to SAMPLE POWER PREPAID for account 37100000001 on 21/3/26 at 3:00 PM New M-PESA balance is Ksh2,000.00. " +
                "Transaction cost, Ksh0.00.Amount you can transact within the day is 499,000.00. Save frequent paybills for quick payment on M-PESA app https://example.invalid/x",
        )
        assertEquals(TxKind.PAYBILL, s.kind)
        assertEquals("paybill", s.shape)
        assertEquals(Counterparty("SAMPLE POWER PREPAID", null, "37100000001", null), s.counterparty)
        assertEquals(ksh("2000"), s.balance)
        assertEquals(Money.ZERO, s.fee)
    }

    @Test
    fun `paybill whose account repeats the name, and paybill with an empty account`() {
        val bundles = parse(
            "TJK4AB12CH Confirmed. Ksh20.00 sent to SAFARICOM DATA BUNDLES for account SAFARICOM DATA BUNDLES on 4/3/26 at 3:18 PM. New M-PESA balance is Ksh480.00. Transaction cost, Ksh0.00.",
        )
        assertEquals(Counterparty("SAFARICOM DATA BUNDLES", null, "SAFARICOM DATA BUNDLES", null), bundles.counterparty)
        val empty = parse(
            "TJK4AB12CJ Confirmed. Ksh99.00 sent to SAMPLE TELCO LIMITED for account on 4/3/26 at 3:18 PM. New M-PESA balance is Ksh381.00. Transaction cost, Ksh0.00.",
        )
        assertEquals(TxKind.PAYBILL, empty.kind)
        assertEquals(Counterparty("SAMPLE TELCO LIMITED", null, null, null), empty.counterparty)
    }

    @Test
    fun `card paybill with plus-digits in the account is not a global send`() {
        val s = parse(
            "TJK4AB12CK Confirmed. Ksh650.00 sent to M-PESA CARD for account Cloud Hosting online +16505550100 US on 4/4/26 at 9:10 AM New M-PESA balance is Ksh3,350.00. Transaction cost, Ksh13.00.",
        )
        assertEquals(TxKind.PAYBILL, s.kind)
        assertEquals(Counterparty("M-PESA CARD", null, "Cloud Hosting online +16505550100 US", null), s.counterparty)
        assertNull(s.destinationCountry)
    }

    @Test
    fun `paybill in the old Account Number format`() {
        val s = parse(
            "TJK4AB12CL Confirmed. Ksh2,500.00 paid to SAMPLE POWER PREPAID. Account Number 12345678. on 21/3/26 at 3:00 PM. New M-PESA balance is Ksh1,000.00. Transaction cost, Ksh0.00.",
        )
        assertEquals(TxKind.PAYBILL, s.kind)
        assertEquals("paybill.old", s.shape)
        assertEquals(Counterparty("SAMPLE POWER PREPAID", null, "12345678", null), s.counterparty)
    }

    @Test
    fun `buy goods, including names with on, digits, dots and a double full stop`() {
        val s = parse(
            "TJK4AB12CM Confirmed. Ksh1,200.00 paid to SAMPLE SUPERMARKET. on 21/3/26 at 2:15 PM.New M-PESA balance is Ksh3,500.00. Transaction cost, Ksh0.00. Amount you can transact within the day is 498,800.00.",
        )
        assertEquals(TxKind.BUY_GOODS, s.kind)
        assertEquals("SAMPLE SUPERMARKET", s.counterparty?.name)
        assertEquals(ksh("3500"), s.balance)

        fun nameOf(name: String) = parse(
            "TJK4AB12CZ Confirmed. Ksh180.00 paid to $name on 5/4/26 at 8:05 AM.New M-PESA balance is Ksh820.00. Transaction cost, Ksh0.00.",
        ).counterparty?.name
        assertEquals("CAFE ON THE GO", nameOf("Cafe on the Go.."))
        assertEquals("Q7 KIOSK", nameOf("Q7 KIOSK."))
        assertEquals("SAMPLE ENERGY 2.0", nameOf("SAMPLE ENERGY 2.0."))
    }

    @Test
    fun `global send with destination country`() {
        val s = parse(
            "TJK4AB12CN Confirmed. Ksh5,000.00 sent to JOHN SAMPLE +447700900123 (United Kingdom) via M-PESA Global on 21/3/26 at 1:30 PM. New M-PESA balance is Ksh10,000.00. Transaction cost, Ksh150.00.",
        )
        assertEquals(TxKind.GLOBAL_SEND, s.kind)
        assertEquals(Counterparty("JOHN SAMPLE", "+447700900123", null, null), s.counterparty)
        assertEquals("United Kingdom", s.destinationCountry)
        assertEquals(ksh("150"), s.fee)
    }

    @Test
    fun `agent withdrawal in the glued, dash and named formats`() {
        val glued = parse(
            "TJK4AB12CP Confirmed.on 12/3/26 at 6:42 PMWithdraw Ksh4,500.00 from 012345 - SAMPLE AGENCIES Main Street Town New M-PESA balance is Ksh2,310.60. " +
                "Transaction cost, Ksh77.00. Amount you can transact within the day is 495,500.00.",
        )
        assertEquals(TxKind.WITHDRAW_AGENT, glued.kind)
        assertEquals(Counterparty("SAMPLE AGENCIES MAIN STREET TOWN", null, null, "012345"), glued.counterparty)
        assertEquals(ksh("4500"), glued.amount)
        assertEquals(ksh("77"), glued.fee)
        assertEquals(ksh("2310.60"), glued.balance)
        assertEquals(Instant.parse("2026-03-12T15:42:00Z"), glued.occurredAt)

        val dash = parse(
            "TJK4AB12CQ Confirmed.You have withdrawn Ksh1,000.00 from 543210 - SAMPLE AGENT SHOP on 21/3/26 at 4:00 PM.New M-PESA balance is Ksh500.00. Transaction cost, Ksh28.00.",
        )
        assertEquals(Counterparty("SAMPLE AGENT SHOP", null, null, "543210"), dash.counterparty)

        val named = parse(
            "TJK4AB12CR Confirmed.You have withdrawn Ksh1,000.00 from SAMPLE AGENT 543210 on 21/3/26 at 4:00 PM.New M-PESA balance is Ksh500.00. Transaction cost, Ksh28.00.",
        )
        assertEquals(Counterparty("SAMPLE AGENT", null, null, "543210"), named.counterparty)
    }

    @Test
    fun `ATM withdrawal`() {
        val s = parse(
            "TJK4AB12CS Confirmed. You have withdrawn Ksh5,000.00 from an ATM on 21/3/26 at 4:30 PM. New M-PESA balance is Ksh2,000.00. Transaction cost, Ksh34.00.",
        )
        assertEquals(TxKind.WITHDRAW_ATM, s.kind)
        assertEquals("ATM", s.counterparty?.name)
    }

    @Test
    fun `airtime for self in both phrasings and for another number`() {
        val bought = parse(
            "TJK4AB12CT confirmed.You bought Ksh150.00 of airtime on 4/3/26 at 3:18 PM.New M-PESA balance is Ksh4,180.35. Transaction cost, Ksh0.00. Amount you can transact within the day is 499,800.00.",
        )
        assertEquals(TxKind.AIRTIME_SELF, bought.kind)
        assertNull(bought.counterparty)
        assertEquals(ksh("4180.35"), bought.balance)

        val purchased = parse(
            "TJK4AB12CU Confirmed. Ksh100.00 of airtime purchased on 21/3/26 at 12:00 PM.New M-PESA balance is Ksh7,400.00. Transaction cost, Ksh0.00.",
        )
        assertEquals(TxKind.AIRTIME_SELF, purchased.kind)
        assertEquals(Instant.parse("2026-03-21T09:00:00Z"), purchased.occurredAt)

        val other = parse(
            "TJK4AB12CV confirmed.You bought Ksh50.00 of airtime for 0712345111 on 2/3/26 at 3:10 PM.New balance is Ksh950.00. Transaction cost, Ksh0.00.",
        )
        assertEquals(TxKind.AIRTIME_OTHER, other.kind)
        assertEquals("0712345111", other.counterparty?.phone)
        assertEquals(ksh("950"), other.balance)
    }

    @Test
    fun `old combined Fuliza send carries Fuliza facts on the payment itself`() {
        val s = parse(
            "TJK4AB12CW Confirmed. Ksh500.00 sent to JANE TESTER 0712345111 on 21/3/26 at 1:30 PM. New M-PESA balance is Ksh0.00. " +
                "Fuliza M-PESA amount is Ksh500.00. Fuliza M-PESA outstanding amount is Ksh500.00.",
        )
        assertEquals(TxKind.SEND, s.kind)
        assertEquals(Money.ZERO, s.balance)
        assertEquals(FulizaFacts(drawn = ksh("500"), outstanding = ksh("500")), s.fuliza)
    }

    @Test
    fun `available Fuliza limit rides along on a payment, double spaces in names collapse`() {
        val s = parse(
            "TJK4AB12CX Confirmed. Ksh40.00 sent to SAMPLE PARKING  TOWN MALL for account 120000001 on 9/6/26 at 7:48 PM New M-PESA balance is Ksh0.00. " +
                "Transaction cost, Ksh0.00. Available Fuliza M-PESA limit is Ksh 970.00.",
        )
        assertEquals(Counterparty("SAMPLE PARKING TOWN MALL", null, "120000001", null), s.counterparty)
        assertEquals(ksh("970"), s.fuliza?.availableLimit)
    }

    @Test
    fun `an impossible body date falls back to received time and is flagged approximate`() {
        val s = parse(
            "TJK4AB12CY Confirmed. Ksh500.00 sent to JANE TESTER 0712345111 on 31/2/26 at 1:30 PM. New M-PESA balance is Ksh700.00. Transaction cost, Ksh7.00.",
        )
        assertEquals(received, s.occurredAt)
        assertFalse(s.occurredAtFromBody)
        assertTrue(s.occurredAtApprox)
    }
}
