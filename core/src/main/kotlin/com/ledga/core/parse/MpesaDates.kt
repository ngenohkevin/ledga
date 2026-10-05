package com.ledga.core.parse

import com.ledga.core.time.Nairobi
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * M-Pesa prints local Kenyan time with no zone, e.g. "21/3/26 at 1:30 PM".
 * Always interpreted in Africa/Nairobi, whatever the device zone. Components are
 * extracted with a regex and validated by java.time (thread-safe, no formatter
 * locale quirks). Any invalid component yields null; callers decide the fallback.
 */
object MpesaDates {
    private val DATE = Regex("""^(\d{1,2})/(\d{1,2})/(\d{2}|\d{4})$""")
    private val TIME = Regex("""^(\d{1,2}):(\d{2})\s*([AaPp])[Mm]$""")
    private val DATE_TIME = Regex(
        """(\d{1,2}/\d{1,2}/(?:\d{4}|\d{2}))\s*at\s*(\d{1,2}:\d{2}\s*[AaPp][Mm])""",
    )

    fun parseDateTime(date: String, time: String): Instant? {
        val d = DATE.matchEntire(date.trim()) ?: return null
        val t = TIME.matchEntire(time.trim()) ?: return null
        val (day, month, yearText) = d.destructured
        val (hourText, minuteText, meridiem) = t.destructured
        val hour12 = hourText.toInt()
        val minute = minuteText.toInt()
        if (hour12 !in 1..12 || minute !in 0..59) return null
        val pm = meridiem.equals("p", ignoreCase = true)
        val hour24 = (hour12 % 12) + if (pm) 12 else 0
        val year = yearText.toInt().let { if (yearText.length == 2) 2000 + it else it }
        return try {
            LocalDateTime.of(year, month.toInt(), day.toInt(), hour24, minute).atZone(Nairobi.ZONE).toInstant()
        } catch (e: DateTimeException) {
            null
        }
    }

    /** First "D/M/YY at H:MM AM" anywhere in [text] (spacing tolerant), or null. */
    fun findDateTime(text: String): Instant? =
        DATE_TIME.find(text)?.let { parseDateTime(it.groupValues[1], it.groupValues[2]) }

    /** Fuliza due dates: "09/07/26" (dd/MM/yy). */
    fun parseDueDate(date: String): LocalDate? {
        val d = DATE.matchEntire(date.trim()) ?: return null
        val (day, month, yearText) = d.destructured
        val year = yearText.toInt().let { if (yearText.length == 2) 2000 + it else it }
        return try {
            LocalDate.of(year, month.toInt(), day.toInt())
        } catch (e: DateTimeException) {
            null
        }
    }
}
