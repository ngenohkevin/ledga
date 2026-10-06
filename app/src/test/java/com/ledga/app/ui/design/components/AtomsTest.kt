package com.ledga.app.ui.design.components

import org.junit.Test
import kotlin.test.assertEquals

class AtomsTest {

    @Test
    fun `initials take the first letter of up to two words`() {
        assertEquals("JD", Initials.of("Jane Doe"))
        assertEquals("N", Initials.of("naivas"))
        assertEquals("KP", Initials.of("KENYA POWER - APP"))
        assertEquals("#", Initials.of("0712***678"))
        assertEquals("#", Initials.of("   "))
    }

    @Test
    fun `amount interpolation lands exactly on the target`() {
        assertEquals(100L, lerpCents(100, 300, 0f))
        assertEquals(200L, lerpCents(100, 300, 0.5f))
        assertEquals(300L, lerpCents(100, 300, 1f))
        assertEquals(123_456_789_012L, lerpCents(0, 123_456_789_012L, 1f))
    }
}
