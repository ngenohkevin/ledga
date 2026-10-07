package com.ledga.app.data.backup

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.zip.ZipInputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

enum class BackupOrigin { LEDGA, SNAPSHOT, V1 }

/** R123: what the migration's own test needs to tell a v1 user's category choice from v1's auto-categoriser. */
data class V1Choice(val code: String, val categoryId: Long?, val type: String, val recipientName: String?, val accountNumber: String?)

/** A backup ready to restore: its [data] and, for a v1 export, v1's category ids. */
data class Incoming(val origin: BackupOrigin, val data: BackupData, val v1Choices: List<V1Choice> = emptyList()) {
    val writtenAt: Instant get() = Instant.ofEpochMilli(data.writtenAt)
}

/**
 * Spec §12.3: a `.ledga` file (zip with `manifest.json` + `data.json`), a snapshot (gzipped `data.json`), or a v1 export
 * (zip whose `data.json` has `transactions[]`). Told apart by their first bytes, never by the file's name.
 */
object BackupFiles {
    /** An entry bigger than this is not a Ledga backup (and can't fill the phone's memory). */
    private const val MAX_ENTRY_BYTES = 128L * 1024 * 1024

    fun read(file: File): Incoming {
        val head = file.inputStream().use { input -> ByteArray(2).also { if (input.readFully(it) < 2) throw BackupFileError.NotLedga() } }
        val incoming = when {
            head[0] == 0x1F.toByte() && head[1] == 0x8B.toByte() ->
                Incoming(BackupOrigin.SNAPSHOT, file.inputStream().buffered().use(SnapshotStore::decode))
            head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() -> readZip(file)
            else -> throw BackupFileError.NotLedga()
        }
        // Final review I2: with no messages there is nothing to restore; Replace would empty the phone.
        if (incoming.data.sms.isEmpty()) throw BackupFileError.Empty()
        return incoming
    }

    private fun readZip(file: File): Incoming {
        val entries = try {
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                buildMap<String, String> {
                    while (true) {
                        val e = zip.nextEntry ?: break
                        if (e.name == Exporter.MANIFEST || e.name == Exporter.DATA) put(e.name, zip.capped())
                    }
                }
            }
        } catch (e: IOException) {
            throw BackupFileError.Damaged(e)
        }
        val data = entries[Exporter.DATA] ?: throw BackupFileError.NotLedga()
        val manifest = entries[Exporter.MANIFEST]
        return try {
            if (manifest != null) {
                val m = BackupJson.json.decodeFromString(ExportManifest.serializer(), manifest)
                if (m.format > BackupData.FORMAT) throw BackupFileError.Newer()
                val d = BackupJson.json.decodeFromString(BackupData.serializer(), data)
                if (d.format > BackupData.FORMAT) throw BackupFileError.Newer()
                Incoming(BackupOrigin.LEDGA, d)
            } else {
                val root = BackupJson.json.parseToJsonElement(data) as? JsonObject ?: throw BackupFileError.NotLedga()
                if (root["transactions"] !is JsonArray) throw BackupFileError.NotLedga()
                fromV1(BackupJson.json.decodeFromJsonElement(V1Export.serializer(), root.jsonObject))
            }
        } catch (e: SerializationException) {
            throw BackupFileError.Damaged(e)
        } catch (e: IllegalArgumentException) {
            throw BackupFileError.Damaged(e)
        }
    }

    /** R123: each v1 transaction's own SMS, as received; no lines, rules or settings (a v1 export has none). */
    private fun fromV1(v1: V1Export): Incoming = Incoming(
        BackupOrigin.V1,
        BackupData(
            writtenAt = v1.exportedAt,
            appVersion = "1.x",
            counts = BackupCounts(sms = v1.transactions.size, payments = v1.transactions.size),
            sms = v1.transactions.map { SmsEntry(sender = "MPESA", body = it.rawSms, receivedAt = it.timestamp, source = "IMPORT") },
        ),
        v1.transactions.map { V1Choice(it.transactionCode, it.categoryId, it.type, it.recipientName, it.accountNumber) },
    )

    private fun InputStream.readFully(buffer: ByteArray): Int {
        var n = 0
        while (n < buffer.size) {
            val r = read(buffer, n, buffer.size - n)
            if (r < 0) break
            n += r
        }
        return n
    }

    private fun ZipInputStream.capped(): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val r = read(buffer)
            if (r < 0) break
            total += r
            if (total > MAX_ENTRY_BYTES) throw BackupFileError.NotLedga()
            out.write(buffer, 0, r)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    /** v1.6's `ExportData` (only what v2 reads). */
    @Serializable
    private data class V1Export(val version: Int = 1, val exportedAt: Long = 0, val transactions: List<V1Tx>)

    @Serializable
    private data class V1Tx(
        val transactionCode: String,
        val type: String = "UNKNOWN",
        val recipientName: String? = null,
        val accountNumber: String? = null,
        val categoryId: Long? = null,
        val rawSms: String,
        val timestamp: Long,
    )
}
