package com.ledga.app.ui.categories

import com.ledga.app.data.trackers.CategoryMeasure
import org.junit.Test
import kotlin.test.assertEquals

/** 4e §3.3: the page's words follow what it counts. */
class CategoryPageTextTest {
    @Test
    fun `the measure line and the empty state say spent, received or moved`() {
        assertEquals("Spent, fees included", CategoryPageText.measureLine(CategoryMeasure.SPENT))
        assertEquals("Money received", CategoryPageText.measureLine(CategoryMeasure.RECEIVED))
        assertEquals("Money moved, either way", CategoryPageText.measureLine(CategoryMeasure.MOVED))
        assertEquals("Payments filed here show up month by month.", CategoryPageText.emptyBody(CategoryMeasure.SPENT))
        assertEquals("Money received here shows up month by month.", CategoryPageText.emptyBody(CategoryMeasure.RECEIVED))
        assertEquals("Money moved here shows up month by month.", CategoryPageText.emptyBody(CategoryMeasure.MOVED))
    }

    @Test
    fun `a top place's line counts its payments`() {
        assertEquals("1 payment", CategoryPageText.placeLine(1))
        assertEquals("5 payments", CategoryPageText.placeLine(5))
    }
}
