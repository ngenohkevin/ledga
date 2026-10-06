package com.ledga.app.startup

import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.legacy.LegacyImporter
import com.ledga.app.data.legacy.PreV6Snapshot
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.settings.Settings
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.work.BackgroundWork
import java.io.File

sealed interface StartupState {
    /** The database is opening (and migrating). The splash screen stays up. */
    data object Opening : StartupState

    /** Spec §8 step 1: the migration failed. [snapshot] is the pre-v6 copy to share, when there is one. */
    data class Failed(val reason: String, val snapshot: File?) : StartupState

    data class Ready(val onboarded: Boolean) : StartupState
}

/** READ_SMS, checked fresh each time (the user can revoke it in Settings at any moment). */
fun interface SmsAccess {
    fun granted(): Boolean
}

/**
 * The app's first touch of the database (spec §8, `app/DATA.md` "Startup"). It opens it, which runs MIGRATION_5_6
 * on a v1 file, and queues the history work owed:
 * - after the migration: the import → full rescan → rebuild chain (and v1's leftovers are cleared, R31);
 * - after a parser or derivation version change: a rebuild;
 * - otherwise: deletes the pre-v6 copy once nothing is owed, and catches up on missed SMS.
 * `LedgaApp` has already taken the pre-v6 snapshot. Never throws: a failure becomes [StartupState.Failed].
 */
class Startup(
    private val db: LedgaDatabase,
    private val snapshot: PreV6Snapshot,
    private val importer: LegacyImporter,
    private val deriver: Deriver,
    private val lines: LinesRepository,
    private val settings: SettingsStore,
    private val work: BackgroundWork,
    private val sms: SmsAccess,
    private val leftovers: () -> Unit = {},
) {
    suspend fun run(): StartupState = try {
        db.openHelper.writableDatabase
        afterOpen()
    } catch (e: Exception) {
        StartupState.Failed(e.message ?: e.javaClass.simpleName, snapshot.file().takeIf { it.exists() })
    }

    private suspend fun afterOpen(): StartupState {
        val legacyPending = importer.isPending()
        when {
            legacyPending -> {
                runCatching(leftovers)
                work.afterMigration()
            }
            deriver.needsRebuild() -> work.rebuild()
            // Migrated, imported and rebuilt: the safety copy has done its job (spec §8 step 1).
            snapshot.exists() -> snapshot.delete()
        }
        val s = runCatching { settings.current() }.getOrDefault(Settings())
        if (s.onboarded && sms.granted()) {
            runCatching { lines.syncActive() }
            if (!legacyPending) work.catchUp()
        }
        return StartupState.Ready(s.onboarded)
    }
}
