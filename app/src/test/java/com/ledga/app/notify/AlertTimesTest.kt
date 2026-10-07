package com.ledga.app.notify

import java.time.Instant
import kotlin.test.assertEquals
import org.junit.Test

/** Spec §11: when each scheduled alert fires, in Nairobi time (UTC+3), strictly after now. */
class AlertTimesTest {
    private fun at(iso: String) = Instant.parse(iso)

    @Test
    fun `the daily summary is the next one at its time, strictly after now`() {
        // 7:59:59 PM on Wed 7 Oct 2026 in Nairobi
        assertEquals(at("2026-10-07T17:00:00Z"), AlertTimes.nextDaily(at("2026-10-07T16:59:59Z"), 20 * 60))
        assertEquals(at("2026-10-08T17:00:00Z"), AlertTimes.nextDaily(at("2026-10-07T17:00:00Z"), 20 * 60), "asked at its own time")
        // midnight: from 11:59 PM on Wed 7 Oct to 12:00 AM on Thu 8 Oct
        assertEquals(at("2026-10-07T21:00:00Z"), AlertTimes.nextDaily(at("2026-10-07T20:59:00Z"), 0))
        // 10:30 PM UTC on 7 Oct is already 1:30 AM on 8 Oct in Nairobi
        assertEquals(at("2026-10-08T17:00:00Z"), AlertTimes.nextDaily(at("2026-10-07T22:30:00Z"), 20 * 60))
    }

    @Test
    fun `the weekly summary is the next Sunday at 7 PM`() {
        assertEquals(at("2026-10-11T16:00:00Z"), AlertTimes.nextWeekly(at("2026-10-07T09:00:00Z")), "from a Wednesday")
        assertEquals(at("2026-10-11T16:00:00Z"), AlertTimes.nextWeekly(at("2026-10-11T15:59:00Z")), "Sunday 6:59 PM")
        assertEquals(at("2026-10-18T16:00:00Z"), AlertTimes.nextWeekly(at("2026-10-11T16:00:00Z")), "Sunday 7 PM itself")
        assertEquals(at("2026-10-18T16:00:00Z"), AlertTimes.nextWeekly(at("2026-10-11T21:30:00Z")), "Sunday in UTC, Monday in Nairobi")
    }

    @Test
    fun `the Fuliza check is at 9 AM every day`() {
        assertEquals(at("2026-10-08T06:00:00Z"), AlertTimes.nextFuliza(at("2026-10-07T16:30:00Z")))
        assertEquals(at("2026-10-07T06:00:00Z"), AlertTimes.nextFuliza(at("2026-10-07T05:59:00Z")))
    }
}
