package com.ledga.core.derive

import com.ledga.core.model.TxKind
import com.ledga.core.money.Money
import java.time.Instant
import java.time.temporal.ChronoUnit

data class ChainBreak(val lineId: Long?, val code: String, val occurredAt: Instant, val expected: Money, val actual: Money)

data class LineChain(val lineId: Long?, val checked: Int, val gaps: Int, val breaks: List<ChainBreak>)

data class BalanceChainReport(val lines: List<LineChain>) {
    val checked: Int get() = lines.sumOf { it.checked }
    val gaps: Int get() = lines.sumOf { it.gaps }
    val breaks: List<ChainBreak> get() = lines.flatMap { it.breaks }
}

/**
 * "History check": per line, every stated wallet balance must equal the previous balance
 * plus this transaction's wallet movement. Breaks mean a parser bug or a missing SMS.
 */
object BalanceChain {

    fun walletDelta(tx: DerivedTx): Money? = when (tx.kind) {
        TxKind.SEND, TxKind.PAYBILL, TxKind.BUY_GOODS, TxKind.WITHDRAW_AGENT, TxKind.WITHDRAW_ATM,
        TxKind.AIRTIME_SELF, TxKind.AIRTIME_OTHER, TxKind.GLOBAL_SEND, TxKind.SAVINGS_OUT,
        TxKind.FULIZA_REPAY_AUTO, TxKind.FULIZA_REPAY_MANUAL -> {
            val walletFee = tx.fee - (tx.fulizaFee ?: Money.ZERO)
            -tx.amount - walletFee + (tx.fulizaDrawn ?: Money.ZERO)
        }
        TxKind.RECEIVE, TxKind.GLOBAL_RECEIVE, TxKind.DEPOSIT, TxKind.SAVINGS_IN -> tx.amount
        TxKind.REVERSAL -> tx.amount.takeUnless { it.isZero }
        TxKind.FULIZA_ONLY, TxKind.FULIZA_REVERSAL -> null
    }

    fun check(txs: List<DerivedTx>): BalanceChainReport = BalanceChainReport(
        txs.groupBy { it.lineId }
            .map { (lineId, rows) -> checkLine(lineId, rows) }
            .sortedBy { it.lineId ?: Long.MIN_VALUE },
    )

    private fun checkLine(lineId: Long?, rows: List<DerivedTx>): LineChain {
        var running: Money? = null
        var checked = 0
        var gaps = 0
        val breaks = mutableListOf<ChainBreak>()
        val minutes = rows.sortedWith(compareBy({ it.occurredAt }, { it.code }))
            .groupBy { it.occurredAt.truncatedTo(ChronoUnit.MINUTES) }
        for (group in minutes.values) {
            val pending = group.toMutableList()
            while (pending.isNotEmpty()) {
                val current = running
                val next = current?.let { r ->
                    pending.firstOrNull { t ->
                        val d = walletDelta(t)
                        d != null && t.balance != null && r + d == t.balance
                    }
                } ?: pending.first()
                pending.remove(next)
                val delta = walletDelta(next)
                val stated = next.balance
                when {
                    delta == null -> { gaps++; running = stated }
                    current == null -> running = stated
                    stated == null -> running = current + delta
                    else -> {
                        checked++
                        val expected = current + delta
                        if (expected != stated) breaks += ChainBreak(lineId, next.code, next.occurredAt, expected, stated)
                        running = stated
                    }
                }
            }
        }
        return LineChain(lineId, checked, gaps, breaks)
    }
}
