package com.ledga.core.derive

import com.ledga.core.model.Categories
import com.ledga.core.model.TxKind
import com.ledga.core.money.Money
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BalanceChainTest {
    private fun ksh(s: String) = Money.parse(s)!!

    private fun tx(
        code: String, kind: TxKind, at: String, amount: String, balance: String?,
        fee: String = "0", fulizaFee: String? = null, drawn: String? = null, line: Long? = 1,
    ) = DerivedTx(
        code = code, lineId = line, occurredAt = Instant.parse(at), occurredAtApprox = false,
        kind = kind, flow = kind.defaultFlow, amount = ksh(amount), fee = ksh(fee), balance = balance?.let(::ksh),
        counterpartyName = null, counterpartyPhone = null, counterpartyAccount = null, counterpartyKey = null,
        destinationCountry = null, reversesCode = null, isReversed = false,
        fulizaDrawn = drawn?.let(::ksh), fulizaFee = fulizaFee?.let(::ksh), fulizaOutstanding = null,
        fulizaLimit = null, fulizaDueDate = null, categoryKey = Categories.OTHER, note = null,
        isHidden = false, searchText = "", smsCount = 1,
    )

    @Test
    fun `an unbroken chain verifies every step after the baseline`() {
        val r = BalanceChain.check(
            listOf(
                tx("TJK4AB12GA", TxKind.RECEIVE, "2026-03-21T05:00:00Z", "1000", "1000"),
                tx("TJK4AB12GB", TxKind.SEND, "2026-03-21T06:00:00Z", "300", "693", fee = "7"),
                tx("TJK4AB12GC", TxKind.BUY_GOODS, "2026-03-21T07:00:00Z", "93", "600"),
            ),
        )
        assertEquals(2, r.checked)
        assertEquals(0, r.gaps)
        assertEquals(emptyList(), r.breaks)
    }

    @Test
    fun `same-minute rows are reordered to fit the chain`() {
        val r = BalanceChain.check(
            listOf(
                tx("TJK4AB12GD", TxKind.BUY_GOODS, "2026-03-21T10:00:00Z", "100", "0"),
                // Listed (and code-ordered) repay-first, but the receipt really came first.
                tx("TJK4AB12GE", TxKind.FULIZA_REPAY_AUTO, "2026-03-21T11:05:00Z", "300", "700"),
                tx("TJK4AB12GF", TxKind.RECEIVE, "2026-03-21T11:05:00Z", "1000", "1000"),
            ),
        )
        assertEquals(2, r.checked)
        assertEquals(emptyList(), r.breaks)
    }

    @Test
    fun `a mismatch is reported once and the chain continues from the stated balance`() {
        val r = BalanceChain.check(
            listOf(
                tx("TJK4AB12GG", TxKind.RECEIVE, "2026-03-21T05:00:00Z", "1000", "1000"),
                tx("TJK4AB12GH", TxKind.SEND, "2026-03-21T06:00:00Z", "300", "650", fee = "7"),
                tx("TJK4AB12GJ", TxKind.BUY_GOODS, "2026-03-21T07:00:00Z", "50", "600"),
            ),
        )
        assertEquals(2, r.checked)
        assertEquals(listOf(ChainBreak(1, "TJK4AB12GH", Instant.parse("2026-03-21T06:00:00Z"), ksh("693"), ksh("650"))), r.breaks)
    }

    @Test
    fun `an orphan Fuliza companion is a gap, not a break`() {
        val r = BalanceChain.check(
            listOf(
                tx("TJK4AB12GK", TxKind.RECEIVE, "2026-03-21T05:00:00Z", "1000", "1000"),
                tx("TJK4AB12GL", TxKind.FULIZA_ONLY, "2026-03-21T06:00:00Z", "463", null, fee = "4.63", fulizaFee = "4.63", drawn = "463"),
                tx("TJK4AB12GM", TxKind.SEND, "2026-03-21T07:00:00Z", "100", "400"),
                tx("TJK4AB12GN", TxKind.BUY_GOODS, "2026-03-21T08:00:00Z", "100", "300"),
            ),
        )
        assertEquals(1, r.gaps)
        assertEquals(1, r.checked)
        assertEquals(emptyList(), r.breaks)
    }

    @Test
    fun `a Fuliza-funded payment balances with the drawn amount and without the access fee`() {
        val r = BalanceChain.check(
            listOf(
                tx("TJK4AB12GP", TxKind.RECEIVE, "2026-06-09T05:00:00Z", "2037", "2037"),
                tx("TJK4AB12GQ", TxKind.BUY_GOODS, "2026-06-09T16:48:00Z", "2500", "0", fee = "4.63", fulizaFee = "4.63", drawn = "463"),
            ),
        )
        assertEquals(1, r.checked)
        assertEquals(emptyList(), r.breaks)
    }

    @Test
    fun `rows without a stated balance still move the running balance`() {
        val r = BalanceChain.check(
            listOf(
                tx("TJK4AB12GR", TxKind.RECEIVE, "2026-03-21T05:00:00Z", "1000", "1000"),
                tx("TJK4AB12GS", TxKind.FULIZA_REPAY_MANUAL, "2026-03-21T06:00:00Z", "200", null),
                tx("TJK4AB12GT", TxKind.RECEIVE, "2026-03-21T07:00:00Z", "100", "900"),
            ),
        )
        assertEquals(1, r.checked)
        assertEquals(emptyList(), r.breaks)
    }

    @Test
    fun `lines are checked independently`() {
        val r = BalanceChain.check(
            listOf(
                tx("TJK4AB12GU", TxKind.RECEIVE, "2026-03-21T05:00:00Z", "1000", "1000", line = 1),
                tx("TJK4AB12GV", TxKind.RECEIVE, "2026-03-21T05:30:00Z", "500", "500", line = 2),
                tx("TJK4AB12GW", TxKind.SEND, "2026-03-21T06:00:00Z", "100", "900", line = 1),
                tx("TJK4AB12GX", TxKind.SEND, "2026-03-21T06:30:00Z", "100", "300", line = 2),
            ),
        )
        assertEquals(listOf(1L, 2L), r.lines.map { it.lineId })
        assertEquals(0, r.lines[0].breaks.size)
        assertEquals(1, r.lines[1].breaks.size)
        assertEquals(ksh("400"), r.lines[1].breaks.single().expected)
    }

    @Test
    fun `wallet deltas by kind`() {
        assertEquals(ksh("1000"), BalanceChain.walletDelta(tx("A", TxKind.SAVINGS_IN, "2026-01-01T00:00:00Z", "1000", "1000")))
        assertEquals(-ksh("1007"), BalanceChain.walletDelta(tx("A", TxKind.SEND, "2026-01-01T00:00:00Z", "1000", null, fee = "7")))
        assertNull(BalanceChain.walletDelta(tx("A", TxKind.REVERSAL, "2026-01-01T00:00:00Z", "0", "2000")))
        assertNull(BalanceChain.walletDelta(tx("A", TxKind.FULIZA_REVERSAL, "2026-01-01T00:00:00Z", "500", null)))
    }
}
