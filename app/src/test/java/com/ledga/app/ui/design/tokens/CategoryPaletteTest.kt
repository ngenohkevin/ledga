package com.ledga.app.ui.design.tokens

import androidx.compose.ui.graphics.Color
import com.ledga.core.model.Categories
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CategoryPaletteTest {

    @Test
    fun `every seeded category has a light and a dark colour`() {
        Categories.SEED.forEach { assertNotNull(CategoryPalette.seeded(it.key), it.key) }
    }

    @Test
    fun `seeded colours are distinct in each theme`() {
        val seeded = Categories.SEED.map { CategoryPalette.seeded(it.key)!! }
        assertEquals(seeded.size, seeded.map { it.light }.toSet().size)
        assertEquals(seeded.size, seeded.map { it.dark }.toSet().size)
    }

    @Test
    fun `the approved mockup colours are kept`() {
        assertEquals(Color(0xFFD98A00), CategoryPalette.seeded(Categories.ELECTRICITY)!!.light)
        assertEquals(Color(0xFF5CB2FF), CategoryPalette.seeded(Categories.WATER)!!.dark)
        assertEquals(Color(0xFFD0414B), CategoryPalette.seeded(Categories.FULIZA)!!.light)
    }

    @Test
    fun `a seeded category with no stored colour uses its token`() {
        assertEquals(CategoryPalette.seeded(Categories.FUEL), CategoryPalette.resolve(Categories.FUEL, null, null))
    }

    @Test
    fun `a stored colour wins and gets a lighter dark variant`() {
        val r = CategoryPalette.resolve("legacy_7", "#4CAF50", null)
        assertEquals(Color(0xFF4CAF50), r.light)
        assertTrue(Contrast.luminance(r.dark) > Contrast.luminance(r.light))
    }

    @Test
    fun `a stored dark colour is used as is`() {
        val r = CategoryPalette.resolve("legacy_7", "#4CAF50", "#A5D6A7")
        assertEquals(Color(0xFFA5D6A7), r.dark)
    }

    @Test
    fun `malformed stored colours fall back without throwing`() {
        listOf("red", "", "   ", "#FFF", "#GG0000", "4CAF5", "#4CAF50FF00").forEach { bad ->
            assertEquals(CategoryPalette.NEUTRAL, CategoryPalette.resolve("legacy_7", bad, null), bad)
            assertEquals(CategoryPalette.seeded(Categories.WATER), CategoryPalette.resolve(Categories.WATER, bad, bad), bad)
        }
    }

    @Test
    fun `an eight-digit colour ignores its alpha`() {
        assertEquals(Color(0xFF4CAF50), CategoryPalette.resolve("legacy_7", "#004CAF50", null).light)
    }

    @Test
    fun `soft is an opaque tint of the colour over the background`() {
        val s = CategoryPalette.soft(Color(0xFFD98A00), Color.White)
        assertEquals(1f, s.alpha)
        assertTrue(Contrast.luminance(s) > Contrast.luminance(Color(0xFFD98A00)))
    }

    @Test
    fun `pick chooses by theme`() {
        val c = CategoryColor(Color.Red, Color.Blue)
        assertEquals(Color.Red, c.pick(dark = false))
        assertEquals(Color.Blue, c.pick(dark = true))
    }
}
