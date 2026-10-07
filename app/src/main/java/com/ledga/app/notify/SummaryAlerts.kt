package com.ledga.app.notify

import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.core.time.InstantRange
import com.ledga.core.time.Period
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * The daily and weekly summaries (spec §11, R106): all lines, from the `ledger` view, so Spent counts fees like every
 * other "spent". Nothing spent writes nothing; a run more than [LATE] after its time writes nothing either.
 */
class SummaryAlerts(
    private val db: LedgaDatabase,
    private val ledger: LedgerQueries,
    private val notifier: Notifier,
    private val clock: Clock,
) {
    /** The summary of the Nairobi day [at] falls on, sent at most once. */
    suspend fun daily(at: Instant): Boolean {
        val now = clock.instant()
        if (Duration.between(at, now) > LATE) return false
        val alert = dailyAlert(Periods.dateOf(at), Periods.dateOf(now)) ?: return false
        return notifier.send(alert)
    }

    /** [day]'s summary as written on [today]; null when nothing was spent that day. */
    suspend fun dailyAlert(day: LocalDate, today: LocalDate): Alert? {
        val range = Period(PeriodType.DAY, day).range()
        val spent = ledger.spentIn(range)
        if (spent.cents <= 0) return null
        val biggest = ledger.biggest(range) ?: return null
        return AlertWords.daily(day, today, spent, biggest)
    }

    /** The week so far at [at], sent at most once. */
    suspend fun weekly(at: Instant): Boolean {
        if (Duration.between(at, clock.instant()) > LATE) return false
        val alert = weeklyAlert(at) ?: return false
        return notifier.send(alert)
    }

    /** Monday to [at] against last week to the same moment (spec §7.6); null when nothing was spent. */
    suspend fun weeklyAlert(at: Instant): Alert? {
        val week = Periods.of(PeriodType.WEEK, at)
        if (!at.isAfter(week.startInstant)) return null
        val range = InstantRange(week.startInstant, at)
        val spent = ledger.spentIn(range)
        if (spent.cents <= 0) return null
        val previous = ledger.spent(Periods.comparisonWindow(PeriodType.WEEK, at)).first().cents
        val top = ledger.spentByCategory(range).first().firstOrNull()?.let { t ->
            AlertWords.CategoryShare(db.categoriesDao().get(t.categoryKey)?.name ?: t.categoryKey, t.cents)
        }
        return AlertWords.weekly(week.start, spent.cents, previous, top)
    }

    companion object {
        /** R106: a summary this late is no longer news. */
        val LATE: Duration = Duration.ofHours(12)
    }
}
