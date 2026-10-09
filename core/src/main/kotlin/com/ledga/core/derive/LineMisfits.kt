package com.ledga.core.derive

/**
 * R194 (owner, 2026-10-09): payments on the wrong line, proven by the balances. On the owner's phone, seven of Line 1's
 * payments sat on Line 2 (no SIM id, no choice of his), each one breaking both lines' balances.
 *
 * At a break on line A (payment `t`'s stated balance isn't the one before it plus `t`'s movement), a payment `x` on
 * another line belongs on A when all of these hold:
 * - A's balance before the break plus `x`'s movement is exactly `x`'s stated balance;
 * - `x`'s balance plus `t`'s movement is exactly `t`'s stated balance;
 * - `x` doesn't carry on its own line's balance (there it is a break too), so a coincidence can't move a payment that
 *   fits where it is;
 * - it is the only such payment;
 * - the person didn't put it on its line ([chosen]).
 *
 * After each move the lines are checked again. Payments not on a line are [LinePlacer]'s.
 */
object LineMisfits {
    private val ORDER = compareBy<DerivedTx>({ it.occurredAt }, { it.code })

    /** code → the line each misfiled payment belongs on. */
    fun find(txs: List<DerivedTx>, chosen: Set<String>): Map<String, Long> {
        val moves = linkedMapOf<String, Long>()
        var rows = txs
        while (true) {
            val (code, line) = next(rows, chosen + moves.keys) ?: return moves
            moves[code] = line
            rows = rows.map { if (it.code == code) it.copy(lineId = line) else it }
        }
    }

    private fun next(rows: List<DerivedTx>, fixed: Set<String>): Pair<String, Long>? {
        val onLines = rows.filter { it.lineId != null }.sortedWith(ORDER)
        val byLine = onLines.groupBy { it.lineId!! }
        for (chain in BalanceChain.check(rows).lines) {
            val line = chain.lineId ?: continue
            val list = byLine[line] ?: continue
            for (b in chain.breaks) {
                val i = list.indexOfFirst { it.code == b.code }
                val t = list.getOrNull(i) ?: continue
                val before = (i - 1 downTo 0).asSequence().map { list[it] }.firstOrNull { it.balance != null } ?: continue
                val start = before.balance ?: continue
                val tMove = BalanceChain.walletDelta(t) ?: continue
                // Only the payments between the two: the list is in time order, so the window is a slice of it.
                val from = firstAtOrAfter(onLines, before)
                val fits = onLines.subList(from, onLines.size).asSequence().takeWhile { it.occurredAt <= t.occurredAt }.filter { x ->
                    val xBalance = x.balance ?: return@filter false
                    if (x.lineId == line || x.code in fixed) return@filter false
                    val xMove = BalanceChain.walletDelta(x) ?: return@filter false
                    start + xMove == xBalance && xBalance + tMove == t.balance && !carriesOn(x, byLine.getValue(x.lineId!!))
                }.toList()
                fits.singleOrNull()?.let { return it.code to line }
            }
        }
        return null
    }

    /** The index of the first payment at or after [tx]'s time, in [sorted] (time order). */
    private fun firstAtOrAfter(sorted: List<DerivedTx>, tx: DerivedTx): Int {
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid].occurredAt < tx.occurredAt) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** Whether [x] carries on the balance before it on its own line; with nothing before it, the balances can't say. */
    private fun carriesOn(x: DerivedTx, line: List<DerivedTx>): Boolean {
        val i = line.indexOfFirst { it.code == x.code }
        val before = (i - 1 downTo 0).asSequence().map { line[it] }.firstOrNull { it.balance != null }?.balance ?: return true
        val move = BalanceChain.walletDelta(x) ?: return true
        return before + move == x.balance
    }
}
