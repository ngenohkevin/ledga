package com.ledga.app.testing

import com.ledga.app.work.BackgroundWork
import com.ledga.app.work.HistoryProgress
import com.ledga.app.work.ImportProgress
import kotlinx.coroutines.flow.MutableStateFlow

/** Records what was asked of WorkManager; tests drive the progress flows by hand. */
class FakeBackgroundWork : BackgroundWork {
    val calls = mutableListOf<String>()
    var chainRunning = false
    override val history = MutableStateFlow<HistoryProgress?>(null)
    override val inboxImport = MutableStateFlow<ImportProgress>(ImportProgress.Idle)

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
}
