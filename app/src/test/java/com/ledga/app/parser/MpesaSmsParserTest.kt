package com.ledga.app.parser

import com.ledga.app.data.parser.MpesaSmsParser
import com.ledga.app.data.parser.ParseResult
import com.ledga.app.data.parser.TransactionDirection
import com.ledga.app.data.parser.TransactionType
import org.junit.Assert.*
import org.junit.Test

class MpesaSmsParserTest {

    private fun assertSuccess(sms: String): ParseResult.Success {
        val result = MpesaSmsParser.parse(sms)
        assertTrue("Expected Success but got: $result", result is ParseResult.Success)
        return result as ParseResult.Success
    }

    // --- Sender filter ---

    @Test
    fun `isMpesaMessage accepts MPESA sender`() {
        assertTrue(MpesaSmsParser.isMpesaMessage("MPESA"))
        assertTrue(MpesaSmsParser.isMpesaMessage("mpesa"))
        assertTrue(MpesaSmsParser.isMpesaMessage("Mpesa"))
    }

    @Test
    fun `isMpesaMessage rejects other senders`() {
        assertFalse(MpesaSmsParser.isMpesaMessage("KCB"))
        assertFalse(MpesaSmsParser.isMpesaMessage(""))
        assertFalse(MpesaSmsParser.isMpesaMessage("SAFARICOM"))
    }

    // --- Real-world regression fixtures ---
    // Sanitized from /Documents/ledga-export — phone numbers and account
    // balances replaced with placeholders to keep real-world fidelity without
    // committing live data.

    @Test
    fun `real Hustler Fund send — amount-first phrasing`() {
        val sms = "SEK8U08F14 Confirmed. You have sent Ksh1,002.84 to Hustler Fund on 20/05/2024  at 03:23 PM. New MPESA balance is Ksh9,429.84."
        val t = assertSuccess(sms).transaction
        assertEquals(TransactionType.SEND, t.type)
        assertEquals(1002.84, t.amount, 0.01)
        assertEquals("Hustler Fund", t.recipientName)
        assertEquals(TransactionDirection.OUTFLOW, t.direction)
    }

    @Test
    fun `real Give-cash agent deposit`() {
        val sms = "TGE6WZSMPY Confirmed. On 14/7/25 at 11:50 AM Give Ksh300.00 cash to Jamag Holdings Harrysal Mali Mali Street Busia New M-PESA balance is Ksh300.00. You can now access M-PESA via *334#"
        val t = assertSuccess(sms).transaction
        assertEquals(TransactionType.DEPOSIT, t.type)
        assertEquals(300.0, t.amount, 0.01)
        assertEquals(TransactionDirection.INFLOW, t.direction)
        assertTrue("recipientName should mention the agent, got: ${t.recipientName}",
            t.recipientName?.contains("Jamag", ignoreCase = true) == true)
    }

    @Test
    fun `real Fuliza auto-pay full`() {
        val sms = "UCHIE9OIM9  Confirmed. Ksh 1738.92 from your M-PESA has been used to fully pay your outstanding Fuliza M-PESA. Available Fuliza M-PESA limit is Ksh 11000.00. Your M-PESA balance is 8581.08."
        val t = assertSuccess(sms).transaction
        assertEquals(TransactionType.FULIZA_AUTO_PAY, t.type)
        assertEquals(1738.92, t.amount, 0.01)
        assertEquals(TransactionDirection.OUTFLOW, t.direction)
    }

    @Test
    fun `real Fuliza auto-pay partial`() {
        val sms = "UC3IE8BHFR  Confirmed. Ksh 200.00 from your M-PESA has been used to partially pay your outstanding Fuliza M-PESA. Your available Fuliza M-PESA limit is Ksh 8018.37. M-PESA balance is Ksh0.00."
        val t = assertSuccess(sms).transaction
        assertEquals(TransactionType.FULIZA_AUTO_PAY, t.type)
        assertEquals(200.0, t.amount, 0.01)
    }

    @Test
    fun `real withdraw alt format with merchant code`() {
        val sms = "UCEIE9EQ8H Confirmed.on 14/3/26 at 7:17 PMWithdraw Ksh6,500.00 from 031824 - Jamag Holdings Harrysal Mali Mali Street Busia New M-PESA balance is Ksh1,200.10. Transaction cost, Ksh87.00."
        val t = assertSuccess(sms).transaction
        assertEquals(TransactionType.WITHDRAW_AGENT, t.type)
        assertEquals(6500.0, t.amount, 0.01)
        assertEquals(87.0, t.transactionCost, 0.01)
        assertEquals("031824", t.recipientPhone)
        assertTrue("merchant name should contain 'Jamag', got: ${t.recipientName}",
            t.recipientName?.contains("Jamag", ignoreCase = true) == true)
    }

    @Test
    fun `real airtime self with no-space confirmed`() {
        val sms = "UC2IE88HZW confirmed.You bought Ksh200.00 of airtime on 2/3/26 at 2:52 PM.New M-PESA balance is Ksh5,240.88. Transaction cost, Ksh0.00."
        val t = assertSuccess(sms).transaction
        assertEquals(TransactionType.AIRTIME_SELF, t.type)
        assertEquals(200.0, t.amount, 0.01)
    }

    @Test
    fun `real reversal alt`() {
        val sms = "SH37SPJZKZ confirmed. Reversal of transaction SH38SP5YUQ has been successfully reversed  on 3/8/24  at 2:30 PM and Ksh200.00 is credited to your M-PESA account. New M-PESA account balance is Ksh1,770.00."
        val t = assertSuccess(sms).transaction
        assertEquals(TransactionType.REVERSAL, t.type)
        assertEquals("SH38SP5YUQ", t.reversedTransactionCode)
        assertEquals(TransactionDirection.INFLOW, t.direction)
    }

    @Test
    fun `real balance inquiry is filtered, not stored as UNKNOWN`() {
        val sms = "UCJQF9UGN5 Confirmed. Your account balance was: M-PESA Account : Ksh458.00 Business Account : Ksh0.00 on 19/3/26 at 1:33 PM. Transaction cost, Ksh0.00."
        val result = MpesaSmsParser.parse(sms)
        assertTrue("Expected balance inquiry to be Failure, got: $result",
            result is ParseResult.Failure)
    }

    // --- Send Money ---

    @Test
    fun `parse send money`() {
        val sms = "RK31B7X4ZQ Confirmed. Ksh500.00 sent to JOHN DOE 0712345678 on 21/3/26 at 1:30 PM. New M-PESA balance is Ksh1,200.00. Transaction cost, Ksh0.00."
        val result = assertSuccess(sms)
        val t = result.transaction
        assertEquals("RK31B7X4ZQ", t.transactionCode)
        assertEquals(TransactionType.SEND, t.type)
        assertEquals(500.0, t.amount, 0.01)
        assertEquals("JOHN DOE", t.recipientName)
        assertEquals("0712345678", t.recipientPhone)
        assertEquals(1200.0, t.balance, 0.01)
        assertEquals(0.0, t.transactionCost, 0.01)
        assertEquals(TransactionDirection.OUTFLOW, t.direction)
    }

    @Test
    fun `parse send money large amount`() {
        val sms = "AB12CD34EF Confirmed. Ksh100,000.00 sent to JANE SMITH 0798765432 on 1/1/26 at 12:00 AM. New M-PESA balance is Ksh50,000.00. Transaction cost, Ksh105.00."
        val result = assertSuccess(sms)
        assertEquals(100000.0, result.transaction.amount, 0.01)
        assertEquals(50000.0, result.transaction.balance, 0.01)
        assertEquals(105.0, result.transaction.transactionCost, 0.01)
    }

    // --- Buy Goods ---

    @Test
    fun `parse buy goods`() {
        val sms = "RK31B7X4ZQ Confirmed. Ksh1,200.00 paid to NAIVAS SUPERMARKET. on 21/3/26 at 2:15 PM.New M-PESA balance is Ksh3,500.00. Transaction cost, Ksh0.00."
        val result = assertSuccess(sms)
        val t = result.transaction
        assertEquals(TransactionType.BUY_GOODS, t.type)
        assertEquals(1200.0, t.amount, 0.01)
        assertEquals("NAIVAS SUPERMARKET", t.recipientName)
        assertEquals(3500.0, t.balance, 0.01)
        assertEquals(TransactionDirection.OUTFLOW, t.direction)
    }

    // --- Pay Bill ---

    @Test
    fun `parse pay bill`() {
        val sms = "RK31B7X4ZQ Confirmed. Ksh2,500.00 paid to KPLC PREPAID. Account Number 12345678. on 21/3/26 at 3:00 PM. New M-PESA balance is Ksh1,000.00. Transaction cost, Ksh0.00."
        val result = assertSuccess(sms)
        val t = result.transaction
        assertEquals(TransactionType.PAY_BILL, t.type)
        assertEquals(2500.0, t.amount, 0.01)
        assertEquals("KPLC PREPAID", t.recipientName)
        assertEquals("12345678", t.accountNumber)
        assertEquals(1000.0, t.balance, 0.01)
    }

    // --- Withdraw Agent ---

    @Test
    fun `parse withdraw agent`() {
        val sms = "RK31B7X4ZQ Confirmed.You have withdrawn Ksh1,000.00 from JAMES AGENT 543210 on 21/3/26 at 4:00 PM.New M-PESA balance is Ksh500.00. Transaction cost, Ksh28.00."
        val result = assertSuccess(sms)
        val t = result.transaction
        assertEquals(TransactionType.WITHDRAW_AGENT, t.type)
        assertEquals(1000.0, t.amount, 0.01)
        assertEquals("JAMES AGENT", t.recipientName)
        assertEquals("543210", t.recipientPhone)
        assertEquals(500.0, t.balance, 0.01)
        assertEquals(28.0, t.transactionCost, 0.01)
        assertEquals(TransactionDirection.OUTFLOW, t.direction)
    }

    // --- Withdraw ATM ---

    @Test
    fun `parse withdraw ATM`() {
        val sms = "RK31B7X4ZQ Confirmed. You have withdrawn Ksh5,000.00 from an ATM on 21/3/26 at 4:30 PM. New M-PESA balance is Ksh2,000.00. Transaction cost, Ksh34.00."
        val result = assertSuccess(sms)
        val t = result.transaction
        assertEquals(TransactionType.WITHDRAW_ATM, t.type)
        assertEquals(5000.0, t.amount, 0.01)
        assertEquals("ATM", t.recipientName)
        assertEquals(2000.0, t.balance, 0.01)
        assertEquals(34.0, t.transactionCost, 0.01)
    }

    // --- Deposit ---

    @Test
    fun `parse deposit`() {
        val sms = "RK31B7X4ZQ Confirmed.You have deposited Ksh5,000.00 to your M-PESA account on 21/3/26 at 10:00 AM.New M-PESA balance is Ksh5,500.00."
        val result = assertSuccess(sms)
        val t = result.transaction
        assertEquals(TransactionType.DEPOSIT, t.type)
        assertEquals(5000.0, t.amount, 0.01)
        assertEquals(5500.0, t.balance, 0.01)
        assertEquals(TransactionDirection.INFLOW, t.direction)
    }

    // --- Received Money ---

    @Test
    fun `parse received money`() {
        val sms = "RK31B7X4ZQ Confirmed.You have received Ksh2,000.00 from JANE DOE 0798765432 on 21/3/26 at 11:00 AM.New M-PESA balance is Ksh7,500.00."
        val result = assertSuccess(sms)
        val t = result.transaction
        assertEquals(TransactionType.RECEIVED, t.type)
        assertEquals(2000.0, t.amount, 0.01)
        assertEquals("JANE DOE", t.recipientName)
        assertEquals("0798765432", t.recipientPhone)
        assertEquals(7500.0, t.balance, 0.01)
        assertEquals(TransactionDirection.INFLOW, t.direction)
    }

    // --- Airtime Self ---

    @Test
    fun `parse airtime self`() {
        val sms = "RK31B7X4ZQ Confirmed. Ksh100.00 of airtime purchased on 21/3/26 at 12:00 PM.New M-PESA balance is Ksh7,400.00. Transaction cost, Ksh0.00."
        val result = assertSuccess(sms)
        val t = result.transaction
        assertEquals(TransactionType.AIRTIME_SELF, t.type)
        assertEquals(100.0, t.amount, 0.01)
        assertEquals(7400.0, t.balance, 0.01)
        assertEquals(TransactionDirection.OUTFLOW, t.direction)
    }

    // --- Airtime Other ---

    @Test
    fun `parse airtime for others`() {
        val sms = "RK31B7X4ZQ Confirmed. You bought Ksh100.00 of airtime for 0712345678 on 21/3/26 at 12:30 PM.New M-PESA balance is Ksh7,300.00. Transaction cost, Ksh0.00."
        val result = assertSuccess(sms)
        val t = result.transaction
        assertEquals(TransactionType.AIRTIME_OTHER, t.type)
        assertEquals(100.0, t.amount, 0.01)
        assertEquals("0712345678", t.recipientPhone)
        assertEquals(7300.0, t.balance, 0.01)
    }

    // --- M-Pesa Global ---

    @Test
    fun `parse mpesa global`() {
        val sms = "RK31B7X4ZQ Confirmed. Ksh5,000.00 sent to JOHN DOE +44712345678 (United Kingdom) via M-PESA Global on 21/3/26 at 1:30 PM. New M-PESA balance is Ksh10,000.00. Transaction cost, Ksh150.00."
        val result = assertSuccess(sms)
        val t = result.transaction
        assertEquals(TransactionType.MPESA_GLOBAL, t.type)
        assertEquals(5000.0, t.amount, 0.01)
        assertEquals("JOHN DOE", t.recipientName)
        assertEquals("+44712345678", t.recipientPhone)
        assertEquals("United Kingdom", t.destinationCountry)
        assertEquals(10000.0, t.balance, 0.01)
        assertEquals(150.0, t.transactionCost, 0.01)
    }

    // --- Fuliza ---

    @Test
    fun `parse fuliza borrow`() {
        val sms = "RK31B7X4ZQ Confirmed. Ksh500.00 sent to JOHN DOE 0712345678 on 21/3/26 at 1:30 PM. New M-PESA balance is Ksh0.00. Fuliza M-PESA amount is Ksh500.00. Fuliza M-PESA outstanding amount is Ksh500.00."
        val result = assertSuccess(sms)
        val t = result.transaction
        assertEquals(TransactionType.FULIZA, t.type)
        assertEquals(500.0, t.amount, 0.01)
        assertEquals(500.0, t.fulizaAmount!!, 0.01)
        assertEquals(500.0, t.fulizaOutstanding!!, 0.01)
        assertEquals("JOHN DOE", t.recipientName)
        assertEquals(TransactionDirection.OUTFLOW, t.direction)
    }

    // --- Fuliza Repayment ---

    @Test
    fun `parse fuliza repayment`() {
        val sms = "RK31B7X4ZQ Confirmed. You have paid Ksh200.00 to Fuliza M-PESA on 21/3/26 at 2:00 PM. Fuliza M-PESA outstanding amount is Ksh300.00."
        val result = assertSuccess(sms)
        val t = result.transaction
        assertEquals(TransactionType.FULIZA_REPAYMENT, t.type)
        assertEquals(200.0, t.amount, 0.01)
        assertEquals(300.0, t.fulizaOutstanding!!, 0.01)
        assertEquals(TransactionDirection.OUTFLOW, t.direction)
    }

    // --- Fuliza limit + interest capture ---

    @Test
    fun `fuliza limit captured from auto-pay full`() {
        val sms = "UCHIE9OIM9  Confirmed. Ksh 1738.92 from your M-PESA has been used to fully pay your outstanding Fuliza M-PESA. Available Fuliza M-PESA limit is Ksh 11000.00. Your M-PESA balance is 8581.08."
        val t = assertSuccess(sms).transaction
        assertEquals(11000.0, t.fulizaLimit!!, 0.01)
    }

    @Test
    fun `fuliza limit captured from auto-pay partial`() {
        val sms = "UC3IE8BHFR  Confirmed. Ksh 200.00 from your M-PESA has been used to partially pay your outstanding Fuliza M-PESA. Your available Fuliza M-PESA limit is Ksh 8018.37. M-PESA balance is Ksh0.00."
        val t = assertSuccess(sms).transaction
        assertEquals(8018.37, t.fulizaLimit!!, 0.01)
    }

    @Test
    fun `fuliza borrow captures interest as cost`() {
        val sms = "TGM53BTX2H Confirmed. Fuliza M-PESA amount is Ksh 1073.00. Interest charged Ksh 10.73. Total Fuliza M-PESA outstanding amount is Ksh 1699.70 due on 21/08/25. To check daily charges, Dial *334#OK Select Fuliza M-PESA."
        val t = assertSuccess(sms).transaction
        assertEquals(TransactionType.FULIZA, t.type)
        assertEquals(1073.0, t.amount, 0.01)
        assertEquals(10.73, t.transactionCost, 0.01)
        assertEquals(1699.70, t.fulizaOutstanding!!, 0.01)
    }

    @Test
    fun `split fuliza companion parses outstanding and access fee`() {
        // M-PESA's post-2026-06 format: a standalone companion SMS sharing the
        // payment's code, carrying the outstanding + access fee.
        val sms = "UF7IE77R23 Confirmed. Fuliza M-PESA amount is Ksh 30.00. Access Fee charged Ksh 0.30. Total Fuliza M-PESA outstanding amount is Ksh3249.74 due on 07/07/26. To check daily charges, Dial *334#OK Select Query Charges"
        val t = assertSuccess(sms).transaction
        assertEquals(TransactionType.FULIZA, t.type)
        assertEquals(30.0, t.amount, 0.01)
        assertEquals(0.30, t.transactionCost, 0.01)
        assertEquals(3249.74, t.fulizaOutstanding!!, 0.01)
        // No recipient in the companion — this is what marks it as a companion
        // (vs the payment SMS) for the merge step.
        assertEquals(null, t.recipientName)
    }

    @Test
    fun `clean payment SMS carries no fuliza fields`() {
        val sms = "UF7IE77R23 Confirmed. Ksh30.00 sent to Kaps Parking  Rupa Mall for account 120850178 on 7/6/26 at 8:59 PM New M-PESA balance is Ksh0.00. Transaction cost, Ksh0.00.Amount you can transact within the day is 488,636.33."
        val t = assertSuccess(sms).transaction
        assertEquals(30.0, t.amount, 0.01)
        assertEquals(null, t.fulizaOutstanding)
        assertEquals(TransactionDirection.OUTFLOW, t.direction)
    }

    @Test
    fun `fuliza sender id is accepted`() {
        assertTrue(MpesaSmsParser.isMpesaMessage("FULIZA"))
        assertTrue(MpesaSmsParser.isMpesaMessage("MPESA"))
        assertTrue(MpesaSmsParser.isMpesaMessage("mpesa"))
        assertTrue(!MpesaSmsParser.isMpesaMessage("BANKSMS"))
    }

    // --- Fuliza Reversal ---

    @Test
    fun `parse fuliza reversal`() {
        val sms = "RK31B7X4ZQ Confirmed. Fuliza M-PESA of Ksh500.00 has been reversed on 21/3/26 at 3:00 PM. Fuliza M-PESA outstanding amount is Ksh0.00."
        val result = assertSuccess(sms)
        val t = result.transaction
        assertEquals(TransactionType.FULIZA_REVERSAL, t.type)
        assertEquals(500.0, t.amount, 0.01)
        assertEquals(0.0, t.fulizaOutstanding!!, 0.01)
        assertEquals(TransactionDirection.INFLOW, t.direction)
    }

    // --- Reversal ---

    @Test
    fun `parse reversal`() {
        val sms = "RK31B7X4ZQ Confirmed. Transaction RJ12345678 has been reversed. Your account balance is Ksh2,000.00."
        val result = assertSuccess(sms)
        val t = result.transaction
        assertEquals(TransactionType.REVERSAL, t.type)
        assertEquals("RJ12345678", t.reversedTransactionCode)
        assertEquals(2000.0, t.balance, 0.01)
        assertEquals(TransactionDirection.INFLOW, t.direction)
        // No "is credited" amount in this format — must stay 0.0, never the balance.
        assertEquals(0.0, t.amount, 0.01)
    }

    @Test
    fun `parse reversal with credited amount`() {
        val sms = "RK31B7X4ZQ Confirmed. Transaction RJ12345678 has been reversed on 21/3/26 at 3:00 PM and Ksh750.00 is credited to your M-PESA account. New M-PESA account balance is Ksh2,750.00."
        val t = assertSuccess(sms).transaction
        assertEquals(TransactionType.REVERSAL, t.type)
        assertEquals("RJ12345678", t.reversedTransactionCode)
        assertEquals(750.0, t.amount, 0.01)
        assertEquals(TransactionDirection.INFLOW, t.direction)
    }

    @Test
    fun `reversal alt captures credited amount`() {
        val sms = "SH37SPJZKZ confirmed. Reversal of transaction SH38SP5YUQ has been successfully reversed  on 3/8/24  at 2:30 PM and Ksh200.00 is credited to your M-PESA account. New M-PESA account balance is Ksh1,770.00."
        val t = assertSuccess(sms).transaction
        assertEquals(200.0, t.amount, 0.01)
    }

    // --- M-Shwari ---

    @Test
    fun `parse mshwari transfer to`() {
        val sms = "RK31B7X4ZQ Confirmed. Ksh1,000.00 transferred to M-Shwari account on 21/3/26 at 5:00 PM. New M-PESA balance is Ksh500.00."
        val result = assertSuccess(sms)
        val t = result.transaction
        assertEquals(TransactionType.MSHWARI, t.type)
        assertEquals(1000.0, t.amount, 0.01)
        assertEquals(TransactionDirection.OUTFLOW, t.direction)
    }

    @Test
    fun `parse mshwari transfer from`() {
        val sms = "RK31B7X4ZQ Confirmed. Ksh1,000.00 transferred from M-Shwari account on 21/3/26 at 5:00 PM. New M-PESA balance is Ksh1,500.00."
        val result = assertSuccess(sms)
        assertEquals(TransactionDirection.INFLOW, result.transaction.direction)
    }

    // --- KCB M-Pesa ---

    @Test
    fun `parse kcb mpesa`() {
        val sms = "RK31B7X4ZQ Confirmed. Ksh2,000.00 transferred to KCB M-Pesa account on 21/3/26 at 6:00 PM. New M-PESA balance is Ksh3,000.00."
        val result = assertSuccess(sms)
        val t = result.transaction
        assertEquals(TransactionType.KCB_MPESA, t.type)
        assertEquals(2000.0, t.amount, 0.01)
        assertEquals(TransactionDirection.OUTFLOW, t.direction)
    }

    @Test
    fun `real kcb withdrawal — wallet balance wins over KCB account balance, direction is INFLOW`() {
        // Real template: the KCB pocket balance comes FIRST, wallet second.
        // Regression: parser stored 0.17 as the wallet balance and OUTFLOW
        // as the direction (the SMS says "from YOUR KCB", not "from KCB").
        val sms = "UF3IE6OMZP Confirmed. You have transfered Ksh15,000.00 from your KCB M-PESA account " +
            "on 3/6/26 at 4:27 PM. KCB M-PESA Account balance is Ksh0.17. New M-PESA balance is Ksh15,000.00."
        val t = assertSuccess(sms).transaction
        assertEquals(TransactionType.KCB_MPESA, t.type)
        assertEquals(15000.0, t.amount, 0.01)
        assertEquals(15000.0, t.balance, 0.01)
        assertEquals(TransactionDirection.INFLOW, t.direction)
    }

    @Test
    fun `real kcb deposit — wallet balance first, savings balance ignored`() {
        val sms = "UF1AB2CD34 Confirmed. Ksh5,000.00 transfered to KCB M-PESA account on 1/6/26 at 9:00 AM. " +
            "New M-PESA balance is Ksh3,782.39, new KCB M-PESA Saving account balance is Ksh5,000.17."
        val t = assertSuccess(sms).transaction
        assertEquals(TransactionType.KCB_MPESA, t.type)
        assertEquals(5000.0, t.amount, 0.01)
        assertEquals(3782.39, t.balance, 0.01)
        assertEquals(TransactionDirection.OUTFLOW, t.direction)
    }

    @Test
    fun `mshwari withdrawal with possessive phrasing is INFLOW`() {
        val sms = "RK31B7X4ZQ Confirmed. Ksh1,000.00 transferred from your M-Shwari account on 21/3/26 at 5:00 PM. " +
            "M-Shwari account balance is Ksh200.00. New M-PESA balance is Ksh1,500.00."
        val t = assertSuccess(sms).transaction
        assertEquals(TransactionDirection.INFLOW, t.direction)
        assertEquals(1500.0, t.balance, 0.01)
    }

    // --- Edge cases ---

    @Test
    fun `parse failure for non-confirmed message`() {
        val sms = "RK31B7X4ZQ Failed. Ksh500.00 sent to JOHN DOE."
        val result = MpesaSmsParser.parse(sms)
        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun `parse failure for no transaction code`() {
        val sms = "Confirmed. Ksh500.00 sent to JOHN DOE on 21/3/26 at 1:30 PM."
        val result = MpesaSmsParser.parse(sms)
        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun `parse small amount`() {
        val sms = "AB12CD34EF Confirmed. Ksh1.00 of airtime purchased on 21/3/26 at 12:00 PM.New M-PESA balance is Ksh0.50. Transaction cost, Ksh0.00."
        val result = assertSuccess(sms)
        assertEquals(1.0, result.transaction.amount, 0.01)
        assertEquals(0.5, result.transaction.balance, 0.01)
    }

    @Test
    fun `unknown type for unrecognized mpesa message`() {
        val sms = "AB12CD34EF Confirmed. Some new M-PESA feature we don't know about."
        val result = assertSuccess(sms)
        assertEquals(TransactionType.UNKNOWN, result.transaction.type)
    }

    @Test
    fun `transaction code extraction`() {
        val sms = "RK31B7X4ZQ Confirmed. Ksh500.00 sent to JOHN DOE 0712345678 on 21/3/26 at 1:30 PM. New M-PESA balance is Ksh1,200.00. Transaction cost, Ksh0.00."
        val result = assertSuccess(sms)
        assertEquals("RK31B7X4ZQ", result.transaction.transactionCode)
    }
}
