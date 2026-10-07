package com.ledga.app.testing

import com.ledga.app.data.backup.BackupReader
import com.ledga.app.data.backup.DeviceId
import com.ledga.app.data.backup.SnapshotStore
import com.ledga.app.data.backup.Snapshots
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.settings.SettingsStore
import java.io.File
import java.time.Clock
import java.time.Instant

/** [Snapshots] over [db], writing into [dir] (a test's TemporaryFolder). No device fingerprint. */
fun testSnapshots(
    db: LedgaDatabase,
    dir: File,
    settings: SettingsStore = SettingsStore(FakePrefsStore()),
    clock: Clock = MutableClock(Instant.parse("2026-10-07T17:00:00Z")),
): Snapshots = Snapshots(SnapshotStore(dir), BackupReader(db, settings, DeviceId { null }, "2.0.0-test", clock), db, settings, clock)
