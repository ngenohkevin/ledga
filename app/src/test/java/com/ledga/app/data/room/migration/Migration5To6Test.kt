package com.ledga.app.data.room.migration

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.MetaKeys
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.room.SmsStatus
import com.ledga.app.testing.LegacyDbWriter
import com.ledga.app.testing.SchemaFixture
import com.ledga.app.testing.Sms
import com.ledga.core.derive.LegacyAutoCategorizer
import com.ledga.core.model.Categories
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class Migration5To6Test {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val at = Instant.parse("2026-03-21T10:30:05Z")

    private fun LedgaDatabase.count(sql: String): Int =
        openHelper.readableDatabase.query(SimpleSQLiteQuery(sql)).use { it.moveToFirst(); it.getInt(0) }

    private fun LedgaDatabase.tables(): Set<String> =
        openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type IN ('table', 'view')").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }

    @Test
    fun `schema 5 becomes 6 with v1 data staged, lines and sms copied, seeds in place and a rebuild pending`() = runTest {
        SchemaFixture.create(context, "m56.db", 5).use { helper ->
            LegacyDbWriter(helper.writableDatabase, 5).apply {
                defaultCategories(); category(15, "Side hustle", isDefault = false); defaultRules()
                rule(100, 5, "RECIPIENT_NAME", "JANE TESTER")
                account(1, 3, "Line A"); account(2, 7, "Line B")
                goalAndContribution(); insight(); budget()
                tx(1, "TJK4AB12FB", "SEND", "OUTFLOW", Sms.SEND, at, 5, recipientName = "JANE TESTER", accountId = 1, note = "lunch")
                tx(2, "TJK4AB12FA", "SEND", "OUTFLOW", Sms.KPLC, at.plusSeconds(60), 3, recipientName = "KPLC PREPAID", accountNumber = "37100000001", accountId = 2, carTag = null)
            }
        }
        val db = LedgaDatabase.builder(context, "m56.db").build()
        val sms = db.smsDao().pageAfter(0, 10)
        assertEquals(listOf("legacy:TJK4AB12FB", "legacy:TJK4AB12FA"), sms.map { it.bodyHash })
        assertTrue(sms.all { it.source == SmsSource.LEGACY && it.status == SmsStatus.PARSED && it.parserVersion == 0 && it.sender == "MPESA" })
        assertEquals(listOf(1L, 2L), sms.map { it.lineId })
        assertEquals(2, db.count("SELECT COUNT(*) FROM lines"))
        assertEquals(Categories.SEED.size, db.categoriesDao().all().size)
        assertEquals("0", db.metaDao().get(MetaKeys.DERIVATION_VERSION))
        assertTrue(Deriver(db).needsRebuild())
        assertEquals(2, db.count("SELECT COUNT(*) FROM legacy_tx"))
        assertEquals("lunch", db.openHelper.readableDatabase.query("SELECT note FROM legacy_tx WHERE code = 'TJK4AB12FB'").use { it.moveToFirst(); it.getString(0) })
        assertEquals(LegacyAutoCategorizer.DEFAULT_RULES.size + 1, db.count("SELECT COUNT(*) FROM legacy_rules"))
        assertEquals(15, db.count("SELECT COUNT(*) FROM legacy_categories"))
        val tables = db.tables()
        listOf("goals", "goal_contributions", "insights", "budgets", "category_rules", "mpesa_accounts", "legacy_sms", "legacy_lines")
            .forEach { assertTrue(it !in tables, "$it should be gone") }
        assertTrue("ledger" in tables)
        db.close()
    }

    @Test
    fun `schemas 1, 3 and 4 reach 6 too`() = runTest {
        for (start in listOf(1, 3, 4)) {
            val name = "from$start.db"
            SchemaFixture.create(context, name, start).use { helper ->
                LegacyDbWriter(helper.writableDatabase, start).apply {
                    defaultCategories(); defaultRules()
                    if (start >= 3) { account(1, 3, "Line A"); insight() }
                    tx(1, "TJK4AB12FB", "SEND", "OUTFLOW", Sms.SEND, at, 6, recipientName = "JANE TESTER", accountId = if (start >= 3) 1 else null)
                }
            }
            val db = LedgaDatabase.builder(context, name).build()
            assertEquals(1, db.smsDao().pageAfter(0, 10).size, "from $start")
            assertEquals(if (start >= 3) 1 else 0, db.count("SELECT COUNT(*) FROM lines"), "from $start")
            assertEquals(1, db.count("SELECT COUNT(*) FROM legacy_tx"), "from $start")
            db.close()
        }
    }
}
