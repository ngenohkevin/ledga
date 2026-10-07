package com.ledga.core.derive

import com.ledga.core.money.Money
import java.time.Instant
import java.time.temporal.ChronoUnit

/** [placed]: code → line, for payments not on a line that Ledga could place. [left]: those it couldn't. */
data class LinePlacement(val placed: Map<String, Long>, val left: Int)

/**
 * Owner call C (R116): which line a payment that isn't on one belongs to, from the balances alone.
 *
 * A payment's balance after is the one M-Pesa stated; its balance before is that minus its own wallet movement
 * ([BalanceChain.walletDelta]). Walking forward, each line has a running balance and a payment fits a line when its
 * balance before equals it; walking back from the newest, a line's running value is what its next payment started from
 * and a payment fits when its balance after equals it. A payment is placed only when it fits exactly one line while
 * every line's running balance is known (a line not seen yet could be its line). Payments already on a line are the
 * anchors, and a placed one counts as on its line; passes alternate forward and back until a round places nothing.
 * Within one minute the SMS order isn't known, so the next payment handled is whichever fits, else the first.
 *
 * A line's running balance is kept only while it is certain (final review C2). It is dropped when a payment on the line
 * says nothing about the balance (a Fuliza companion whose payment never came); when a payment Ledga couldn't place
 * could be that line's (it fits it, or its balance is unknown); and for every line when a payment fits none while all
 * are known (some line lost a message, and Ledga can't tell which). A dropped line is known again at its next anchor.
 */
object LinePlacer {
    private class Step(val code: String, val line: Long?, val at: Instant, val delta: Money?, val after: Money?) {
        val before: Money? = if (delta != null && after != null) after - delta else null
    }

    fun place(txs: List<DerivedTx>, lineIds: Set<Long>): LinePlacement {
        val steps = txs.map { Step(it.code, it.lineId?.takeIf(lineIds::contains), it.occurredAt, BalanceChain.walletDelta(it), it.balance) }
        val placed = linkedMapOf<String, Long>()
        if (lineIds.isNotEmpty()) {
            val forward = steps.sortedWith(compareBy<Step>({ it.at }, { it.code }))
                .groupBy { it.at.truncatedTo(ChronoUnit.MINUTES) }.values.toList()
            val backward = forward.asReversed().map { it.asReversed() }
            do {
                val before = placed.size
                pass(forward, lineIds, placed, back = false)
                pass(backward, lineIds, placed, back = true)
            } while (placed.size > before)
        }
        return LinePlacement(placed, steps.count { it.line == null && it.code !in placed })
    }

    private fun pass(minutes: List<List<Step>>, lines: Set<Long>, placed: MutableMap<String, Long>, back: Boolean) {
        val running = HashMap<Long, Money>()
        fun owner(s: Step): Long? = s.line ?: placed[s.code]
        // Walking forward a payment joins its line at its balance before and leaves it at its balance after; walking
        // back, the other way round.
        fun entry(s: Step): Money? = if (back) s.after else s.before
        fun exit(s: Step): Money? = if (back) s.before else s.after
        fun fitting(s: Step): List<Long> {
            val e = entry(s) ?: return emptyList()
            if (lines.any { it !in running }) return emptyList()
            return lines.filter { running[it] == e }
        }
        fun continues(s: Step): Boolean {
            val o = owner(s) ?: return fitting(s).size == 1
            val e = entry(s) ?: return false
            return running[o] == e
        }
        for (minute in minutes) {
            val pending = minute.toMutableList()
            while (pending.isNotEmpty()) {
                val next = pending.firstOrNull(::continues) ?: pending.first()
                pending.remove(next)
                val line = owner(next) ?: fitting(next).singleOrNull()?.also { placed[next.code] = it }
                if (line == null) {
                    forget(next, lines, running, back)
                    continue
                }
                val moved = next.delta?.let { d -> running[line]?.let { r -> if (back) r - d else r + d } }
                val value = exit(next) ?: moved
                if (value == null) running.remove(line) else running[line] = value
            }
        }
    }

    /**
     * A payment Ledga couldn't place belongs to some line it can't name: every known line that could be that line loses
     * its running balance. [running] holds only certain balances, so a known line whose balance isn't where this payment
     * started can't be its line — unless every line is known and none fits, which means a line lost a message.
     */
    private fun forget(step: Step, lines: Set<Long>, running: HashMap<Long, Money>, back: Boolean) {
        val entry = if (back) step.after else step.before
        if (entry == null) {
            running.clear() // its balance is unknown: any line could be its line
            return
        }
        val fits = running.filterValues { it == entry }.keys
        when {
            fits.isNotEmpty() -> fits.forEach(running::remove)
            lines.all { it in running } -> running.clear()
        }
    }
}
