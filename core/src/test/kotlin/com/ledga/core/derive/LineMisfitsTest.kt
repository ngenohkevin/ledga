package com.ledga.core.derive

import com.ledga.core.model.Categories
import com.ledga.core.model.TxKind
import com.ledga.core.money.Money
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** R194 (owner, 2026-10-09): payments on the wrong line, proven by both balances. Synthetic payments on two lines. */
class LineMisfitsTest {
    private fun ksh(s: String) = Money.parse(s)!!

    private fun tx(code: String, kind: TxKind, at: String, amount: String, balance: String, line: Long) = DerivedTx(
        code = code, lineId = line, occurredAt = Instant.parse(at), occurredAtApprox = false,
        kind = kind, flow = kind.defaultFlow, amount = ksh(amount), fee = Money.ZERO, balance = ksh(balance),
        counterpartyName = null, counterpartyPhone = null, counterpartyAccount = null, counterpartyKey = null,
        destinationCountry = null, reversesCode = null, isReversed = false,
        fulizaDrawn = null, fulizaFee = null, fulizaOutstanding = null,
        fulizaLimit = null, fulizaDueDate = null, categoryKey = Categories.OTHER, note = null,
        isHidden = false, searchText = "", smsCount = 1,
    )

    /** Line 1 runs 1,000 → (100 out) → 900 → (200 out) → 700; the middle payment sits on line 2, whose own balance is 5,000. */
    private fun misfiled(lineOfMiddle: Long = 2, line2Start: String = "5000") = listOf(
        tx("TJK4AB15AA", TxKind.RECEIVE, "2026-09-01T06:00:00Z", "1000", "1000", line = 1),
        tx("TJK4AB15BA", TxKind.RECEIVE, "2026-09-01T07:00:00Z", line2Start, line2Start, line = 2),
        tx("TJK4AB15AB", TxKind.BUY_GOODS, "2026-09-02T06:00:00Z", "100", "900", line = lineOfMiddle),
        tx("TJK4AB15AC", TxKind.BUY_GOODS, "2026-09-03T06:00:00Z", "200", "700", line = 1),
        tx("TJK4AB15BB", TxKind.BUY_GOODS, "2026-09-04T06:00:00Z", "50", "4950", line = 2),
    )

    @Test
    fun `a payment whose balances carry on another line's, where that line breaks, belongs there`() {
        assertEquals(mapOf("TJK4AB15AB" to 1L), LineMisfits.find(misfiled(), chosen = emptySet()))
    }

    @Test
    fun `on its right line nothing is suggested`() {
        assertEquals(emptyMap(), LineMisfits.find(misfiled(lineOfMiddle = 1), chosen = emptySet()))
    }

    @Test
    fun `a payment the person put on its line stays there`() {
        assertEquals(emptyMap(), LineMisfits.find(misfiled(), chosen = setOf("TJK4AB15AB")))
    }

    @Test
    fun `a payment that also carries on its own line's balance is left alone`() {
        // Line 2 happens to stand at 1,000 too, so the payment fits where it is: the balances can't tell.
        assertEquals(emptyMap(), LineMisfits.find(misfiled(line2Start = "1000").filterNot { it.code == "TJK4AB15BB" }, chosen = emptySet()))
    }
}
