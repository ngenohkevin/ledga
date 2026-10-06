package com.ledga.app.data.legacy

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.ledga.app.data.room.LedgaDatabase
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Spec §8 step 1: before Room first opens the database, a pre-v6 file is copied to `files/pre-v6/` so a failed
 * migration can be recovered ("Send me the database"). Kept until the first successful v2 launch and a completed
 * rebuild, then [delete]d. The original is never opened: files are copied first and the version is read from the
 * copy, because opening a corrupt file with the default error handler deletes it (the very case this exists for)
 * and a read-write open can change its journal mode. Room must not open the file before this runs. A database whose
 * header already reads 6 is recognised in 100 bytes and never copied (refinement R29).
 */
class PreV6Snapshot(
    private val context: Context,
    private val dbName: String = LedgaDatabase.FILE_NAME,
    private val copy: (from: File, to: File) -> Unit = { from, to -> from.copyTo(to, overwrite = true) },
) {
    val dir: File get() = File(context.filesDir, "pre-v6")
    private val staging: File get() = File(context.filesDir, "pre-v6.tmp")

    fun exists(): Boolean = File(dir, dbName).exists()

    /** The copy "Send me the database" shares (spec §8 step 1). */
    fun file(): File = File(dir, dbName)

    /** True when a snapshot was written now. Never overwrites an existing (older, more original) snapshot. */
    fun takeIfNeeded(): Boolean {
        val db = context.getDatabasePath(dbName)
        if (!db.exists() || exists()) return false
        // R29: every launch after the upgrade lands here. The header already says 6, so the database is not copied.
        if ((headerVersion(db) ?: 0) >= 6) return false
        staging.deleteRecursively()
        staging.mkdirs()
        for (suffix in SUFFIXES) {
            val source = File(db.path + suffix)
            if (source.exists()) copy(source, File(staging, dbName + suffix))
        }
        val version = versionOf(File(staging, dbName))
        if (version != null && version >= 6) {
            staging.deleteRecursively()
            return false
        }
        dir.deleteRecursively()
        check(staging.renameTo(dir)) { "could not finish the pre-v6 snapshot" }
        return true
    }

    fun delete() {
        dir.deleteRecursively()
    }

    /** The copy's user_version, or null when it can't be read (corrupt): then it is kept, which is the point. */
    private fun versionOf(copy: File): Int? = try {
        SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READONLY, KEEP_CORRUPT).use { it.version }
    } catch (e: SQLiteException) {
        null
    }

    companion object {
        private val SUFFIXES = listOf("", "-wal", "-shm")

        /** The framework default deletes a corrupt file; this one leaves it alone. */
        private val KEEP_CORRUPT = DatabaseErrorHandler { }

        /**
         * SQLite's user_version, read from the file header (bytes 60–63, big-endian) without opening the database;
         * null when the file is missing or isn't SQLite. A WAL can only hold a newer version than the header, so a
         * header of 6 or more is trustworthy and anything lower takes the full copy path.
         */
        fun headerVersion(file: File): Int? = try {
            RandomAccessFile(file, "r").use { f ->
                if (f.length() < 100) return null
                val header = ByteArray(100)
                f.readFully(header)
                if (String(header, 0, 15, Charsets.US_ASCII) != "SQLite format 3") return null
                ByteBuffer.wrap(header, 60, 4).int
            }
        } catch (e: IOException) {
            null
        }
    }
}
