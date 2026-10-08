package com.ledga.app.data.legacy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.room.TxRow
import com.ledga.app.testing.LegacyDbWriter
import com.ledga.app.testing.SchemaFixture
import com.ledga.app.testing.Sms
import com.ledga.core.derive.RuleAction
import com.ledga.core.derive.RuleField
import com.ledga.core.derive.RuleOrigin
import com.ledga.core.model.Categories
import com.ledga.core.model.CategoryGroup
import com.ledga.core.model.FlowKind
import com.ledga.core.model.TxKind
import com.ledga.core.parse.SmsText
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class LegacyImporterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val at = Instant.parse("2026-03-21T10:30:05Z")
    private var db: LedgaDatabase? = null

    @After fun close() { db?.close() }

    private val zuku = Sms.paybill("TJK4AB12GA", "ZUKU", "4400123", "2,999.00")
    private val landlord = Sms.paybill("TJK4AB12GD", "SAMPLE LANDLORD", "A12", "15,000.00")
    private val sacco = Sms.paybill("TJK4AB12GB", "SAMPLE SACCO", "7788", "3,000.00")
    private val client = Sms.receive("TJK4AB12GC", "SAMPLE CLIENT 0722333444", "4,000.00")
    private val fuel = Sms.buyGoods("TJK4AB12GE", "SAMPLE ENERGY", "2,000.00")

    /** A realistic v1.6 (schema 5) install, migrated to 6. Synthetic data only. */
    private fun migratedV5(): LedgaDatabase {
        SchemaFixture.create(context, "imp.db", 5).use { helper ->
            LegacyDbWriter(helper.writableDatabase, 5).apply {
                defaultCategories(); category(15, "Side hustle", isDefault = false); defaultRules()
                rule(100, 5, "RECIPIENT_NAME", "JANE TESTER")      // user rule -> Food
                rule(101, 14, "RECIPIENT_NAME", "EXAMPLE BANK")    // transfer rule -> own account
                rule(102, 10, "TILL", "123456")                    // TILL: no v2 equivalent, skipped
                account(1, 3, "Line A")
                tx(1, "TJK4AB12FB", "SEND", "OUTFLOW", Sms.SEND, at, 5, recipientName = "JANE TESTER", accountId = 1)
                tx(2, "TJK4AB12FA", "SEND", "OUTFLOW", Sms.KPLC, at.plusSeconds(60), 3, recipientName = "KPLC PREPAID", accountNumber = "37100000001")
                tx(3, "TJK4AB12GA", "SEND", "OUTFLOW", zuku, at.plusSeconds(120), 3, recipientName = "ZUKU", accountNumber = "4400123")
                tx(4, "TJK4AB12GD", "SEND", "OUTFLOW", landlord, at.plusSeconds(180), 3, recipientName = "SAMPLE LANDLORD", accountNumber = "A12")
                tx(5, "TJK4AB12GB", "SEND", "OUTFLOW", sacco, at.plusSeconds(240), 14, recipientName = "SAMPLE SACCO", accountNumber = "7788")
                tx(6, "TJK4AB12FC", "RECEIVED", "INFLOW", Sms.BANK_APP, at.plusSeconds(300), 14, recipientName = "EXAMPLE BANK LIMITED")
                tx(7, "TJK4AB12GC", "RECEIVED", "INFLOW", client, at.plusSeconds(360), 15, recipientName = "SAMPLE CLIENT")
                tx(8, "TJK4AB12GE", "BUY_GOODS", "OUTFLOW", fuel, at.plusSeconds(420), 13, recipientName = "SAMPLE ENERGY", carTag = "FUEL")
                tx(9, "TJK4AB12EA", "FULIZA", "OUTFLOW", Sms.COMPANION, at.plusSeconds(480), 12, note = "  ")
                tx(10, "TJK4AB12FE", "REVERSAL", "INFLOW", Sms.REVERSAL, at.plusSeconds(540), 13, note = "refund for the lunch")
            }
        }
        return LedgaDatabase.builder(context, "imp.db").build().also { db = it }
    }

    private suspend fun LedgaDatabase.tx(code: String): TxRow = transactionsDao().get(code)!!

    @Test
    fun `v1 intent survives the upgrade`() = runTest {
        val db = migratedV5()
        val importer = LegacyImporter(db)
        assertTrue(importer.isPending())
        val report = importer.run()
        Deriver(db).rebuildAll()

        assertFalse(importer.isPending())
        assertEquals(LegacyImportReport(10, 0, 1, 2, 1, 3, 1, 1, 1), report)
        assertTrue(db.smsDao().pageAfter(0, 50).all { it.bodyHash == SmsText.hash(it.body) })

        assertEquals(Categories.FOOD, db.tx("TJK4AB12FB").categoryKey, "v1 user rule became a USER rule")
        assertEquals(1L, db.tx("TJK4AB12FB").lineId)
        assertEquals(Categories.ELECTRICITY, db.tx("TJK4AB12FA").categoryKey, "auto Bills -> no override -> v2 rule")
        assertEquals(Categories.INTERNET, db.tx("TJK4AB12GA").categoryKey, "R6: a Bills choice yields to the Internet rule")
        assertEquals(Categories.OTHER, db.tx("TJK4AB12GD").categoryKey, "a Bills choice with no Bills rule -> Other")
        assertEquals(FlowKind.OWN_OUT, db.tx("TJK4AB12GB").flow, "My Accounts choice -> own account override")
        assertEquals(FlowKind.OWN_IN, db.tx("TJK4AB12FC").flow, "transfer rule -> MARK_OWN_ACCOUNT rule")
        assertEquals("legacy_15", db.tx("TJK4AB12GC").categoryKey)
        assertEquals(FlowKind.INCOME, db.tx("TJK4AB12GC").flow)
        assertEquals(Categories.FUEL, db.tx("TJK4AB12GE").categoryKey, "carTag FUEL")
        assertEquals(TxKind.FULIZA_ONLY, db.tx("TJK4AB12EA").kind)
        assertNull(db.tx("TJK4AB12EA").note, "blank notes are dropped")
        assertEquals("refund for the lunch", db.tx("TJK4AB12FE").note)
        assertTrue(db.tx("TJK4AB12FB").isReversed)

        val side = db.categoriesDao().all().single { it.key == "legacy_15" }
        assertEquals(CategoryGroup.MONEY_IN, side.groupKey)
        assertEquals(CategoryOrigin.USER, side.origin)
        assertEquals("Side hustle", side.name)
        val user = db.rulesDao().all().filter { it.origin == RuleOrigin.USER }
        assertEquals(
            setOf(Triple(RuleField.NAME_CONTAINS, "JANE TESTER", RuleAction.SET_CATEGORY), Triple(RuleField.NAME_CONTAINS, "EXAMPLE BANK", RuleAction.MARK_OWN_ACCOUNT)),
            user.map { Triple(it.field, it.pattern, it.action) }.toSet(),
        )
    }

    @Test
    fun `a v1 user rule into Bills yields to v2's Bills rules (R6 for rules)`() = runTest {
        val water = Sms.paybill("TJK4AB12GF", "SAMPLE CITY WATER AND SEWERAGE", "W77", "1,500.00")
        SchemaFixture.create(context, "bills.db", 5).use { helper ->
            LegacyDbWriter(helper.writableDatabase, 5).apply {
                defaultCategories(); defaultRules()
                rule(100, 3, "RECIPIENT_NAME", "SAMPLE CITY WATER") // v1 user rule -> Bills & Utilities
                tx(1, "TJK4AB12GF", "SEND", "OUTFLOW", water, at, 3, recipientName = "SAMPLE CITY WATER AND SEWERAGE", accountNumber = "W77")
            }
        }
        val db = LedgaDatabase.builder(context, "bills.db").build().also { db = it }
        val report = LegacyImporter(db).run()
        Deriver(db).rebuildAll()
        assertEquals(Categories.WATER, db.tx("TJK4AB12GF").categoryKey, "v2's WATER AND SEWERAGE rule, not an imported Other rule")
        assertTrue(db.rulesDao().all().none { it.origin == RuleOrigin.USER })
        assertEquals(0, report.userRules)
        assertEquals(1, report.skippedRules)
    }

    @Test
    fun `a payment re-ingested after the import merges with its orphan companion`() = runTest {
        val db = migratedV5()
        LegacyImporter(db).run()
        val deriver = Deriver(db)
        deriver.rebuildAll()
        SmsIngestor(db, deriver).ingest(RawSms("MPESA", Sms.PURCHASE, at.plusSeconds(470), null, null, SmsSource.INBOX))
        assertEquals(TxKind.BUY_GOODS, db.tx("TJK4AB12EA").kind)
        assertEquals(2, db.tx("TJK4AB12EA").smsCount)
    }

    @Test
    fun `an SMS that arrived before the import keeps one copy`() = runTest {
        val db = migratedV5()
        SmsIngestor(db, Deriver(db)).ingest(RawSms("MPESA", Sms.KPLC, at.plusSeconds(60), null, null, SmsSource.RECEIVER))
        val report = LegacyImporter(db).run()
        assertEquals(1, report.smsDuplicatesDropped)
        assertEquals(1, db.smsDao().pageAfter(0, 50).count { it.code == "TJK4AB12FA" })
    }

    @Test
    fun `a failed import changes nothing and can run again`() = runTest {
        val db = migratedV5()
        assertFailsWith<IllegalStateException> { LegacyImporter(db, checkpoint = { if (it == "rules") error("killed") }).run() }
        assertTrue(LegacyImporter(db).isPending())
        assertTrue(db.rulesDao().all().none { it.origin == RuleOrigin.USER })
        assertTrue(db.smsDao().pageAfter(0, 50).all { it.bodyHash.startsWith("legacy:") })
        LegacyImporter(db).run()
        assertFalse(LegacyImporter(db).isPending())
    }

    @Test
    fun `a never-upgraded schema 1 install imports too`() = runTest {
        SchemaFixture.create(context, "imp1.db", 1).use { helper ->
            LegacyDbWriter(helper.writableDatabase, 1).apply {
                defaultCategories(); defaultRules()
                tx(1, "TJK4AB12FB", "SEND", "OUTFLOW", Sms.SEND, at, 5, recipientName = "JANE TESTER") // Food chosen by hand
            }
        }
        val db = LedgaDatabase.builder(context, "imp1.db").build().also { db = it }
        LegacyImporter(db).run()
        Deriver(db).rebuildAll()
        assertEquals(Categories.FOOD, db.tx("TJK4AB12FB").categoryKey)
        assertEquals(Categories.FOOD, db.overridesDao().get("TJK4AB12FB")!!.categoryKey)
    }

    @Test
    fun `imported v1 rules keep v1's order - names before paybills, the lowest id first (R156)`() = runTest {
        val shop = Sms.paybill("TJK4AB12GG", "SAMPLE SHOP CENTRE", "7788", "2,500.00")
        SchemaFixture.create(context, "order.db", 5).use { helper ->
            LegacyDbWriter(helper.writableDatabase, 5).apply {
                defaultCategories(); defaultRules()
                rule(100, 2, "PAYBILL", "7788")                     // v1 tried paybill rules after every name rule
                rule(101, 5, "RECIPIENT_NAME", "SAMPLE SHOP")       // v1's pick: the first name rule that matches
                rule(102, 10, "RECIPIENT_NAME", "SAMPLE SHOP CENTRE")
                tx(1, "TJK4AB12GG", "SEND", "OUTFLOW", shop, at, 5, recipientName = "SAMPLE SHOP CENTRE", accountNumber = "7788")
            }
        }
        val db = LedgaDatabase.builder(context, "order.db").build().also { db = it }
        LegacyImporter(db).run()
        Deriver(db).rebuildAll()
        assertEquals(Categories.FOOD, db.tx("TJK4AB12GG").categoryKey, "the rule v1 filed it by, not the newest")
        assertEquals(3, db.rulesDao().all().count { it.origin == RuleOrigin.USER })
    }

    @Test
    fun `a v1 transfer rule in v1's paybill form still marks those payments as own-account (R173)`() = runTest {
        val sacco = Sms.paybill("TJK4AB12GH", "SAMPLE SACCO", "7788", "2,500.00", "15/9/26 at 9:00 AM")
        SchemaFixture.create(context, "paybillrule.db", 5).use { helper ->
            LegacyDbWriter(helper.writableDatabase, 5).apply {
                defaultCategories(); defaultRules()
                rule(100, 14, "RECIPIENT_NAME", "SAMPLE SACCO for account") // v1's reading of the payee
                tx(1, "TJK4AB12GH", "SEND", "OUTFLOW", sacco, at, 14, recipientName = "SAMPLE SACCO for account")
            }
        }
        val db = LedgaDatabase.builder(context, "paybillrule.db").build().also { db = it }
        LegacyImporter(db).run()
        Deriver(db).rebuildAll()
        assertEquals(FlowKind.OWN_OUT, db.tx("TJK4AB12GH").flow, "a transfer, not spending")
        val rule = db.rulesDao().all().single { it.origin == RuleOrigin.USER }
        assertEquals(RuleField.NAME_CONTAINS to "SAMPLE SACCO", rule.field to rule.pattern)
    }

    @Test
    fun `a payment v1 filed under My Accounts stays own-account even where v2's rules don't match it (R174)`() = runTest {
        val bank = Sms.receive("TJK4AB12GJ", "SAMPLE BANK LIMITED", "2,500.00", "16/9/26 at 9:00 AM")
        SchemaFixture.create(context, "safeguard.db", 5).use { helper ->
            LegacyDbWriter(helper.writableDatabase, 5).apply {
                defaultCategories(); defaultRules()
                rule(100, 14, "RECIPIENT_NAME", "SAMPLE BAN") // v1 matched part of a word; v2 matches whole words
                tx(1, "TJK4AB12GJ", "RECEIVED", "INFLOW", bank, at, 14, recipientName = "SAMPLE BANK LIMITED")
            }
        }
        val db = LedgaDatabase.builder(context, "safeguard.db").build().also { db = it }
        val report = LegacyImporter(db).run()
        Deriver(db).rebuildAll()
        assertEquals(FlowKind.OWN_IN, db.tx("TJK4AB12GJ").flow)
        assertEquals(1, report.ownAccountOverrides)
    }
}
