package com.ledga.app.data.backup

import com.ledga.app.data.room.LedgaDatabase
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExportResult(val payments: Int, val messages: Int)

/** Spec §12.2: a `.ledga` file is a zip of `manifest.json`, `data.json` (the backup) and `transactions.csv`. No row limit. */
class Exporter(private val reader: BackupReader, private val db: LedgaDatabase) {

    /** Writes the whole file to [out] and closes it. */
    suspend fun write(out: OutputStream): ExportResult = withContext(Dispatchers.IO) {
        val data = reader.read()
        val rows = db.transactionsDao().exportRows()
        val categories = db.categoriesDao().all().associate { it.key to it.name }
        val lines = db.linesDao().all().associate { it.id to it.displayName }
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            val manifest = ExportManifest(data.format, data.appVersion, data.writtenAt, data.counts)
            zip.write(BackupJson.json.encodeToString(ExportManifest.serializer(), manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(DATA))
            zip.write(BackupJson.json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(CSV))
            val csv = zip.bufferedWriter(Charsets.UTF_8)
            csv.write(CsvExport.BOM.code)
            csv.write(CsvExport.line(CsvExport.HEADER))
            rows.forEach { tx -> csv.write(CsvExport.line(CsvExport.row(tx, categories[tx.categoryKey], tx.lineId?.let(lines::get)))) }
            csv.flush() // not close(): that would close the zip before its last entry is finished
            zip.closeEntry()
        }
        ExportResult(rows.size, data.sms.size)
    }

    companion object {
        const val MANIFEST = "manifest.json"
        const val DATA = "data.json"
        const val CSV = "transactions.csv"

        /** What the share sheet and the file picker see; `.ledga` has no registered type. */
        const val MIME = "application/octet-stream"

        fun fileName(day: LocalDate): String = "ledga-$day.ledga"
    }
}
