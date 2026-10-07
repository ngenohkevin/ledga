package com.ledga.app.ui.home

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals

/** R88: the strip shows n whole tiles and half of the next, each at least 13 caption sizes wide. */
class StripLayoutTest {
    @Test
    fun `a phone at normal text shows a tile and a half, and smaller text two and a half`() {
        assertEquals(224f, StripLayout.tileWidth(344.dp, 156.dp, 8.dp).value, 0.01f) // 360 dp, 12 sp captions
        assertEquals(131.2f, StripLayout.tileWidth(344.dp, 108.dp, 8.dp).value, 0.01f) // smaller system text
    }

    @Test
    fun `a narrow screen never goes below the minimum`() {
        assertEquals(156f, StripLayout.tileWidth(200.dp, 156.dp, 8.dp).value, 0.01f)
    }
}
