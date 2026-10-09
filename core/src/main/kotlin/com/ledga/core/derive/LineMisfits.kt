package com.ledga.core.derive

/**
 * R194 (owner, 2026-10-09): payments on the wrong line, proven by the balances. On the owner's phone, seven of Line 1's
 * payments sat on Line 2 (no SIM id, no choice of his), each one breaking both lines' balances.
 *
 * At a break on line A (payment `t`'s stated balance isn't the one before it plus `t`'s movement), a payment `x` on
 * another line belongs on A when all of these hold:
 * - A's balance before the break, as [BalanceChain] carried it, plus `x`'s movement is exactly `x`'s stated balance;
 * - `x`'s balance plus `t`'s movement is exactly `t`'s stated balance;
 * - `x` is a break on its own line too, so a coincidence can't move a payment that fits where it is;
 * - it is the only such payment;
 * - the person didn't put it on its line ([chosen]);
 * - moving it leaves fewer breaks in all, and none more on its own line.
 *
 * Every balance comes from [BalanceChain]'s own reading (final review I1: plain time order missed its reordering within
 * a minute and its running through payments with no stated balance). After each move the lines are checked again.
 * A known limit: two misfiled payments in one gap aren't found, as neither bridges it alone. Payments not on a line
 * are [LinePlacer]'s.
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
        val report = BalanceChain.check(rows)
        val breaksOn = report.lines.associate { it.lineId to it.breaks.mapTo(HashSet()) { b -> b.code } }
        val onLines = rows.filter { it.lineId != null }.sortedWith(ORDER)
        val byLine = onLines.groupBy { it.lineId!! }
        for (chain in report.lines) {
            val line = chain.lineId ?: continue
            val list = byLine[line] ?: continue
            for (b in chain.breaks) {
                val i = list.indexOfFirst { it.code == b.code }
                val t = list.getOrNull(i) ?: continue
                val tMove = BalanceChain.walletDelta(t) ?: continue
                // The balance the chain carried into the break: what it expected less t's own movement.
                val start = b.expected - tMove
                // Only payments since A's last stated balance: the list is in time order, so the window is a slice of it.
                val before = (i - 1 downTo 0).asSequence().map { list[it] }.firstOrNull { it.balance != null } ?: continue
                val from = firstAtOrAfter(onLines, before)
                val fits = onLines.subList(from, onLines.size).asSequence().takeWhile { it.occurredAt <= t.occurredAt }.filter { x ->
                    val xBalance = x.balance ?: return@filter false
                    if (x.lineId == line || x.code in fixed || x.code !in breaksOn[x.lineId].orEmpty()) return@filter false
                    val xMove = BalanceChain.walletDelta(x) ?: return@filter false
                    start + xMove == xBalance && xBalance + tMove == t.balance
                }.toList()
                val x = fits.singleOrNull() ?: continue
                if (improves(rows, report, x, line)) return x.code to line
            }
        }
        return null
    }

    /** Moving [x] to [line] leaves fewer breaks in all and none more on x's own line. */
    private fun improves(rows: List<DerivedTx>, before: BalanceChainReport, x: DerivedTx, line: Long): Boolean {
        val after = BalanceChain.check(rows.map { if (it.code == x.code) it.copy(lineId = line) else it })
        fun count(r: BalanceChainReport, l: Long? = null) = r.lines.filter { l == null || it.lineId == l }.sumOf { it.breaks.size }
        return count(after) < count(before) && count(after, x.lineId) <= count(before, x.lineId)
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
}
