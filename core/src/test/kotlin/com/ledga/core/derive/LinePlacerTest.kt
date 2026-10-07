package com.ledga.core.derive

import com.ledga.core.model.Categories
import com.ledga.core.model.TxKind
import com.ledga.core.money.Money
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** R116: where a payment that isn't on a line belongs, by the balances alone. Synthetic codes and amounts. */
class LinePlacerTest {
    private fun ksh(s: String) = Money.parse(s)!!

    private fun tx(code: String, kind: TxKind, at: String, amount: String, balance: String?, line: Long? = null, fee: String = "0") = DerivedTx(
        code = code, lineId = line, occurredAt = Instant.parse(at), occurredAtApprox = false,
        kind = kind, flow = kind.defaultFlow, amount = ksh(amount), fee = ksh(fee), balance = balance?.let(::ksh),
        counterpartyName = null, counterpartyPhone = null, counterpartyAccount = null, counterpartyKey = null,
        destinationCountry = null, reversesCode = null, isReversed = false,
        fulizaDrawn = null, fulizaFee = null, fulizaOutstanding = null,
        fulizaLimit = null, fulizaDueDate = null, categoryKey = Categories.OTHER, note = null,
        isHidden = false, searchText = "", smsCount = 1,
    )

    private val lines = setOf(1L, 2L)

    @Test
    fun `payments before the newest ones on each line are placed walking back`() {
        val r = LinePlacer.place(
            listOf(
                tx("TJK4AB13AA", TxKind.RECEIVE, "2026-03-21T06:00:00Z", "1000", "1000"),
                tx("TJK4AB13AB", TxKind.RECEIVE, "2026-03-21T06:30:00Z", "5000", "5000"),
                tx("TJK4AB13AC", TxKind.SEND, "2026-03-21T07:00:00Z", "300", "700"),
                tx("TJK4AB13AD", TxKind.PAYBILL, "2026-03-21T08:00:00Z", "200", "4800", line = 2),
                tx("TJK4AB13AE", TxKind.BUY_GOODS, "2026-03-21T09:00:00Z", "100", "600", line = 1),
            ),
            lines,
        )
        assertEquals(mapOf("TJK4AB13AA" to 1L, "TJK4AB13AB" to 2L, "TJK4AB13AC" to 1L), r.placed)
        assertEquals(0, r.left)
    }

    @Test
    fun `payments after the first ones on each line are placed walking forward`() {
        val r = LinePlacer.place(
            listOf(
                tx("TJK4AB13BA", TxKind.RECEIVE, "2026-03-21T06:00:00Z", "1000", "1000", line = 1),
                tx("TJK4AB13BB", TxKind.RECEIVE, "2026-03-21T06:30:00Z", "5000", "5000", line = 2),
                tx("TJK4AB13BC", TxKind.SEND, "2026-03-21T07:00:00Z", "300", "693", fee = "7"),
                tx("TJK4AB13BD", TxKind.PAYBILL, "2026-03-21T08:00:00Z", "200", "4800"),
                tx("TJK4AB13BE", TxKind.RECEIVE, "2026-03-21T09:00:00Z", "7", "700"),
            ),
            lines,
        )
        assertEquals(mapOf("TJK4AB13BC" to 1L, "TJK4AB13BD" to 2L, "TJK4AB13BE" to 1L), r.placed)
        assertEquals(0, r.left)
    }

    @Test
    fun `a payment that fits both lines stays where it is`() {
        val r = LinePlacer.place(
            listOf(
                tx("TJK4AB13CA", TxKind.RECEIVE, "2026-03-21T06:00:00Z", "500", "500", line = 1),
                tx("TJK4AB13CB", TxKind.RECEIVE, "2026-03-21T06:30:00Z", "500", "500", line = 2),
                tx("TJK4AB13CC", TxKind.RECEIVE, "2026-03-21T07:00:00Z", "100", "600"),
            ),
            lines,
        )
        assertEquals(emptyMap(), r.placed)
        assertEquals(1, r.left)
    }

    @Test
    fun `a line with no payment yet blocks every guess`() {
        val r = LinePlacer.place(
            listOf(
                tx("TJK4AB13DA", TxKind.RECEIVE, "2026-03-21T06:00:00Z", "1000", "1000", line = 1),
                tx("TJK4AB13DB", TxKind.SEND, "2026-03-21T07:00:00Z", "300", "700"),
            ),
            lines,
        )
        assertEquals(emptyMap(), r.placed, "line 2 could have sent it: nothing says otherwise")
        assertEquals(1, r.left)
    }

    @Test
    fun `a missing message is bridged from both sides`() {
        // Line 1 lost a Ksh 200 send between 08:00 and 10:00: the payment before it is placed walking forward, the one
        // after it walking back, and neither is ever put on line 2.
        val r = LinePlacer.place(
            listOf(
                tx("TJK4AB13EA", TxKind.RECEIVE, "2026-03-21T05:00:00Z", "1000", "1000", line = 1),
                tx("TJK4AB13EB", TxKind.RECEIVE, "2026-03-21T05:05:00Z", "3000", "3000", line = 2),
                tx("TJK4AB13EC", TxKind.SEND, "2026-03-21T06:00:00Z", "100", "900"),
                tx("TJK4AB13ED", TxKind.SEND, "2026-03-21T08:00:00Z", "50", "650"),
                tx("TJK4AB13EE", TxKind.BUY_GOODS, "2026-03-21T09:00:00Z", "50", "600", line = 1),
                tx("TJK4AB13EF", TxKind.BUY_GOODS, "2026-03-21T09:30:00Z", "100", "2900", line = 2),
            ),
            lines,
        )
        assertEquals(mapOf("TJK4AB13EC" to 1L, "TJK4AB13ED" to 1L), r.placed)
        assertEquals(0, r.left)
    }

    @Test
    fun `two payments in the same minute are taken in the order their balances run`() {
        // M-Pesa times have no seconds: both say 9:00 AM, and their codes sort the wrong way round. The balances say
        // which came first.
        val r = LinePlacer.place(
            listOf(
                tx("TJK4AB13FA", TxKind.RECEIVE, "2026-03-21T05:00:00Z", "1000", "1000", line = 1),
                tx("TJK4AB13FB", TxKind.RECEIVE, "2026-03-21T05:01:00Z", "3000", "3000", line = 2),
                tx("TJK4AB13FD", TxKind.SEND, "2026-03-21T06:00:00Z", "100", "900"),
                tx("TJK4AB13FC", TxKind.SEND, "2026-03-21T06:00:00Z", "100", "800"),
            ),
            lines,
        )
        assertEquals(mapOf("TJK4AB13FD" to 1L, "TJK4AB13FC" to 1L), r.placed)
    }

    @Test
    fun `payments already on a line, and those with no balance, are never placed`() {
        val companion = tx("TJK4AB13GC", TxKind.FULIZA_ONLY, "2026-03-21T07:00:00Z", "463", null)
        val r = LinePlacer.place(
            listOf(
                tx("TJK4AB13GA", TxKind.RECEIVE, "2026-03-21T05:00:00Z", "1000", "1000", line = 1),
                tx("TJK4AB13GB", TxKind.RECEIVE, "2026-03-21T05:01:00Z", "3000", "3000", line = 2),
                companion,
            ),
            lines,
        )
        assertEquals(emptyMap(), r.placed)
        assertEquals(1, r.left)
    }

    @Test
    fun `with no lines nothing is placed`() {
        val r = LinePlacer.place(listOf(tx("TJK4AB13HA", TxKind.RECEIVE, "2026-03-21T05:00:00Z", "1000", "1000")), emptySet())
        assertEquals(LinePlacement(emptyMap(), 1), r)
    }
}
