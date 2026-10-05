package com.ledga.core.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoneyTest {

    @Test
    fun `parses grouped, plain, one-decimal and whole amounts exactly`() {
        assertEquals(120_000L, Money.parse("1,200.00")!!.cents)
        assertEquals(736_055L, Money.parse("7360.55")!!.cents)
        assertEquals(46_300L, Money.parse("463")!!.cents)
        assertEquals(46_350L, Money.parse("463.5")!!.cents)
        assertEquals(100_000_000L, Money.parse("1,000,000.00")!!.cents)
        assertEquals(0L, Money.parse("0.00")!!.cents)
        assertEquals(42L, Money.parse("0.42")!!.cents)
        assertEquals(120_000L, Money.parse("  1,200.00 ")!!.cents)
    }

    @Test
    fun `rejects malformed amounts instead of guessing`() {
        listOf("", "abc", "1,20.00", "12,3456.00", "1.234", "-5.00", "1,200.", "500,", "Ksh500.00", "1 200")
            .forEach { assertNull(Money.parse(it), "should reject '$it'") }
    }

    @Test
    fun `formats with grouping and decimals modes`() {
        val m = Money.parse("1234567.50")!!
        assertEquals("1,234,567.50", m.amountText())
        assertEquals("1,234,567.50", m.amountText(Decimals.ALWAYS))
        assertEquals("1,234,568", m.amountText(Decimals.NEVER))
        assertEquals("1,200", Money.parse("1200")!!.amountText())
        assertEquals("1,200.00", Money.parse("1200")!!.amountText(Decimals.ALWAYS))
        assertEquals("1,200", Money.parse("1200.49")!!.amountText(Decimals.NEVER))
        assertEquals("0", Money.ZERO.amountText())
        assertEquals("-75.00", Money.parse("75")!!.let { -it }.amountText(Decimals.ALWAYS))
        assertEquals("Ksh 6,418.36", Money.parse("6418.36")!!.kshText())
    }

    @Test
    fun `search forms cover the ways people type an amount`() {
        assertEquals(listOf("1200", "1,200", "1200.00", "1,200.00"), Money.parse("1200")!!.searchForms())
        assertEquals(listOf("1200.50", "1,200.50"), Money.parse("1200.5")!!.searchForms())
        assertEquals(listOf("500", "500.00"), Money.parse("500")!!.searchForms())
    }

    @Test
    fun `arithmetic is exact and overflow-checked`() {
        val a = Money.ofShillings(2_500)
        val b = Money.ofShillings(463, 25)
        assertEquals(203_675L, (a - b).cents)
        assertEquals(296_325L, (a + b).cents)
        assertTrue(b < a)
        assertTrue((b - a).isNegative)
        assertFailsWith<ArithmeticException> { Money(Long.MAX_VALUE) + Money(1) }
    }

    @Test
    fun `Long MIN_VALUE cannot be formatted`() {
        assertFailsWith<ArithmeticException> { Money(Long.MIN_VALUE).amountText() }
        assertFailsWith<ArithmeticException> { Money(Long.MIN_VALUE).searchForms() }
    }
}
