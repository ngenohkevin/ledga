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
