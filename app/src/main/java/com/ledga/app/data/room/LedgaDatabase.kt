package com.ledga.app.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.ledga.app.data.room.dao.AlertsDao
import com.ledga.app.data.room.dao.CategoriesDao
import com.ledga.app.data.room.dao.LedgerDao
import com.ledga.app.data.room.dao.LegacyDao
import com.ledga.app.data.room.dao.LinesDao
import com.ledga.app.data.room.dao.MetaDao
import com.ledga.app.data.room.dao.OverridesDao
import com.ledga.app.data.room.dao.RestoreDao
import com.ledga.app.data.room.dao.RulesDao
import com.ledga.app.data.room.dao.SmsDao
import com.ledga.app.data.room.dao.TransactionsDao
import com.ledga.app.data.room.migration.LegacyMigrations
import com.ledga.app.data.room.migration.MIGRATION_5_6

/**
 * Ledga v2's database (schema 6), the same file v1 used. One instance (the Hilt singleton): two Room instances on one
 * file would corrupt it. Never fallbackToDestructiveMigration, and never let a corrupt file be deleted
 * ([KeepCorruptOpenHelperFactory]): it may be the user's only copy of their history.
 */
@Database(
    entities = [SmsRow::class, TxRow::class, OverrideRow::class, RuleRow::class, CategoryRow::class, LineRow::class, AlertRow::class, MetaRow::class],
    views = [LedgerRow::class],
    version = 6,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class LedgaDatabase : RoomDatabase() {
    abstract fun categoriesDao(): CategoriesDao
    abstract fun rulesDao(): RulesDao
    abstract fun metaDao(): MetaDao
    abstract fun smsDao(): SmsDao
    abstract fun transactionsDao(): TransactionsDao
    abstract fun overridesDao(): OverridesDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun legacyDao(): LegacyDao
    abstract fun linesDao(): LinesDao
    abstract fun alertsDao(): AlertsDao
    abstract fun restoreDao(): RestoreDao

    companion object {
        const val FILE_NAME = "ledga.db"

        /** Every migration from any shipped v1.x schema to 6. */
        val MIGRATIONS: Array<Migration> get() = LegacyMigrations.ALL + MIGRATION_5_6

        fun builder(context: Context, name: String = FILE_NAME): RoomDatabase.Builder<LedgaDatabase> =
            Room.databaseBuilder(context, LedgaDatabase::class.java, name)
                .openHelperFactory(KeepCorruptOpenHelperFactory)
                .addMigrations(*MIGRATIONS)
                .addCallback(SeedOnCreate)

        fun inMemory(context: Context): RoomDatabase.Builder<LedgaDatabase> =
            Room.inMemoryDatabaseBuilder(context, LedgaDatabase::class.java).addCallback(SeedOnCreate)
    }
}
