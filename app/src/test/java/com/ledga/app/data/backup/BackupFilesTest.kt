package com.ledga.app.data.backup

import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.twoLines
import com.ledga.app.testing.txRow
import java.io.File
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec §12.3: a restore reads a `.ledga` file, a snapshot, or a v1 export zip; anything else says what's wrong. */
@RunWith(RobolectricTestRunner::class)
class BackupFilesTest {
    @get:Rule val tmp = TemporaryFolder()
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-10-07T17:00:00Z"))
    private val reader = BackupReader(db, SettingsStore(FakePrefsStore()), DeviceId { "fingerprint-a" }, "2.0.0-test", clock)

    @After fun close() = db.close()

    private fun zip(name: String, vararg entries: Pair<String, String>): File = tmp.newFile(name).also { f ->
        ZipOutputStream(f.outputStream()).use { z ->
            entries.forEach { (n, text) ->
                z.putNextEntry(ZipEntry(n))
                z.write(text.toByteArray())
                z.closeEntry()
            }
        }
    }

    /** A synthetic v1 export, shaped like v1.6's `ExportData` (with fields v2 doesn't read). */
    private fun v1Zip(): File = zip(
        "ledga-v1.zip",
        "transactions.csv" to "Date,Time,Code\n",
        "data.json" to """
            {"version":1,"exportedAt":1790000000000,"transactions":[
              {"transactionCode":"TJK4AB12FB","type":"SEND","amount":500.0,"transactionCost":7.0,"recipientName":"JANE TESTER",
               "recipientPhone":"0712345111","accountNumber":null,"destinationCountry":null,"balance":1200.0,"direction":"OUTFLOW",
               "categoryId":1,"fulizaAmount":null,"fulizaOutstanding":null,"reversedTransactionCode":null,
               "rawSms":"${Sms.SEND}","timestamp":1774089000000},
              {"transactionCode":"TJK4AB12FA","type":"PAYBILL","amount":1000.0,"transactionCost":0.0,"recipientName":"KPLC PREPAID",
               "recipientPhone":null,"accountNumber":"37100000001","destinationCountry":null,"balance":2000.0,"direction":"OUTFLOW",
               "categoryId":null,"fulizaAmount":null,"fulizaOutstanding":null,"reversedTransactionCode":null,
               "rawSms":"${Sms.KPLC}","timestamp":1774094400000}
            ]}
        """.trimIndent(),
    )

    @Test
    fun `a ledga file reads back what was exported`() = runTest {
        twoLines(db)
        SmsIngestor(db, Deriver(db, clock)).ingest(RawSms("MPESA", Sms.SEND, Sms.at("2026-03-21T10:30:00Z"), null, null, SmsSource.INBOX))
        val file = tmp.newFile("ledga-2026-10-07.ledga")
        Exporter(reader, db).write(file.outputStream())
        val incoming = BackupFiles.read(file)
        assertEquals(BackupOrigin.LEDGA, incoming.origin)
        assertEquals(reader.read(), incoming.data)
        assertEquals(clock.instant(), incoming.writtenAt)
    }

    @Test
    fun `a snapshot reads as one`() {
        val store = SnapshotStore(tmp.root)
        val sms = SmsEntry("MPESA", Sms.SEND, Sms.at("2026-03-21T10:30:00Z").toEpochMilli(), source = "INBOX")
        store.write(store.current, BackupData(writtenAt = 9, appVersion = "2.0.0-test", counts = BackupCounts(1, 1), sms = listOf(sms)))
        assertEquals(BackupOrigin.SNAPSHOT, BackupFiles.read(store.current).origin)
    }

    @Test
    fun `a v1 export brings its messages and v1's category ids, without lines (R123)`() {
        val incoming = BackupFiles.read(v1Zip())
        assertEquals(BackupOrigin.V1, incoming.origin)
        assertEquals(listOf(Sms.SEND, Sms.KPLC), incoming.data.sms.map { it.body })
        assertEquals(listOf(null, null), incoming.data.sms.map { it.line })
        assertEquals(listOf("IMPORT", "IMPORT"), incoming.data.sms.map { it.source })
        assertEquals(1774089000000, incoming.data.sms.first().receivedAt)
        assertEquals(BackupCounts(sms = 2, payments = 2), incoming.data.counts)
        assertEquals(null, incoming.data.settings)
        assertEquals(
            listOf(V1Choice("TJK4AB12FB", 1, "SEND", "JANE TESTER", null), V1Choice("TJK4AB12FA", null, "PAYBILL", "KPLC PREPAID", "37100000001")),
            incoming.v1Choices,
        )
    }

    @Test
    fun `anything that isn't a Ledga backup says so`() {
        val notOurs = zip("photos.zip", "hello.txt" to "hi")
        val otherJson = zip("other.zip", "data.json" to """{"items":[]}""")
        val png = tmp.newFile("photo.png").apply { writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)) }
        val empty = tmp.newFile("empty.ledga")
        for (f in listOf(notOurs, otherJson, png, empty)) assertFailsWith<BackupFileError.NotLedga>(f.name) { BackupFiles.read(f) }
    }

    @Test
    fun `a cut-off file is damaged, and one from a newer Ledga says to update`() = runTest {
        db.transactionsDao().upsertAll(List(200) { i -> txRow(code = "TJK6%04d".format(i), lineId = null) })
        val whole = tmp.newFile("whole.ledga")
        Exporter(reader, db).write(whole.outputStream())
        val cut = tmp.newFile("cut.ledga").apply { writeBytes(whole.readBytes().copyOf(whole.length().toInt() * 6 / 10)) }
        assertFailsWith<BackupFileError.Damaged> { BackupFiles.read(cut) }
        val newer = zip(
            "newer.ledga",
            "manifest.json" to """{"format":2,"appVersion":"3.0.0","exportedAt":1,"counts":{"sms":0,"payments":0}}""",
            "data.json" to """{"format":2,"writtenAt":1,"appVersion":"3.0.0","counts":{"sms":0,"payments":0}}""",
        )
        assertFailsWith<BackupFileError.Newer> { BackupFiles.read(newer) }
    }

    @Test
    fun `a backup with no messages says there is nothing to restore (final review I2)`() = runTest {
        // Restored with Replace it would empty the phone; merged it would add only lines and rules.
        val file = tmp.newFile("empty-history.ledga")
        Exporter(reader, db).write(file.outputStream())
        val e = assertFailsWith<BackupFileError.Empty> { BackupFiles.read(file) }
        assertEquals("This backup holds no messages, so there's nothing to restore.", e.message)
    }
}
