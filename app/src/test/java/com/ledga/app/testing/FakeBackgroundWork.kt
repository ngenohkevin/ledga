package com.ledga.app.testing

import com.ledga.app.data.settings.Settings
import com.ledga.app.work.BackgroundWork
import com.ledga.app.work.HistoryProgress
import com.ledga.app.work.ImportProgress
import com.ledga.app.work.RestoreProgress
import com.ledga.app.work.RestoreRequest
import com.ledga.app.work.Scheduled
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow

/** Records what was asked of WorkManager; tests drive the progress flows by hand. */
class FakeBackgroundWork : BackgroundWork {
    val calls = mutableListOf<String>()
    var chainRunning = false

    /** What 5a queued (alerts, schedules, the 6-hourly check), kept apart from [calls] so 4a's tests stay as they are. */
    val scheduled = mutableListOf<String>()
    override val history = MutableStateFlow<HistoryProgress?>(null)
    override val inboxImport = MutableStateFlow<ImportProgress>(ImportProgress.Idle)
    override val legacyImportFailed = MutableStateFlow(false)

    override fun afterMigration() {
        calls += "afterMigration"
    }

    override fun rebuild() {
        calls += "rebuild"
    }

    override fun catchUp() {
        calls += "catchUp"
    }

    override fun importInbox() {
        calls += "importInbox"
    }

    override suspend fun migrationChainRunning() = chainRunning

    override fun alertsFor(codes: Set<String>, receivedAt: Instant) {
        scheduled += "alertsFor ${codes.sorted().joinToString(",")}"
    }

    override fun schedule(kind: Scheduled, settings: Settings, replace: Boolean) {
        scheduled += "${kind.name} ${if (kind.isOn(settings)) "on" else "off"} replace=$replace"
    }

    override fun keepSyncing() {
        scheduled += "keepSyncing"
    }

    override fun snapshotSoon() {
        scheduled += "snapshotSoon"
    }

    /** Restores asked for (R122). */
    val restores = mutableListOf<RestoreRequest>()
    override val restoreProgress = MutableStateFlow<RestoreProgress>(RestoreProgress.Idle)

    override fun restore(request: RestoreRequest) {
        restores += request
    }
}
