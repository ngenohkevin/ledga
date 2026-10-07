package com.ledga.app.data.backup

import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.settings.SettingsStore
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Which snapshot a restore can come from on this phone (Export & restore). */
enum class RestoreSourceKind { BEFORE_RESTORE, EARLIER }

data class RestoreSource(val kind: RestoreSourceKind, val info: SnapshotInfo)

/**
 * When the snapshot is written (spec §12.1, R119): after the person is onboarded and has messages, never sooner than
 * [EVERY] after the last in the background, and at once after a rebuild or a restore. Before that, a snapshot Android
 * restored onto a new phone stays untouched until the person decides (R120). One write at a time.
 */
class Snapshots(
    private val store: SnapshotStore,
    private val reader: BackupReader,
    private val db: LedgaDatabase,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    private val lock = Mutex()

    /** The app went to the background, or the 6-hourly check ran. True when it wrote. */
    suspend fun writeIfDue(): Boolean = lock.withLock {
        val last = store.current.takeIf { it.exists() }?.lastModified()
        if (last != null && clock.millis() - last < EVERY.toMillis()) return@withLock false
        writeLocked()
    }

    /** After a rebuild or a restore. True when it wrote. */
    suspend fun write(): Boolean = lock.withLock { writeLocked() }

    private suspend fun writeLocked(): Boolean = withContext(Dispatchers.IO) {
        if (!settings.current().onboarded || db.smsDao().count() == 0) return@withContext false
        store.write(store.current, reader.read())
        true
    }

    /** R121: this phone's history, kept before a restore changes it. False when there is nothing to keep. */
    suspend fun saveBeforeRestore(): Boolean = lock.withLock {
        withContext(Dispatchers.IO) {
            if (db.smsDao().count() == 0) return@withContext false
            store.write(store.beforeRestore, reader.read())
            true
        }
    }

    /** R120: a snapshot this install didn't restore is kept as the earlier backup, still in `files/backup/`. */
    suspend fun keepAsEarlier() = lock.withLock {
        withContext(Dispatchers.IO) {
            val found = store.current
            if (found.exists() && !found.renameTo(store.earlier)) throw IOException("couldn't keep the earlier backup")
        }
    }

    /** When this phone's snapshot was last written (You's "Android backup" row); null before the first. */
    fun savedAt(): Instant? = store.current.takeIf { it.exists() }?.let { Instant.ofEpochMilli(it.lastModified()) }

    /** Onboarding's offer (R126): the snapshot found on a fresh install. */
    suspend fun found(): SnapshotInfo? = withContext(Dispatchers.IO) { store.info(store.current) }

    /** Export & restore's sources (R120, R121), newest kind first. */
    suspend fun sources(): List<RestoreSource> = withContext(Dispatchers.IO) {
        listOfNotNull(
            store.info(store.beforeRestore)?.let { RestoreSource(RestoreSourceKind.BEFORE_RESTORE, it) },
            store.info(store.earlier)?.let { RestoreSource(RestoreSourceKind.EARLIER, it) },
        )
    }

    companion object {
        val EVERY: Duration = Duration.ofHours(1)
    }
}
