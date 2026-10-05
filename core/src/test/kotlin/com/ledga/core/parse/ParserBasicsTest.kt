package com.ledga.core.parse

import com.ledga.core.money.Money
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParserBasicsTest {
    private val received = Instant.parse("2026-03-21T10:45:12Z")
    private fun ksh(s: String) = Money.parse(s)!!

    @Test
    fun `messages without a transaction code are ignored`() {
        listOf(
            "Confirmed. Ksh500.00 sent to JANE TESTER on 21/3/26 at 1:30 PM.",
            "Dear Customer, dial *334# to opt in to Fuliza M-PESA.",
            "tjk4ab12cd Confirmed. Ksh500.00 sent to JANE TESTER on 21/3/26 at 1:30 PM.",
            "1234567890 Confirmed. Ksh500.00 sent to JANE TESTER on 21/3/26 at 1:30 PM.",
        ).forEach {
            assertEquals(ParseOutcome.Ignored(IgnoreReason.NO_CODE), MpesaParser.parse(it, received), it)
        }
    }

    @Test
    fun `failed transactions and balance checks are ignored`() {
        assertEquals(
            ParseOutcome.Ignored(IgnoreReason.FAILED),
            MpesaParser.parse("TJK4AB12CD Failed. Insufficient funds in your M-PESA account to send Ksh500.00.", received),
        )
        assertEquals(
            ParseOutcome.Ignored(IgnoreReason.BALANCE_CHECK),
            MpesaParser.parse(
                "TJK4AB12CD Confirmed. Your account balance was: M-PESA Account : Ksh612.00 Business Account : Ksh0.00 on 17/3/26 at 2:05 PM. Transaction cost, Ksh0.00.",
                received,
            ),
        )
    }

    @Test
    fun `a code-bearing message no shape understands is unreadable, digit-less codes included`() {
        val outcome = MpesaParser.parse("TJKABCDEFG Confirmed. Some new M-PESA feature we don't know about.", received)
        assertIs<ParseOutcome.Unreadable>(outcome)
    }

    @Test
    fun `sender check and version`() {
        assertTrue(MpesaParser.isMpesaSender("M-PESA"))
        assertEquals(1, MpesaParser.VERSION)
    }

    @Test
    fun `wallet balance prefers the wallet figure and supports every phrasing`() {
        assertEquals(ksh("1200"), Extract.walletBalance("New M-PESA balance is Ksh1,200.00. Transaction cost, Ksh7.00."))
        assertEquals(ksh("7360.55"), Extract.walletBalance("Available Fuliza M-PESA limit is Ksh 7300.00. Your M-PESA balance is 7360.55."))
        assertEquals(ksh("0"), Extract.walletBalance("Your available Fuliza M-PESA limit is Ksh 6250.40. M-PESA balance is Ksh0.00."))
        assertEquals(ksh("8212.40"), Extract.walletBalance("on 14/05/2024 at 04:41 PM. New MPESA balance is Ksh8,212.40."))
        assertEquals(ksh("950"), Extract.walletBalance("on 2/3/26 at 3:10 PM.New balance is Ksh950.00. Transaction cost, Ksh0.00."))
        assertEquals(ksh("1915"), Extract.walletBalance("is credited to your M-PESA account. New M-PESA account balance is Ksh1,915.00."))
        assertEquals(ksh("2000"), Extract.walletBalance("Transaction TJK4AB12CF has been reversed. Your account balance is Ksh2,000.00."))
        assertEquals(
            ksh("12000"),
            Extract.walletBalance("KCB M-PESA Account balance is Ksh0.42. New M-PESA balance is Ksh12,000.00."),
        )
        assertEquals(
            ksh("10000"),
            Extract.walletBalance("New M-PESA balance is Ksh10,000.00, new KCB M-PESA Saving account balance is Ksh4,000.42."),
        )
        assertNull(Extract.walletBalance("Fuliza M-PESA amount is Ksh 45.00. Total Fuliza M-PESA outstanding amount is Ksh2875.30 due on 09/07/26."))
        assertNull(Extract.walletBalance("Amount you can transact within the day is 499,500.00."))
    }

    @Test
    fun `fee is the transaction cost or zero`() {
        assertEquals(ksh("77"), Extract.fee("New M-PESA balance is Ksh2,310.60. Transaction cost, Ksh77.00."))
        assertEquals(Money.ZERO, Extract.fee("Transaction cost, Ksh0.00.Amount you can transact within the day is 499,000.00."))
        assertEquals(Money.ZERO, Extract.fee("You have received Ksh2,000.00 from JANE TESTER"))
    }

    @Test
    fun `fuliza facts in all wordings`() {
        val interest = Extract.fuliza(
            "Fuliza M-PESA amount is Ksh 840.00. Interest charged Ksh 8.40. Total Fuliza M-PESA outstanding amount is Ksh 1325.60 due on 18/08/25. To check daily charges, Dial *334#OK",
        )
        assertEquals(FulizaFacts(ksh("840"), ksh("8.40"), ksh("1325.60"), null, LocalDate.of(2025, 8, 18)), interest)

        val accessMixedCase = Extract.fuliza(
            "Fuliza M-Pesa amount is Ksh 45.00. Access Fee charged Ksh 0.45. Total Fuliza M-Pesa outstanding amount is Ksh2875.30 due on 09/07/26.",
        )
        assertEquals(FulizaFacts(ksh("45"), ksh("0.45"), ksh("2875.30"), null, LocalDate.of(2026, 7, 9)), accessMixedCase)

        assertEquals(ksh("7300"), Extract.fuliza("Available Fuliza M-PESA limit is Ksh 7300.00.")!!.availableLimit)
        assertEquals(ksh("6250.40"), Extract.fuliza("Your available Fuliza M-PESA limit is Ksh 6250.40.")!!.availableLimit)
        assertNull(Extract.fuliza("Ksh500.00 sent to JANE TESTER 0712345111 on 21/3/26 at 1:30 PM."))
    }

    @Test
    fun `savings balances are separate from the wallet balance`() {
        assertEquals(ksh("0.42"), Extract.savingsBalance("KCB M-PESA Account balance is Ksh0.42. New M-PESA balance is Ksh12,000.00."))
        assertEquals(ksh("4000.42"), Extract.savingsBalance("New M-PESA balance is Ksh10,000.00, new KCB M-PESA Saving account balance is Ksh4,000.42."))
        assertEquals(ksh("3000"), Extract.savingsBalance("M-Shwari balance is Ksh3,000.00. New M-PESA balance is Ksh500.00."))
        assertNull(Extract.savingsBalance("New M-PESA balance is Ksh500.00."))
    }

    @Test
    fun `fuliza facts merge field by field, newer wins`() {
        val old = FulizaFacts(drawn = ksh("500"), outstanding = ksh("500"))
        val newer = FulizaFacts(accessFee = ksh("5"), outstanding = ksh("505"), dueDate = LocalDate.of(2026, 4, 20))
        assertEquals(FulizaFacts(ksh("500"), ksh("5"), ksh("505"), null, LocalDate.of(2026, 4, 20)), old.mergedWith(newer))
        assertEquals(old, old.mergedWith(null))
        assertTrue(FulizaFacts().isEmpty)
    }
}
