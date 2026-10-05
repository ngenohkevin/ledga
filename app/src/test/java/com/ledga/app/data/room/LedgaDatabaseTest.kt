package com.ledga.app.data.room

import com.ledga.app.testing.SchemaFixture
import com.ledga.app.testing.TestDb
import com.ledga.core.derive.Derivation
import com.ledga.core.derive.RuleEngine
import com.ledga.core.derive.RuleOrigin
import com.ledga.core.model.Categories
import com.ledga.core.parse.MpesaParser
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class LedgaDatabaseTest {
    @Test
    fun `a fresh database is seeded with categories, system rules and current versions`() = runTest {
        val db = TestDb.inMemory()
        val categories = db.categoriesDao().all()
        assertEquals(Categories.SEED.map { it.key }, categories.map { it.key })
        categories.forEach { c ->
            val seed = Categories.seed(c.key)!!
            assertEquals(seed.group, c.groupKey)
            assertEquals(seed.tracked, c.tracked)
            assertEquals(CategoryOrigin.SYSTEM, c.origin)
        }
        val rules = db.rulesDao().all()
        assertEquals(RuleEngine.systemRules(Instant.EPOCH).map { it.pattern to it.categoryKey }, rules.map { it.pattern to it.categoryKey })
        assertTrue(rules.all { it.origin == RuleOrigin.SYSTEM && it.enabled })
        assertEquals(MpesaParser.VERSION.toString(), db.metaDao().get(MetaKeys.PARSER_VERSION))
        assertEquals(Derivation.VERSION.toString(), db.metaDao().get(MetaKeys.DERIVATION_VERSION))
        db.close()
    }

    @Test
    fun `schema 6 is exported and its view is exactly LedgerView QUERY`() {
        val db = SchemaFixture.json(6)
        assertEquals("6", db["version"]!!.jsonPrimitive.content)
        val view = db["views"]!!.jsonArray.single().jsonObject
        assertEquals("ledger", view["viewName"]!!.jsonPrimitive.content)
        assertEquals("CREATE VIEW `${SchemaFixture.VIEW}` AS ${LedgerView.QUERY}", view["createSql"]!!.jsonPrimitive.content)
    }
}
