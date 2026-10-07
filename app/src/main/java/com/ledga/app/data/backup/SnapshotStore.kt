@file:OptIn(ExperimentalSerializationApi::class)

package com.ledga.app.data.backup

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

/**
 * 5b's folders. [snapshots] is `files/backup/`, the only folder Android backs up (R114); [exports] is in the cache (an
 * export to share); [incoming] is a picked restore file's copy, in no-backup storage (R122).
 */
data class BackupDirs(val snapshots: File, val exports: File, val incoming: File) {
    companion object {
        fun of(context: Context) =
            BackupDirs(File(context.filesDir, "backup"), File(context.cacheDir, "exports"), File(context.noBackupFilesDir, "restore"))
    }
}

/** A snapshot's date and how many payments it holds (spec §12.1: "date + transaction count"). */
data class SnapshotInfo(val file: File, val writtenAt: Instant, val payments: Int)

/** The snapshot files in `files/backup/`: gzipped [BackupData] JSON. */
class SnapshotStore(private val dir: File) {
    val current: File get() = File(dir, CURRENT)

    /** R120: a backup this install found but didn't restore. */
    val earlier: File get() = File(dir, EARLIER)

    /** R121: this phone's history just before its last restore. */
    val beforeRestore: File get() = File(dir, BEFORE_RESTORE)

    /**
     * Written beside [file] and renamed over it: a crash leaves the old file or the new one, never half of one. The file
     * is dated [BackupData.writtenAt], so its age follows Ledga's clock.
     */
    fun write(file: File, data: BackupData) {
        dir.mkdirs()
        val temp = File(dir, file.name + ".tmp")
        try {
            GZIPOutputStream(temp.outputStream().buffered()).use { BackupJson.json.encodeToStream(BackupData.serializer(), data, it) }
            if (!temp.renameTo(file)) throw IOException("couldn't replace ${file.name}")
            file.setLastModified(data.writtenAt)
        } finally {
            temp.delete()
        }
    }

    /** Null when [file] doesn't exist; [BackupFileError] when it can't be read. */
    fun read(file: File): BackupData? = if (!file.exists()) null else file.inputStream().buffered().use(::decode)

    /** For offering a snapshot: null when it is missing or can't be read. */
    fun info(file: File): SnapshotInfo? = try {
        read(file)?.let { SnapshotInfo(file, Instant.ofEpochMilli(it.writtenAt), it.counts.payments) }
    } catch (e: BackupFileError) {
        null
    }

    companion object {
        const val CURRENT = "ledga-snapshot.json.gz"
        const val EARLIER = "ledga-snapshot-earlier.json.gz"
        const val BEFORE_RESTORE = "ledga-before-restore.json.gz"

        /** A gzipped [BackupData]: Damaged when it isn't one, Newer when a later Ledga wrote it. */
        fun decode(input: InputStream): BackupData {
            val data = try {
                GZIPInputStream(input).use { BackupJson.json.decodeFromStream(BackupData.serializer(), it) }
            } catch (e: IOException) {
                throw BackupFileError.Damaged(e)
            } catch (e: SerializationException) {
                throw BackupFileError.Damaged(e)
            } catch (e: IllegalArgumentException) {
                throw BackupFileError.Damaged(e)
            }
            if (data.format > BackupData.FORMAT) throw BackupFileError.Newer()
            return data
        }
    }
}
