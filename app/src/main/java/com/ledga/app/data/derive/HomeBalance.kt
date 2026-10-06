package com.ledga.app.data.derive

import com.ledga.app.data.room.BalanceReading
import java.time.Instant

/** Home's balance (spec §7.5): the amount, when M-Pesa stated it, and the line the newest reading came from. */
data class HomeBalance(val cents: Long, val updatedAt: Instant, val fromLineId: Long?) {
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
            return HomeBalance(pick.sumOf { it.balanceCents }, newest.occurredAt, newest.lineId)
        }
    }
}
