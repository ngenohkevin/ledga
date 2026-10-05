package com.ledga.app.data.legacy

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.ledga.app.data.room.LedgaDatabase
import java.io.File

/**
 * Spec §8 step 1: before Room first opens the database, a pre-v6 file is copied to `files/pre-v6/` so a failed
 * migration can be recovered ("Send me the database"). Kept until the first successful v2 launch and a completed
 * rebuild, then [delete]d. Uses the framework SQLite API: Room must not open the file before this runs.
 */
class PreV6Snapshot(private val context: Context, private val dbName: String = LedgaDatabase.FILE_NAME) {
    val dir: File get() = File(context.filesDir, "pre-v6")

    fun exists(): Boolean = File(dir, dbName).exists()

    /** True when a snapshot was written now. Never overwrites an existing (older, more original) snapshot. */
    fun takeIfNeeded(): Boolean {
        val db = context.getDatabasePath(dbName)
        if (!db.exists() || exists()) return false
        val version = SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READWRITE).use { sqlite ->
            sqlite.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            sqlite.version
        }
        if (version >= 6) return false
        dir.mkdirs()
        for (suffix in listOf("", "-wal", "-shm")) {
            val source = File(db.path + suffix)
            if (!source.exists()) continue
            val temp = File(dir, "$dbName$suffix.tmp")
            source.copyTo(temp, overwrite = true)
            check(temp.renameTo(File(dir, dbName + suffix))) { "could not finish the pre-v6 snapshot" }
        }
        return true
    }

    fun delete() {
        dir.deleteRecursively()
    }
}
