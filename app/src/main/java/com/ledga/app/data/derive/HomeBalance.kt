package com.ledga.app.data.derive

import com.ledga.app.data.room.BalanceReading
import java.time.Instant

/** One line's latest balance, a part of the All lines total. */
data class LineBalance(val lineId: Long, val cents: Long, val updatedAt: Instant)

/**
 * Home's balance (spec §7.5): the amount, when M-Pesa stated it, the line the newest reading came from, and under All
 * lines each line's part of the total ([lines]).
 */
data class HomeBalance(val cents: Long, val updatedAt: Instant, val fromLineId: Long?, val lines: List<LineBalance> = emptyList()) {
    companion object {
        /**
         * [lineId] set: that line's latest balance. All lines: Σ of each line's latest, or the latest overall when no
         * reading has a line ([LedgerQueries.combine]'s rule); [updatedAt] is the newest of the readings added up.
         */
        fun of(readings: List<BalanceReading>, lineId: Long?): HomeBalance? {
            if (lineId != null) {
                return readings.firstOrNull { it.lineId == lineId }?.let { HomeBalance(it.balanceCents, it.occurredAt, it.lineId) }
            }
            val pick = readings.filter { it.lineId != null }.ifEmpty { readings }
            val newest = pick.maxByOrNull { it.occurredAt } ?: return null
            val parts = pick.mapNotNull { r -> r.lineId?.let { LineBalance(it, r.balanceCents, r.occurredAt) } }
            return HomeBalance(pick.sumOf { it.balanceCents }, newest.occurredAt, newest.lineId, parts)
        }
    }
}
