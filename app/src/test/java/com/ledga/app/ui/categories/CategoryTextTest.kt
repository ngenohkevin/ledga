package com.ledga.app.ui.categories

import kotlin.test.assertEquals
import org.junit.Test

class CategoryTextTest {
    @Test
    fun `a category row counts its rules and says how many are off (R67)`() {
        assertEquals("No rules", CategoryText.rulesLine(0, 0))
        assertEquals("1 rule", CategoryText.rulesLine(1, 0))
        assertEquals("1 rule · 1 off", CategoryText.rulesLine(0, 1))
        assertEquals("3 rules · 1 off", CategoryText.rulesLine(2, 1))
    }
}
