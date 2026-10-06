package com.ledga.app.time

import com.ledga.app.testing.MutableClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals

/** Spec §7.6 (R34): live periods move at Nairobi midnight and when Ledga comes back to the screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveClockTest {

    @Test
    fun `today turns over at Nairobi midnight, whatever the phone's zone`() = runTest {
        val clock = MutableClock(Instant.parse("2026-03-31T20:59:30Z")) // 23:59:30 in Nairobi
        val waits = mutableListOf<Duration>()
        val live = LiveClock(clock) { d ->
            waits += d
            clock.instant = clock.instant.plus(d)
        }
        assertEquals(listOf(LocalDate.parse("2026-03-31"), LocalDate.parse("2026-04-01")), live.today.take(2).toList())
        // merge() may run the loop ahead of the collector before take(2) cancels it, so only the first wait is pinned.
        assertEquals(Duration.ofMillis(30_001), waits.first(), "it sleeps until just past Nairobi midnight")
    }

    @Test
    fun `coming back after sleeping through midnight moves today, and only once`() = runTest {
        val clock = MutableClock(Instant.parse("2026-03-31T18:00:00Z"))
        val live = LiveClock(clock) { awaitCancellation() } // the midnight timer never fires: the phone slept
        val seen = mutableListOf<LocalDate>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { live.today.toList(seen) }
        assertEquals(listOf(LocalDate.parse("2026-03-31")), seen)
        clock.instant = Instant.parse("2026-04-01T05:00:00Z")
        live.onResume()
        live.onResume()
        assertEquals(listOf(LocalDate.parse("2026-03-31"), LocalDate.parse("2026-04-01")), seen)
    }
}
