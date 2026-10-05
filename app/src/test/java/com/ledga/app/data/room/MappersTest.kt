package com.ledga.app.data.room

import com.ledga.core.derive.DerivedTx
import com.ledga.core.derive.Override
import com.ledga.core.derive.Rule
import com.ledga.core.derive.RuleAction
import com.ledga.core.derive.RuleField
import com.ledga.core.derive.RuleOrigin
import com.ledga.core.model.FlowKind
import com.ledga.core.model.TxKind
import com.ledga.core.money.Money
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals

class MappersTest {
    private val full = DerivedTx(
        code = "TJK4AB12EA", lineId = 2, occurredAt = Instant.parse("2026-06-09T16:48:00Z"), occurredAtApprox = false,
        kind = TxKind.BUY_GOODS, flow = FlowKind.SPEND, amount = Money(250_000), fee = Money(463), balance = Money.ZERO,
        counterpartyName = "SAMPLE SUPERMARKET", counterpartyPhone = null, counterpartyAccount = null, counterpartyKey = "SAMPLE SUPERMARKET",
        destinationCountry = null, reversesCode = null, isReversed = false, fulizaDrawn = Money(46_300), fulizaFee = Money(463),
        fulizaOutstanding = Money(641_836), fulizaLimit = null, fulizaDueDate = LocalDate.of(2026, 11, 2), categoryKey = "groceries",
        note = "weekly shop", isHidden = false, searchText = "sample supermarket tjk4ab12ea 2500", smsCount = 2,
    )

    @Test
    fun `a derived transaction survives the row round trip`() {
        assertEquals(full, full.toRow().toDerived())
        val sparse = full.copy(lineId = null, balance = null, fulizaDrawn = null, fulizaFee = null, fulizaOutstanding = null, fulizaDueDate = null, note = null)
        assertEquals(sparse, sparse.toRow().toDerived())
    }

    @Test
    fun `overrides and rules map to core`() {
        val o = Override("TJK4AB12FB", categoryKey = "food", note = "lunch", lineId = 1, ownAccount = null, hidden = true)
        assertEquals(o, o.toRow(Instant.EPOCH).toCore())
        val r = RuleRow(7, RuleField.NAME_CONTAINS, "JANE", RuleAction.SET_CATEGORY, "food", RuleOrigin.USER, 3, Instant.EPOCH, enabled = false)
        assertEquals(Rule(7, RuleField.NAME_CONTAINS, "JANE", RuleAction.SET_CATEGORY, "food", RuleOrigin.USER, 3, Instant.EPOCH, enabled = false), r.toCore())
    }
}
