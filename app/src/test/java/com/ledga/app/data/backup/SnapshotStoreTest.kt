package com.ledga.app.data.backup

import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** R119: the snapshot file is gzipped JSON, replaced whole or not at all. */
class SnapshotStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun data(writtenAt: Long = 1_790_000_000_000, format: Int = BackupData.FORMAT) = BackupData(
        format = format,
        writtenAt = writtenAt,
        appVersion = "2.0.0-test",
        counts = BackupCounts(sms = 1, payments = 1),
        sms = listOf(SmsEntry("MPESA", "TJK4AB12FB Confirmed. Ksh500.00 sent to JANE TESTER", 1_790_000_000_000, null, null, "INBOX")),
    )

    @Test
    fun `what is written reads back the same, gzipped, and dated by when it was written`() {
        val store = SnapshotStore(File(tmp.root, "backup"))
        store.write(store.current, data())
        assertEquals(data(), store.read(store.current))
        val head = store.current.readBytes().take(2).map { it.toInt() and 0xFF }
        assertEquals(listOf(0x1F, 0x8B), head, "gzip")
        assertEquals(1_790_000_000_000, store.current.lastModified() / 1000 * 1000)
        assertEquals(SnapshotInfo(store.current, Instant.ofEpochMilli(1_790_000_000_000), 1), store.info(store.current))
    }

    @Test
    fun `a write replaces the file whole and leaves no temporary file, even after a crash left one`() {
        val dir = File(tmp.root, "backup").apply { mkdirs() }
        val store = SnapshotStore(dir)
        File(dir, "ledga-snapshot.json.gz.tmp").writeText("half a file from a crash")
        store.write(store.current, data(writtenAt = 1))
        store.write(store.current, data(writtenAt = 2))
        assertEquals(2, store.read(store.current)?.writtenAt)
        assertEquals(listOf("ledga-snapshot.json.gz"), dir.list()!!.toList())
    }

    @Test
    fun `a missing file is nothing, a damaged one says so, a newer one too`() {
        val store = SnapshotStore(File(tmp.root, "backup"))
        assertNull(store.read(store.current))
        assertNull(store.info(store.current))
        store.current.parentFile!!.mkdirs()
        store.current.writeText("not gzip")
        assertFailsWith<BackupFileError.Damaged> { store.read(store.current) }
        assertNull(store.info(store.current), "a damaged snapshot isn't offered")
        store.write(store.current, data(format = BackupData.FORMAT + 1))
        assertFailsWith<BackupFileError.Newer> { store.read(store.current) }
        assertFalse(File(store.current.path + ".tmp").exists())
    }
}
