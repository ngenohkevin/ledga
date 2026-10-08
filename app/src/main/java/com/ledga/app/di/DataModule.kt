package com.ledga.app.di

import android.content.ContentResolver
import android.content.Context
import androidx.work.WorkManager
import com.ledga.app.BuildConfig
import com.ledga.app.data.backup.AndroidDeviceId
import com.ledga.app.data.backup.AndroidDocuments
import com.ledga.app.data.backup.BackupDirs
import com.ledga.app.data.backup.BackupReader
import com.ledga.app.data.backup.BackupStatus
import com.ledga.app.data.backup.DeviceId
import com.ledga.app.data.backup.Documents
import com.ledga.app.data.backup.Exporter
import com.ledga.app.data.backup.Restorer
import com.ledga.app.data.backup.SnapshotStore
import com.ledga.app.data.backup.Snapshots
import com.ledga.app.data.capture.InboxScanner
import com.ledga.app.data.capture.InboxSource
import com.ledga.app.data.capture.MpesaInbox
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.legacy.LegacyImporter
import com.ledga.app.data.legacy.PreV6Snapshot
import com.ledga.app.data.lines.AndroidSimDirectory
import com.ledga.app.data.lines.LinePlacements
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.lines.SelectedLine
import com.ledga.app.data.lines.SimDirectory
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.data.trackers.Trackers
import com.ledga.app.notify.AndroidPhoneNotifications
import com.ledga.app.notify.FulizaCheck
import com.ledga.app.notify.Notifier
import com.ledga.app.notify.PaymentAlerts
import com.ledga.app.notify.PhoneNotifications
import com.ledga.app.notify.SummaryAlerts
import com.ledga.app.receiver.IncomingSms
import com.ledga.app.startup.SmsAccess
import com.ledga.app.startup.Startup
import com.ledga.app.startup.V1Leftovers
import com.ledga.app.work.BackgroundWork
import com.ledga.app.work.UpdateWork
import com.ledga.app.work.WorkManagerBackgroundWork
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    /**
     * The one Room instance on ledga.db (`app/DATA.md`). Room opens the file lazily. `Startup.run()` usually opens it
     * first, but a cold start from the receiver or a worker may open it before; that is safe because `LedgaApp` takes
     * the pre-v6 snapshot in `Application.onCreate`, before either. Never fallbackToDestructiveMigration.
     */
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): LedgaDatabase = LedgaDatabase.builder(context).build()

    @Provides
    @Singleton
    fun deriver(db: LedgaDatabase, clock: Clock): Deriver = Deriver(db, clock)

    @Provides
    @Singleton
    fun ingestor(db: LedgaDatabase, deriver: Deriver): SmsIngestor = SmsIngestor(db, deriver)

    @Provides
    @Singleton
    fun ledger(db: LedgaDatabase): LedgerQueries = LedgerQueries(db)

    /** The one tracker reader (R52): Home's strip, the Trackers tab and Tracker detail. */
    @Provides
    @Singleton
    fun trackers(db: LedgaDatabase, ledger: LedgerQueries): Trackers = Trackers(db, ledger)

    /** Every change a person makes to a transaction (spec §7.4). */
    @Provides
    @Singleton
    fun edits(db: LedgaDatabase, deriver: Deriver, clock: Clock): TransactionEdits = TransactionEdits(db, deriver, clock)

    @Provides
    fun legacyImporter(db: LedgaDatabase, clock: Clock): LegacyImporter = LegacyImporter(db, clock)

    @Provides
    fun snapshot(@ApplicationContext context: Context): PreV6Snapshot = PreV6Snapshot(context)

    @Provides
    @Singleton
    fun sims(@ApplicationContext context: Context): SimDirectory = AndroidSimDirectory(context)

    @Provides
    @Singleton
    fun lines(db: LedgaDatabase, sims: SimDirectory, clock: Clock): LinesRepository = LinesRepository(db.linesDao(), sims, clock)

    /** R116, R128. */
    @Provides
    fun linePlacements(db: LedgaDatabase): LinePlacements = LinePlacements(db)

    /** R47: the one line choice every summary follows. */
    @Provides
    @Singleton
    fun selectedLine(lines: LinesRepository, settings: SettingsStore): SelectedLine = SelectedLine(lines, settings)

    @Provides
    @Singleton
    fun inbox(resolver: ContentResolver): InboxSource = MpesaInbox(resolver)

    @Provides
    @Singleton
    fun scanner(inbox: InboxSource, lines: LinesRepository, ingestor: SmsIngestor, settings: SettingsStore, clock: Clock): InboxScanner =
        InboxScanner(inbox, lines, ingestor, settings, clock)

    @Provides
    @Singleton
    fun backgroundWork(wm: WorkManager, clock: Clock): BackgroundWork = WorkManagerBackgroundWork(wm, clock)

    /** Android's notification shade (spec §11). */
    @Provides
    @Singleton
    fun phoneNotifications(@ApplicationContext context: Context): PhoneNotifications = AndroidPhoneNotifications(context)

    /** The one writer of `alerts` (spec §7.1, R101). */
    @Provides
    @Singleton
    fun notifier(db: LedgaDatabase, phone: PhoneNotifications, clock: Clock): Notifier = Notifier(db, phone, clock)

    /** The receiver's alerts (spec §7.2 step 4, R104). */
    @Provides
    fun paymentAlerts(db: LedgaDatabase, settings: SettingsStore, notifier: Notifier, clock: Clock): PaymentAlerts =
        PaymentAlerts(db, settings, notifier, clock)

    /** The receiver's pipeline (R112). */
    @Provides
    fun incomingSms(lines: LinesRepository, ingestor: SmsIngestor, work: BackgroundWork, clock: Clock): IncomingSms =
        IncomingSms(lines, ingestor, work, clock)

    /** The daily and weekly summaries (spec §11, R106). */
    @Provides
    fun summaryAlerts(db: LedgaDatabase, ledger: LedgerQueries, notifier: Notifier, clock: Clock): SummaryAlerts =
        SummaryAlerts(db, ledger, notifier, clock)

    /** The 9 AM Fuliza check (spec §11, R105). */
    @Provides
    fun fulizaCheck(db: LedgaDatabase, notifier: Notifier, clock: Clock): FulizaCheck = FulizaCheck(db, notifier, clock)

    /** R115: the hashed per-app device id. */
    @Provides
    @Singleton
    fun deviceId(@ApplicationContext context: Context): DeviceId = AndroidDeviceId(context)

    @Provides
    @Singleton
    fun backupDirs(@ApplicationContext context: Context): BackupDirs = BackupDirs.of(context)

    @Provides
    @Singleton
    fun snapshotStore(dirs: BackupDirs): SnapshotStore = SnapshotStore(dirs.snapshots)

    @Provides
    @Singleton
    fun backupReader(db: LedgaDatabase, settings: SettingsStore, device: DeviceId, clock: Clock): BackupReader =
        BackupReader(db, settings, device, BuildConfig.VERSION_NAME, clock)

    /** Spec §12.1, R119: the one decider of when the snapshot is written. */
    @Provides
    @Singleton
    fun snapshots(store: SnapshotStore, reader: BackupReader, db: LedgaDatabase, settings: SettingsStore, clock: Clock): Snapshots =
        Snapshots(store, reader, db, settings, clock)

    @Provides
    fun documents(resolver: ContentResolver): Documents = AndroidDocuments(resolver)

    @Provides
    fun backupStatus(snapshots: Snapshots): BackupStatus = BackupStatus { snapshots.savedAt() }

    /** Spec §12.2. */
    @Provides
    fun exporter(reader: BackupReader, db: LedgaDatabase): Exporter = Exporter(reader, db)

    /** Spec §12.3 (R121–R123). */
    @Provides
    fun restorer(db: LedgaDatabase, deriver: Deriver, settings: SettingsStore, snapshots: Snapshots, sims: SimDirectory, device: DeviceId, clock: Clock): Restorer =
        Restorer(db, deriver, settings, snapshots, sims, device, clock)

    @Provides
    fun startup(
        @ApplicationContext context: Context,
        db: LedgaDatabase,
        snapshot: PreV6Snapshot,
        importer: LegacyImporter,
        deriver: Deriver,
        lines: LinesRepository,
        settings: SettingsStore,
        work: BackgroundWork,
        sms: SmsAccess,
        updates: UpdateWork,
        wm: WorkManager,
    ): Startup = Startup(db, snapshot, importer, deriver, lines, settings, work, sms, updates, V1Leftovers(context, wm))
}
