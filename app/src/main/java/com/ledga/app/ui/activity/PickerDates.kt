package com.ledga.app.ui.activity

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * M3's date picker speaks UTC midnights; Ledga's days are Nairobi dates (R70). Converting through UTC, never through the
 * phone's zone, keeps the day the person tapped whatever zone the phone is set to.
 */
object PickerDates {
    fun toMillis(day: LocalDate): Long = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromMillis(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    /** Nothing has happened after today, so later days can't be picked. */
    @OptIn(ExperimentalMaterial3Api::class)
    fun selectable(today: LocalDate): SelectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean = !fromMillis(utcTimeMillis).isAfter(today)

        override fun isSelectableYear(year: Int): Boolean = year <= today.year
    }
}
