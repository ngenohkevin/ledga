package com.ledga.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {

    private val mpesaDateFormat2 = SimpleDateFormat("d/M/yy 'at' h:mm a", Locale.ENGLISH)
    private val mpesaDateFormat4 = SimpleDateFormat("d/M/yyyy 'at' h:mm a", Locale.ENGLISH)
    // Alt format with extra spaces: "20/05/2024  at 03:23 PM"
    private val mpesaDateFormatAlt = SimpleDateFormat("d/M/yyyy 'at' hh:mm a", Locale.ENGLISH)

    fun parseMpesaDate(dateStr: String): Long {
        val cleaned = dateStr.trim().replace(Regex("""\s+"""), " ")
        return try {
            // Try 4-digit year first
            if (cleaned.matches(Regex("""\d{1,2}/\d{1,2}/\d{4}\s.*"""))) {
                mpesaDateFormat4.parse(cleaned)?.time ?: mpesaDateFormatAlt.parse(cleaned)?.time ?: System.currentTimeMillis()
            } else {
                mpesaDateFormat2.parse(cleaned)?.time ?: System.currentTimeMillis()
            }
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    fun formatRelativeDate(timestamp: Long): String {
        val now = Calendar.getInstance()
        val date = Calendar.getInstance().apply { timeInMillis = timestamp }

        return when {
            isSameDay(now, date) -> "Today"
            isYesterday(now, date) -> "Yesterday"
            else -> SimpleDateFormat("EEE, d MMM", Locale.ENGLISH).format(Date(timestamp))
        }
    }

    fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(timestamp))
    }

    fun formatMonthYear(timestamp: Long): String {
        return SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(Date(timestamp))
    }

    fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(timestamp))
    }

    fun greeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    fun getStartOfDay(timestamp: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun getStartOfWeek(timestamp: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun getStartOfMonth(timestamp: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(now: Calendar, date: Calendar): Boolean {
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return isSameDay(yesterday, date)
    }
}
