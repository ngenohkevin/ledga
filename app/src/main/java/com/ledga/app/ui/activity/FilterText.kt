package com.ledga.app.ui.activity

import com.ledga.app.data.derive.DateFilter
import com.ledga.app.ui.design.format.DateLabels

/** What the filter sheet says about a date choice (R69, R70). */
object FilterText {
    private val DASH = Char(0x2013)

    /** "This month", "September 2026", "2 Sep – 14 Oct 2026", "2 Dec 2025 – 3 Jan 2026", "14 Oct 2026". */
    fun dateLabel(d: DateFilter): String = when (d) {
        is DateFilter.Preset -> d.preset.label
        is DateFilter.Month -> DateLabels.monthYear(d.month)
        is DateFilter.Custom -> when {
            d.from == d.to -> DateLabels.date(d.from)
            d.from.year == d.to.year -> "${DateLabels.dayMonth(d.from)} $DASH ${DateLabels.date(d.to)}"
            else -> "${DateLabels.date(d.from)} $DASH ${DateLabels.date(d.to)}"
        }
    }
}
