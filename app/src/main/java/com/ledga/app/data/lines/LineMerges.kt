package com.ledga.app.data.lines

import androidx.room.withTransaction
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.LineRow
import com.ledga.app.data.room.toDerived
import com.ledga.app.data.settings.SettingsStore
import com.ledga.core.derive.BalanceChain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

/** R176: [from]'s history ends where [into]'s begins, with the balance carrying on: one number on two lines. */
data class LineMergeSuggestion(val from: LineRow, val into: LineRow)

/**
 * One number's history split across two lines: the SIM moved to a new phone, or became an eSIM, and got a new
 * subscription id that nothing linked to the old one (no readable number). Ledga suggests; the person decides (R177).
 */
class LineMerges(
    private val db: LedgaDatabase,
    private val sims: SimDirectory,
    private val settings: SettingsStore,
    private val deriver: Deriver,
    /** Re-derives the moved payments; a test makes it fail part-way (final review I2). */
    private val rederive: suspend (Collection<String>) -> Unit = { deriver.rederive(it) },
) {
    /** The one merge to suggest now, re-read when lines, payments or the dismissed pairs change. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val suggestion: Flow<LineMergeSuggestion?> = combine(
        db.linesDao().observeAll(),
        db.transactionsDao().observeSpan(),
        settings.settings.map { it.lineMergesDismissed }.distinctUntilChanged(),
    ) { _, _, _ -> }.mapLatest { find() }

    /** R176: the first pair of lines that looks like one number, or null. */
    suspend fun find(): LineMergeSuggestion? {
        val lines = db.linesDao().all()
        if (lines.size < 2) return null
        val dismissed = settings.current().lineMergesDismissed
        val active = sims.active().map { it.subscriptionId }.toSet()
        val tx = db.transactionsDao()
        for (from in lines) {
            if (active.isNotEmpty() && from.subscriptionId in active) continue
            val last = tx.lastWithBalance(from.id) ?: continue
            val lastBalance = last.balanceCents ?: continue
            // Final review I3: a line that ended at nothing "carries on" into any new number's first balance.
            if (lastBalance == 0L) continue
            for (into in lines) {
                if (into.id == from.id || key(from.id, into.id) in dismissed) continue
                if (active.isNotEmpty() && into.subscriptionId !in active) continue
                val first = tx.firstWithBalance(into.id) ?: continue
                if (!last.occurredAt.isBefore(first.occurredAt)) continue
                if (tx.countOnLineThrough(into.id, last.occurredAt) > 0) continue // used side by side: two numbers
                val delta = BalanceChain.walletDelta(first.toDerived()) ?: continue
                if (first.balanceCents == lastBalance + delta.cents) return LineMergeSuggestion(from, into)
            }
        }
        return null
    }

    /**
     * R178: [from]'s messages and the person's placements move to [into], which keeps its colour and SIM, and its name
     * unless that is still an automatic "Line N": then it takes [from]'s (owner, 2026-10-08).
     */
    suspend fun merge(from: Long, into: Long) {
        val lines = db.linesDao()
        db.withTransaction {
            val moved = lines.codesOnLine(from)
            val old = lines.get(from)
            // Final review I5: messages carried over from v1 have no SIM id; stamping the old line's lets that id find
            // the line kept (lineOfPastMessages) instead of creating an empty line at the next rescan.
            old?.subscriptionId?.let { lines.stampSubscription(from, it) }
            lines.moveSms(from, into)
            lines.moveOverrides(from, into)
            if (old?.isPrimary == true) lines.setPrimary(into)
            // Owner (2026-10-08): the line kept carries on the old one, so while its own name is still an automatic
            // "Line N" it takes the name the person has known the number by.
            val kept = lines.get(into)
            if (old != null && kept != null && LinesRepository.AUTO_NAME.matches(kept.displayName)) lines.rename(into, old.displayName)
            lines.delete(from)
            // Final review I2: re-derived inside the same transaction, so a merge cut short changes nothing.
            rederive(moved)
        }
        if (settings.current().selectedLineId == from) settings.setSelectedLine(into)
    }

    /** R179: "Not the same": this pair isn't suggested again on this phone. */
    suspend fun dismiss(from: Long, into: Long) = settings.dismissLineMerge(key(from, into))

    companion object {
        fun key(from: Long, into: Long) = "$from>$into"
    }
}
