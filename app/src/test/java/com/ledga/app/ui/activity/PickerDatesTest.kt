package com.ledga.app.ui.activity

import java.time.LocalDate
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** R70: M3's date picker speaks UTC midnights; Ledga's days come back the same on any phone zone. */
class PickerDatesTest {
    private fun inZone(id: String, block: () -> Unit) {
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(id))
            block()
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test
    fun `a picked day comes back as the same day west of UTC, in Nairobi and east of it`() {
        listOf("America/Los_Angeles", "Africa/Nairobi", "Pacific/Auckland").forEach { zone ->
            inZone(zone) {
                val day = LocalDate.parse("2026-03-01")
                assertEquals(day, PickerDates.fromMillis(PickerDates.toMillis(day)), zone)
                // What the picker hands back for 1 March: 00:00 UTC that day.
                assertEquals(day, PickerDates.fromMillis(1_772_323_200_000L), zone)
            }
        }
    }

    @Test
    fun `days after today can't be picked`() {
        val today = LocalDate.parse("2026-10-06")
        val dates = PickerDates.selectable(today)
        assertTrue(dates.isSelectableDate(PickerDates.toMillis(today)))
        assertTrue(dates.isSelectableDate(PickerDates.toMillis(today.minusYears(3))))
        assertFalse(dates.isSelectableDate(PickerDates.toMillis(today.plusDays(1))))
        assertFalse(dates.isSelectableYear(2027))
    }
}
