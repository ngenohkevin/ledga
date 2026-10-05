package com.ledga.app.data.room.migration

import com.ledga.app.testing.SchemaFixture
import org.junit.Test
import kotlin.test.assertEquals

class V6SchemaTest {
    @Test
    fun `MIGRATION_5_6 creates exactly the schema Room exported as 6`() {
        val expected = SchemaFixture.statements(6).filterNot { it.contains("room_master_table") }.toSet()
        // If this fails, 6.json is the source of truth: copy the mismatched statement into V6Schema verbatim.
        assertEquals(expected, V6Schema.CREATE.toSet())
    }
}
