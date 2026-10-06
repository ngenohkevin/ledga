package com.ledga.app.data.edit

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** R74: twelve named swatches, each a light and a dark #RRGGBB. */
class CategoryLooksTest {
    private val hex = Regex("#[0-9A-F]{6}")

    @Test
    fun `twelve swatches with distinct names and colours, each stored as light and dark hex`() {
        val s = CategoryLooks.SWATCHES
        assertEquals(12, s.size)
        assertEquals(12, s.map { it.name }.toSet().size)
        assertEquals(12, s.map { it.light }.toSet().size)
        assertEquals(12, s.map { it.dark }.toSet().size)
        s.forEach {
            assertTrue(hex.matches(it.light), it.name)
            assertTrue(hex.matches(it.dark), it.name)
        }
    }
}
