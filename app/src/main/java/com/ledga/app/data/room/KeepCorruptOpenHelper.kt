package com.ledga.app.data.room

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/**
 * Room's default open helper deletes a database it finds corrupt and opens a fresh, empty one in its place. For
 * ledga.db that is the user's whole history, so here corruption is left alone: the open fails, Startup shows the
 * recovery screen, and the file and its pre-v6 copy both survive (spec §8 step 1).
 */
internal object KeepCorruptOpenHelperFactory : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name)
                .callback(KeepCorrupt(configuration.callback))
                .noBackupDirectory(configuration.useNoBackupDirectory)
                .allowDataLossOnRecovery(configuration.allowDataLossOnRecovery)
                .build(),
        )

    /** Room's own callback in every respect but corruption. */
    private class KeepCorrupt(private val inner: SupportSQLiteOpenHelper.Callback) : SupportSQLiteOpenHelper.Callback(inner.version) {
        override fun onConfigure(db: SupportSQLiteDatabase) = inner.onConfigure(db)
        override fun onCreate(db: SupportSQLiteDatabase) = inner.onCreate(db)
        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = inner.onUpgrade(db, oldVersion, newVersion)
        override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = inner.onDowngrade(db, oldVersion, newVersion)
        override fun onOpen(db: SupportSQLiteDatabase) = inner.onOpen(db)

        /** The framework default deletes the file. Do nothing: the open then fails and the data stays where it is. */
        override fun onCorruption(db: SupportSQLiteDatabase) = Unit
    }
}
