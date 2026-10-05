package com.ledga.core.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CounterpartyTest {

    @Test
    fun `names are uppercased, collapsed, and stripped of APP suffix and trailing punctuation`() {
        assertEquals("EXAMPLE BANK LIMITED", Names.normalize("EXAMPLE BANK LIMITED- APP"))
        assertEquals("EXAMPLE BANK LIMITED", Names.normalize("Example Bank Limited - APP"))
        assertEquals("Q7 KIOSK", Names.normalize("Q7 KIOSK."))
        assertEquals("SAMPLE STORES", Names.normalize("Sample  Stores.."))
        assertEquals("CAFE ON THE GO", Names.normalize(" Cafe on the Go "))
        assertEquals("SAMPLE ENERGY 2.0", Names.normalize("Sample Energy 2.0"))
        assertNull(Names.normalize("  "))
        assertNull(Names.normalize(null))
        assertNull(Names.normalize(" - APP"))
    }

    @Test
    fun `phone fragments match full, international and masked Kenyan numbers`() {
        val full = Regex("^(?:${Phones.FULL})$")
        val masked = Regex("^(?:${Phones.MASKED})$")
        listOf("0712345111", "0112345111", "254712345111", "+254712345111").forEach { assertTrue(full.matches(it), it) }
        listOf("0712***111", "0712**111", "254712***111").forEach { assertTrue(masked.matches(it), it) }
        listOf("071234511", "0812345111", "600100", "+447700900123").forEach { assertFalse(full.matches(it), it) }
    }

    @Test
    fun `phone key is stable across full, international and masked forms`() {
        assertEquals("0712111", Phones.key("0712345111"))
        assertEquals("0712111", Phones.key("0712***111"))
        assertEquals("0712111", Phones.key("+254712345111"))
        assertEquals("0712111", Phones.key("254712***111"))
    }

    @Test
    fun `counterparty key joins name and phone key`() {
        assertEquals("JANE TESTER|0712111", CounterpartyKey.of("JANE TESTER", "0712345111"))
        assertEquals("JANE TESTER|0712111", Counterparty("JANE TESTER", "0712***111", null, null).key)
        assertEquals("SAMPLE STORES", CounterpartyKey.of("SAMPLE STORES", null))
        assertEquals("|0712111", CounterpartyKey.of(null, "0712345111"))
        assertNull(CounterpartyKey.of(null, null))
    }

    @Test
    fun `account refs keep case but collapse whitespace`() {
        assertEquals("Cloud Hosting online +16505550100 US", normalizeAccount("  Cloud Hosting  online +16505550100 US "))
        assertNull(normalizeAccount(""))
        assertNull(normalizeAccount(null))
    }
}
