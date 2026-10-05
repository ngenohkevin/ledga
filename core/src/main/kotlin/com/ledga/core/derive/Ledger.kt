package com.ledga.core.derive

import com.ledga.core.model.FlowKind
import com.ledga.core.money.Money

/**
 * THE spending definition (spec §5.3), as a reference implementation.
 * Spent = Σ amount of SPEND + Σ fee of every transaction; Money in = Σ amount of INCOME;
 * hidden and reversed transactions count for nothing. Phase 2's `ledger` SQL view must
 * match these per-row values exactly (its tests compare against this object).
 */
object Ledger {
    fun spendCents(tx: DerivedTx): Long = if (counts(tx) && tx.flow == FlowKind.SPEND) tx.amount.cents else 0L
    fun inCents(tx: DerivedTx): Long = if (counts(tx) && tx.flow == FlowKind.INCOME) tx.amount.cents else 0L
    fun feeCents(tx: DerivedTx): Long = if (counts(tx)) tx.fee.cents else 0L

    fun spent(txs: Iterable<DerivedTx>): Money = Money(txs.sumOf { spendCents(it) + feeCents(it) })
    fun moneyIn(txs: Iterable<DerivedTx>): Money = Money(txs.sumOf { inCents(it) })

    private fun counts(tx: DerivedTx) = !tx.isHidden && !tx.isReversed
}
