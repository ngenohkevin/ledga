package com.ledga.app.util

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

object CurrencyFormatter {

    private val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    fun formatKsh(amount: Double): String {
        val formatted = formatter.format(abs(amount))
        val prefix = if (amount < 0) "-" else ""
        return "${prefix}Ksh $formatted"
    }

    fun formatKshSigned(amount: Double, isInflow: Boolean): String {
        val formatted = formatter.format(abs(amount))
        val sign = if (isInflow) "+" else "-"
        return "${sign}Ksh $formatted"
    }

    fun formatKshCompact(amount: Double): String {
        val abs = abs(amount)
        val prefix = if (amount < 0) "-" else ""
        return when {
            abs >= 1_000_000 -> "${prefix}Ksh ${String.format("%.1fM", abs / 1_000_000)}"
            abs >= 1_000 -> "${prefix}Ksh ${String.format("%.1fK", abs / 1_000)}"
            else -> formatKsh(amount)
        }
    }
}
