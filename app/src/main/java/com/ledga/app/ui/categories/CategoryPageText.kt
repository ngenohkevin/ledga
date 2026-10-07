package com.ledga.app.ui.categories

import com.ledga.app.data.trackers.CategoryMeasure

/** What a category's page says (4e §3.3): its words follow what it counts. */
object CategoryPageText {
    fun measureLine(m: CategoryMeasure): String = when (m) {
        CategoryMeasure.SPENT -> "Spent, fees included"
        CategoryMeasure.RECEIVED -> "Money received"
        CategoryMeasure.MOVED -> "Money moved, either way"
    }

    fun emptyBody(m: CategoryMeasure): String = when (m) {
        CategoryMeasure.SPENT -> "Payments filed here show up month by month."
        CategoryMeasure.RECEIVED -> "Money received here shows up month by month."
        CategoryMeasure.MOVED -> "Money moved here shows up month by month."
    }

    fun placeLine(count: Int): String = "$count ${if (count == 1) "payment" else "payments"}"

    /** D4: Top places' window. */
    const val PLACES_NOTE = "The last 12 months and this one."
}
