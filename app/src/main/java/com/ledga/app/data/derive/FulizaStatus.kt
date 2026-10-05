package com.ledga.app.data.derive

import com.ledga.app.data.room.FulizaReading
import com.ledga.core.model.TxKind
import com.ledga.core.money.Money
import java.time.LocalDate

/**
 * A line's Fuliza position (spec §7.5): [ceiling] = a stated limit + the outstanding at that reading,
 * [available] = ceiling − current outstanding; [dueDate] only while something is owed.
 */
data class FulizaStatus(val outstanding: Money, val ceiling: Money?, val available: Money?, val dueDate: LocalDate?) {
    companion object {
        fun perLine(readings: List<FulizaReading>): Map<Long?, FulizaStatus> =
            readings.groupBy { it.lineId }.mapValues { (_, rows) -> of(rows) }

        /** [readings] for one line; order is enforced here (time, then code). */
        fun of(readings: List<FulizaReading>): FulizaStatus {
            var outstanding: Long? = null
            var ceiling: Long? = null
            var due: LocalDate? = null
            for (r in readings.sortedWith(compareBy({ it.occurredAt }, { it.code }))) {
                val stated = r.fulizaOutstandingCents
                if (stated != null) {
                    outstanding = stated
                } else if (r.kind == TxKind.FULIZA_REPAY_AUTO || r.kind == TxKind.FULIZA_REPAY_MANUAL) {
                    // A partial auto-repay states a limit but no outstanding: lower the last known one.
                    outstanding = outstanding?.let { maxOf(0L, it - r.amountCents) }
                }
                r.fulizaDueDate?.let { due = it }
                r.fulizaLimitCents?.let { limit -> ceiling = limit + (outstanding ?: 0L) }
            }
            val owed = outstanding ?: 0L
            return FulizaStatus(
                outstanding = Money(owed),
                ceiling = ceiling?.let(::Money),
                available = ceiling?.let { Money(maxOf(0L, it - owed)) },
                dueDate = due.takeIf { owed > 0 },
            )
        }
    }
}
