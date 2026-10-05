package com.ledga.app.data.legacy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.SmsStatus
import com.ledga.app.data.room.toDerived
import com.ledga.app.testing.LegacyDbWriter
import com.ledga.app.testing.SchemaFixture
import com.ledga.core.derive.BalanceChain
import com.ledga.core.model.TxKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * OPT-IN, LOCAL ONLY. Migrates the owner's real v1 export through MIGRATION_5_6 + LegacyImporter + rebuildAll and
 * prints counts. Skipped when the export is absent. Never prints message text, names, numbers or amounts.
 */
@RunWith(RobolectricTestRunner::class)
class LegacyMigrationAuditTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun corpus(): File? =
        (System.getenv("LEDGA_CORPUS")?.let(::File) ?: File(System.getProperty("user.home"), "Documents/ledga-export/data.json"))
            .takeIf { it.isFile }

    /** Loads the export; any failure is rethrown without content (a cause would print real data). */
    private fun rows(file: File): List<JsonObject> = try {
        Json.parseToJsonElement(file.readText()).jsonObject["transactions"]!!.jsonArray.map { it.jsonObject }
    } catch (e: Exception) {
        throw IllegalStateException("could not read the export (${e.javaClass.simpleName})")
    }

    @Test
    fun `the real v1 history migrates`() = runTest {
        val file = corpus()
        assumeTrue("no local export; audit skipped", file != null)
        val export = rows(file!!)

        SchemaFixture.create(context, "audit.db", 5).use { helper ->
            val w = LegacyDbWriter(helper.writableDatabase, 5)
            helper.writableDatabase.beginTransaction()
            try {
                w.defaultCategories(); w.defaultRules()
                export.forEachIndexed { i, o ->
                    fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull
                    w.tx(
                        id = i + 1L, code = s("transactionCode")!!, type = s("type")!!, direction = s("direction")!!, rawSms = s("rawSms")!!,
                        timestamp = Instant.ofEpochMilli(o["timestamp"]!!.jsonPrimitive.longOrNull!!), categoryId = o["categoryId"]?.jsonPrimitive?.longOrNull,
                        recipientName = s("recipientName"), accountNumber = s("accountNumber"),
                    )
                }
                helper.writableDatabase.setTransactionSuccessful()
            } finally {
                helper.writableDatabase.endTransaction()
            }
        }

        val db = LedgaDatabase.builder(context, "audit.db").build()
        val report = LegacyImporter(db).run()
        val deriver = Deriver(db)
        deriver.rebuildAll()

        val sms = db.smsDao()
        val txs = db.transactionsDao().all()
        val chain = BalanceChain.check(txs.map { it.toDerived() })
        println("== legacy migration audit (counts only) ==")
        println("v1 rows: ${export.size}  sms rows: ${sms.countByStatus(SmsStatus.PARSED) + sms.countByStatus(SmsStatus.IGNORED) + sms.countByStatus(SmsStatus.UNREADABLE)}")
        println("sms PARSED ${sms.countByStatus(SmsStatus.PARSED)} / IGNORED ${sms.countByStatus(SmsStatus.IGNORED)} / UNREADABLE ${sms.countByStatus(SmsStatus.UNREADABLE)}")
        println("importer: $report")
        println("transactions: ${txs.size}  FULIZA_ONLY orphans: ${txs.count { it.kind == TxKind.FULIZA_ONLY }}")
        println("history check: checked ${chain.checked}, gaps ${chain.gaps}, breaks ${chain.breaks.size}")

        assertEquals(export.size, report.smsRehashed + report.smsDuplicatesDropped)
        assertFalse(LegacyImporter(db).isPending())
        assertFalse(deriver.needsRebuild())
        db.close()
    }
}
