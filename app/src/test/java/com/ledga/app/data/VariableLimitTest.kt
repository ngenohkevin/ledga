package com.ledga.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.legacy.LegacyImporter
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.SmsSource
import com.ledga.app.testing.BindGuard
import com.ledga.app.testing.LegacyDbWriter
import com.ledga.app.testing.SchemaFixture
import com.ledga.app.testing.Sms
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Review Focus #2: a 1,200-code batch must never bind more than API 26's 999 variables in one statement. */
@RunWith(RobolectricTestRunner::class)
class VariableLimitTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val at = Instant.parse("2026-03-21T10:30:05Z")
    private val codes = (1..1_200).map { "TJK" + it.toString().padStart(7, '0') }

    @Test
    fun `ingest, rederive, rebuild and reclassify stay under 999 bound variables`() = runTest {
        val guard = BindGuard()
        val db = guard.attach(LedgaDatabase.inMemory(context)).allowMainThreadQueries().build()
        val deriver = Deriver(db)
        SmsIngestor(db, deriver).ingestAll(codes.map { RawSms("MPESA", Sms.send(it), at, null, null, SmsSource.INBOX) })
        deriver.rederive(codes)
        deriver.rebuildAll()
        deriver.reclassifyAll()
        assertEquals(1_200, db.transactionsDao().count())
        assertTrue(guard.max <= BindGuard.API26_LIMIT, "${guard.max} variables in: ${guard.maxSql}")
        db.close()
    }

    @Test
    fun `the legacy import stays under 999 bound variables`() = runTest {
        SchemaFixture.create(context, "big.db", 5).use { helper ->
            val w = LegacyDbWriter(helper.writableDatabase, 5)
            w.defaultCategories(); w.defaultRules()
            codes.forEachIndexed { i, code -> w.tx(i + 1L, code, "SEND", "OUTFLOW", Sms.send(code), at.plusSeconds(i.toLong()), 5, recipientName = "JANE TESTER") }
        }
        val guard = BindGuard()
        val db = guard.attach(LedgaDatabase.builder(context, "big.db")).build()
        LegacyImporter(db).run()
        Deriver(db).rebuildAll()
        assertEquals(1_200, db.transactionsDao().count())
        assertTrue(guard.max <= BindGuard.API26_LIMIT, "${guard.max} variables in: ${guard.maxSql}")
        db.close()
    }
}
