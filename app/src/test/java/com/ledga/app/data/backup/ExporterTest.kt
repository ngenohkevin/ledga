package com.ledga.app.data.backup

import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.twoLines
import com.ledga.app.testing.txRow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec §12.2, R124: a `.ledga` file is a zip of the manifest, the backup and the spreadsheet. Synthetic payments. */
@RunWith(RobolectricTestRunner::class)
class ExporterTest {
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-10-07T17:00:00Z"))
    private val reader = BackupReader(db, SettingsStore(FakePrefsStore()), DeviceId { "fingerprint-a" }, "2.0.0-test", clock)
    private val exporter = Exporter(reader, db)

    @After fun close() = db.close()

    private suspend fun entries(): Map<String, ByteArray> {
        val out = ByteArrayOutputStream()
        exporter.write(out)
        val found = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                found[e.name] = zip.readBytes()
            }
        }
        return found
    }

    @Test
    fun `the file holds the manifest, the backup and the spreadsheet`() = runTest {
        twoLines(db)
        db.transactionsDao().upsertAll(listOf(txRow(), txRow(code = "TJK4AB12FZ", hidden = true)))
        val files = entries()
        assertEquals(listOf("manifest.json", "data.json", "transactions.csv"), files.keys.toList())
        val manifest = BackupJson.json.decodeFromString(ExportManifest.serializer(), files.getValue("manifest.json").decodeToString())
        assertEquals(ExportManifest(1, "2.0.0-test", clock.millis(), BackupCounts(sms = 0, payments = 1)), manifest)
        val data = BackupJson.json.decodeFromString(BackupData.serializer(), files.getValue("data.json").decodeToString())
        assertEquals(reader.read(), data)
    }

    @Test
    fun `the spreadsheet is UTF-8 with a byte-order mark, one row per payment that shows`() = runTest {
        twoLines(db)
        db.transactionsDao().upsertAll(listOf(txRow(), txRow(code = "TJK4AB12FZ", hidden = true)))
        val csv = entries().getValue("transactions.csv").decodeToString()
        assertEquals(CsvExport.BOM, csv.first())
        val lines = csv.drop(1).split("\r\n").filter { it.isNotEmpty() }
        assertEquals(2, lines.size, "the header and the one payment that shows")
        assertTrue(lines[1].startsWith("2026-10-05,14:15,TJK4AB12FA,Paybill,Spending,Electricity,KPLC PREPAID,"))
        assertTrue(lines[1].endsWith(",Personal,"))
        assertFalse(csv.contains("TJK4AB12FZ"), "a hidden payment stays out")
    }

    @Test
    fun `there is no row limit`() = runTest {
        // v1 stopped at 10,000 (spec §4 defect 4).
        db.transactionsDao().upsertAll(List(10_001) { i -> txRow(code = "TJK5%05d".format(i), lineId = null) })
        val csv = entries().getValue("transactions.csv").decodeToString()
        assertEquals(10_002, csv.split("\r\n").count { it.isNotEmpty() })
    }

    @Test
    fun `the file is named for the day`() {
        assertEquals("ledga-2026-10-07.ledga", Exporter.fileName(LocalDate.parse("2026-10-07")))
    }
}
