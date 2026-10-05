package com.ledga.core.derive

import com.ledga.core.model.TxKind
import com.ledga.core.money.Money
import com.ledga.core.parse.MpesaParser
import com.ledga.core.parse.ParseOutcome
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AssemblerTest {
    private fun ksh(s: String) = Money.parse(s)!!
    private val t0 = Instant.parse("2026-06-09T16:48:10Z")

    private fun src(id: Long, body: String, receivedAt: Instant = t0, lineId: Long? = 1): SourceSms {
        val p = (MpesaParser.parse(body, receivedAt) as ParseOutcome.Parsed).sms
        return SourceSms(id, p, receivedAt, lineId)
    }

    private val payment =
        "TJK4AB12EA Confirmed. Ksh2,500.00 paid to SAMPLE SUPERMARKET. on 9/6/26 at 7:48 PM.New M-PESA balance is Ksh0.00. Transaction cost, Ksh0.00."
    private val companion =
        "TJK4AB12EA Confirmed. Fuliza M-PESA amount is Ksh 463.00. Access Fee charged Ksh 4.63. Total Fuliza M-PESA outstanding amount is Ksh6,418.36 due on 02/11/26. To check daily charges, Dial *334#OK Select Query Charges"

    @Test
    fun `payment plus companion merge into one spend in either arrival order`() {
        listOf(
            listOf(src(1, payment, t0), src(2, companion, t0.plusSeconds(2))),
            listOf(src(2, companion, t0), src(1, payment, t0.plusSeconds(2))),
        ).forEach { sources ->
            val tx = Assembler.assemble(sources)
            assertEquals("TJK4AB12EA", tx.code)
            assertEquals(TxKind.BUY_GOODS, tx.kind)
            assertEquals(ksh("2500"), tx.amount)
            assertEquals(ksh("4.63"), tx.fee)
            assertEquals(ksh("4.63"), tx.fulizaFee)
            assertEquals(Money.ZERO, tx.balance)
            assertEquals("SAMPLE SUPERMARKET", tx.counterparty?.name)
            assertEquals(ksh("463"), tx.fuliza?.drawn)
            assertEquals(ksh("6418.36"), tx.fuliza?.outstanding)
            assertEquals(LocalDate.of(2026, 11, 2), tx.fuliza?.dueDate)
            assertEquals(Instant.parse("2026-06-09T16:48:00Z"), tx.occurredAt)
            assertEquals(2, tx.smsCount)
        }
    }

    @Test
    fun `orphan companion becomes FULIZA_ONLY spend of the drawn amount`() {
        val tx = Assembler.assemble(listOf(src(2, companion)))
        assertEquals(TxKind.FULIZA_ONLY, tx.kind)
        assertEquals(ksh("463"), tx.amount)
        assertEquals(ksh("4.63"), tx.fee)
        assertNull(tx.balance)
        assertNull(tx.counterparty)
        assertEquals(t0, tx.occurredAt)
    }

    @Test
    fun `same code resent with a different promo tail is one transaction`() {
        val a = "TJK4AB12EB Confirmed. Ksh500.00 sent to JANE TESTER 0712345111 on 21/3/26 at 1:30 PM. New M-PESA balance is Ksh1,200.00. Transaction cost, Ksh7.00. Promo one."
        val b = "TJK4AB12EB Confirmed. Ksh500.00 sent to JANE TESTER 0712345111 on 21/3/26 at 1:30 PM. New M-PESA balance is Ksh1,200.00. Transaction cost, Ksh7.00. Promo two."
        val tx = Assembler.assemble(listOf(src(9, b, t0.plusSeconds(60)), src(4, a, t0)))
        assertEquals(ksh("500"), tx.amount)
        assertEquals(ksh("7"), tx.fee)
        assertNull(tx.fulizaFee)
        assertEquals(2, tx.smsCount)
    }

    @Test
    fun `line comes from the primary payment, else any source`() {
        assertEquals(7L, Assembler.assemble(listOf(src(1, payment, lineId = 7), src(2, companion, lineId = 8))).lineId)
        assertEquals(8L, Assembler.assemble(listOf(src(1, payment, lineId = null), src(2, companion, lineId = 8))).lineId)
    }

    @Test
    fun `old combined format keeps its own Fuliza facts`() {
        val tx = Assembler.assemble(
            listOf(
                src(
                    1,
                    "TJK4AB12EC Confirmed. Ksh500.00 sent to JANE TESTER 0712345111 on 21/3/26 at 1:30 PM. New M-PESA balance is Ksh0.00. " +
                        "Fuliza M-PESA amount is Ksh500.00. Fuliza M-PESA outstanding amount is Ksh500.00.",
                ),
            ),
        )
        assertEquals(TxKind.SEND, tx.kind)
        assertEquals(ksh("500"), tx.fuliza?.drawn)
        assertEquals(Money.ZERO, tx.fee)
    }

    @Test
    fun `rejects empty input and mixed codes`() {
        assertFailsWith<IllegalArgumentException> { Assembler.assemble(emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            Assembler.assemble(listOf(src(1, payment), src(2, companion.replace("TJK4AB12EA", "TJK4AB12ZZ"))))
        }
    }

    @Test
    fun `a transaction is reversed when another names it`() {
        assertEquals(setOf("TJK4AB12CD"), Reversals.reversedCodes(listOf(null, "TJK4AB12CD", null)))
        assertEquals(emptySet<String>(), Reversals.reversedCodes(listOf<String?>(null, null)))
    }

    @Test
    fun `lineId fallback is deterministic across source list orders`() {
        val a = src(1, payment, t0, lineId = null)
        val b = src(2, payment, t0.plusSeconds(1), lineId = 5)
        val c = src(3, companion, t0.plusSeconds(2), lineId = 7)
        val orders = listOf(listOf(a, b, c), listOf(c, b, a), listOf(b, c, a), listOf(c, a, b))
        assertEquals(setOf<Long?>(5L), orders.map { Assembler.assemble(it).lineId }.toSet())
        val companionOnly = listOf(src(4, companion, t0, lineId = null), src(5, companion, t0.plusSeconds(1), lineId = 9), src(6, companion, t0.plusSeconds(2), lineId = 8))
        assertEquals(setOf<Long?>(9L), listOf(companionOnly, companionOnly.reversed()).map { Assembler.assemble(it).lineId }.toSet())
    }

    @Test
    fun `two different payment SMS on one code keep the earliest payment`() {
        val first = src(1, "TJK4AB12ED Confirmed. Ksh500.00 sent to JANE TESTER 0712345111 on 9/6/26 at 7:48 PM. New M-PESA balance is Ksh1,200.00. Transaction cost, Ksh7.00.", t0)
        val second = src(2, "TJK4AB12ED Confirmed. Ksh900.00 sent to JANE TESTER 0712345111 on 9/6/26 at 7:49 PM. New M-PESA balance is Ksh300.00. Transaction cost, Ksh9.00.", t0.plusSeconds(60))
        listOf(listOf(first, second), listOf(second, first)).forEach {
            val tx = Assembler.assemble(it)
            assertEquals(ksh("500"), tx.amount)
            assertEquals(ksh("7"), tx.fee)
            assertEquals(TxKind.SEND, tx.kind)
            assertEquals(2, tx.smsCount)
        }
    }
}
