package com.ledga.app.time

import com.ledga.core.time.Nairobi
import com.ledga.core.time.Periods
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Spec §7.6 (R34): what "now" is for live periods ("today", "this month"). [today] emits the Nairobi date at once,
 * again just past every Nairobi midnight, and when Ledga comes back to the screen ([onResume]), because a phone that
 * slept through midnight wakes with a late timer. It never repeats a date. [sleep] is a seam for tests.
 */
class LiveClock(
    private val clock: Clock,
    private val sleep: suspend (Duration) -> Unit = { delay(it.toMillis()) },
) {
    private val resumes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    // Each source reads the clock when it fires, not when the collector gets to it: merge() buffers, so a date read
    // downstream could belong to a later moment than the midnight or resume that produced it.
    val today: Flow<LocalDate> = merge(midnights(), resumes.map { Periods.dateOf(clock.instant()) })
        .distinctUntilChanged()

    /**
     * The moment, at once, just past every Nairobi hour, and when Ledga comes back to the screen: for text that changes
     * during a day (Home's greeting, R60). A screen that needs the date too derives it from here, so it runs one timer.
     */
    val hours: Flow<Instant> = merge(hourly(), resumes.map { clock.instant() })

    fun now(): Instant = clock.instant()

    /** `MainActivity.onResume`: re-reads the date, in case midnight passed while the phone slept. */
    fun onResume() {
        resumes.tryEmit(Unit)
    }

    private fun hourly(): Flow<Instant> = flow {
        while (true) {
            emit(clock.instant())
            val now = clock.instant()
            sleep(Duration.between(now, now.atZone(Nairobi.ZONE).truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant()).plusMillis(1))
        }
    }

    private fun midnights(): Flow<LocalDate> = flow {
        while (true) {
            emit(Periods.dateOf(clock.instant()))
            val now = clock.instant()
            sleep(Duration.between(now, Periods.nextMidnight(now)).plusMillis(1))
        }
    }
}
