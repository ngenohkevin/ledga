package com.ledga.app.ui.design.format

import org.junit.Test
import kotlin.test.assertEquals

class AmountFormatTest {

    @Test
    fun `outflows use the true minus sign`() {
        val s = AmountFormat.signed(120_000, inflow = false)
        assertEquals(0x2212, s[0].code)
        assertEquals("1,200.00", s.drop(1))
    }

    @Test
    fun `inflows are plus-signed and zero is unsigned`() {
        assertEquals("+5,000.00", AmountFormat.signed(500_000, inflow = true))
        assertEquals("0.00", AmountFormat.signed(0, inflow = false))
        assertEquals("0.00", AmountFormat.signed(0, inflow = true))
    }

    @Test
    fun `plain drops the sign and shows cents only when present`() {
        assertEquals("1,200", AmountFormat.plain(120_000))
        assertEquals("1,200.50", AmountFormat.plain(-120_050))
    }

    @Test
    fun `seven-figure amounts group correctly`() {
        assertEquals("+1,234,567.89", AmountFormat.signed(123_456_789, inflow = true))
    }

    @Test
    fun `compact chart labels round half-up to one significant step`() {
        mapOf(
            0L to "0", 95_000L to "950", 99_950L to "1k", 100_000L to "1k", 245_000L to "2.5k",
            994_900L to "9.9k", 995_000L to "10k", 1_234_500L to "12k", 99_950_000L to "1M",
            123_456_700L to "1.2M", 995_000_000L to "10M",
        ).forEach { (cents, label) -> assertEquals(label, AmountFormat.compact(cents), "$cents") }
        assertEquals("${AmountFormat.MINUS}2.5k", AmountFormat.compact(-245_000))
    }
}
