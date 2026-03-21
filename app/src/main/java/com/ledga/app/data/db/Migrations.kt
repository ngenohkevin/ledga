package com.ledga.app.data.db

/**
 * Room database migrations.
 *
 * NEVER use fallbackToDestructiveMigration() — this destroys all user data.
 * Always define explicit migrations for schema changes.
 *
 * Version history:
 * 1 - Initial schema (transactions, categories, budgets, category_rules)
 */
object Migrations {
    // Future migrations will be added here as:
    // val MIGRATION_1_2 = object : Migration(1, 2) {
    //     override fun migrate(db: SupportSQLiteDatabase) {
    //         db.execSQL("ALTER TABLE ...")
    //     }
    // }
}
