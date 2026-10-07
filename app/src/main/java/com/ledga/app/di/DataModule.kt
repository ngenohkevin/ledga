package com.ledga.app.di

import com.ledga.app.data.trackers.Trackers
import com.ledga.app.data.lines.SelectedLine
import android.content.ContentResolver
import android.content.Context
import androidx.work.WorkManager
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
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.lines.SimDirectory
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.notify.AndroidPhoneNotifications
import com.ledga.app.notify.Notifier
import com.ledga.app.notify.PhoneNotifications
import com.ledga.app.startup.SmsAccess
import com.ledga.app.startup.Startup
import com.ledga.app.startup.V1Leftovers
import com.ledga.app.work.BackgroundWork
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
     * The one Room instance on ledga.db (`app/DATA.md`). Room opens the file lazily, so `Startup.run()` is its first
     * opener, after `LedgaApp` has taken the pre-v6 snapshot. Never fallbackToDestructiveMigration.
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

    /** R47: the one line choice every summary follows. */
    @Provides
    @Singleton
    fun selectedLine(lines: LinesRepository, settings: SettingsStore): SelectedLine = SelectedLine(lines, settings)

    @Provides
    @Singleton
    fun inbox(resolver: ContentResolver): InboxSource = MpesaInbox(resolver)

    @Provides
    @Singleton
    fun scanner(inbox: InboxSource, lines: LinesRepository, ingestor: SmsIngestor, settings: SettingsStore): InboxScanner =
        InboxScanner(inbox, lines, ingestor, settings)

    @Provides
    @Singleton
    fun backgroundWork(wm: WorkManager): BackgroundWork = WorkManagerBackgroundWork(wm)

    /** Android's notification shade (spec §11). */
    @Provides
    @Singleton
    fun phoneNotifications(@ApplicationContext context: Context): PhoneNotifications = AndroidPhoneNotifications(context)

    /** The one writer of `alerts` (spec §7.1, R101). */
    @Provides
    @Singleton
    fun notifier(db: LedgaDatabase, phone: PhoneNotifications, clock: Clock): Notifier = Notifier(db, phone, clock)

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
        wm: WorkManager,
    ): Startup = Startup(db, snapshot, importer, deriver, lines, settings, work, sms, V1Leftovers(context, wm))
}
