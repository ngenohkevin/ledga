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

        /**
         * Home's Fuliza (R58): [lineId]'s status, or under all lines each line's status added up (the lines' own
         * readings when any reading has a line, as for the balance). Null when there is no Fuliza reading at all.
         */
        fun forLine(readings: List<FulizaReading>, lineId: Long?): FulizaStatus? {
            if (lineId != null) return readings.filter { it.lineId == lineId }.takeIf { it.isNotEmpty() }?.let(::of)
            val groups = perLine(readings)
            val pick = groups.filterKeys { it != null }.values.ifEmpty { groups.values }
            return if (pick.isEmpty()) null else combine(pick)
        }

        /**
         * Owed adds up and the earliest due date leads. Ceiling and available are known only when every line's are:
         * a total built from some lines alone would understate what can be borrowed and read as the whole truth.
         */
        fun combine(statuses: Collection<FulizaStatus>): FulizaStatus = FulizaStatus(
            outstanding = Money(statuses.sumOf { it.outstanding.cents }),
            ceiling = if (statuses.all { it.ceiling != null }) Money(statuses.sumOf { it.ceiling!!.cents }) else null,
            available = if (statuses.all { it.available != null }) Money(statuses.sumOf { it.available!!.cents }) else null,
            dueDate = statuses.mapNotNull { it.dueDate }.minOrNull(),
        )
    }
}
