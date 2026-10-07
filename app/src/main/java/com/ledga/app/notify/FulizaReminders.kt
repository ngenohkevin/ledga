package com.ledga.app.notify

import com.ledga.app.data.derive.FulizaStatus
import com.ledga.app.data.room.FulizaReading
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.ui.tx.TxText
import com.ledga.core.time.Periods
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One reminder owed today (R105): [daysLeft] 0 is "due today"; 1–3 is the reminder three days before. */
data class FulizaDue(val lineId: Long?, val outstandingCents: Long, val dueDate: LocalDate, val daysLeft: Int)

object FulizaReminders {
    /**
     * Spec §11 (R105): the reminders owed on [today], one per line that owes Fuliza with a due date 0–3 days away. Like
     * Home's Fuliza strip (R58), the lines' own readings count when any reading has a line; repaid lines owe nothing.
     */
    fun due(readings: List<FulizaReading>, today: LocalDate): List<FulizaDue> {
        val groups = FulizaStatus.perLine(readings)
        val lines = groups.filterKeys { it != null }.ifEmpty { groups }
        return lines.mapNotNull { (lineId, status) ->
            val due = status.dueDate ?: return@mapNotNull null
            val days = ChronoUnit.DAYS.between(today, due)
            if (status.outstanding.cents <= 0L || days !in 0L..3L) null else FulizaDue(lineId, status.outstanding.cents, due, days.toInt())
        }.sortedBy { it.lineId ?: Long.MIN_VALUE }
    }

    /** Spec §7.1: `fuliza-due:<line>:<date>:<3d|0d>`; payments not on a line read "none". */
    fun key(d: FulizaDue): String = "fuliza-due:${d.lineId ?: "none"}:${d.dueDate}:${if (d.daysLeft == 0) "0d" else "3d"}"
}

/** The 9 AM check (R105): today's reminders, each written once (its key). */
class FulizaCheck(private val db: LedgaDatabase, private val notifier: Notifier, private val clock: Clock) {
    /** The reminders [today] owes; the line is named only on a phone with two or more lines. */
    suspend fun alerts(today: LocalDate): List<Alert> {
        val lines = db.linesDao().all()
        val labels = lines.associate { it.id to TxText.lineLabel(it) }
        return FulizaReminders.due(db.transactionsDao().fulizaReadings(), today).map { d ->
            AlertWords.fulizaDue(d, if (lines.size >= 2) d.lineId?.let(labels::get) else null)
        }
    }

    suspend fun check(): Int = alerts(Periods.dateOf(clock.instant())).count { notifier.send(it) }
}
