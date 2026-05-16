package com.ledga.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database migrations.
 *
 * NEVER use fallbackToDestructiveMigration() — this destroys all user data.
 * Always define explicit migrations for schema changes.
 *
 * Version history:
 *   1 - Initial schema (transactions, categories, budgets, category_rules)
 *   2 - Add mpesa_accounts, goals, goal_contributions; transactions gains
 *       accountId (FK → mpesa_accounts.id) and note columns.
 *   3 - Add insights table (Phase C — rule-engine derived suggestions,
 *       with natural-key dedup + dismiss/snooze state).
 */
object Migrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // ---- mpesa_accounts ----
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `mpesa_accounts` (
                    `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    `subscriptionId` INTEGER NOT NULL,
                    `phoneNumber` TEXT,
                    `displayName` TEXT NOT NULL,
                    `colorHex` TEXT NOT NULL,
                    `isPrimary` INTEGER NOT NULL DEFAULT 0,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_mpesa_accounts_subscriptionId` " +
                        "ON `mpesa_accounts` (`subscriptionId`)"
            )

            // ---- goals ----
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `goals` (
                    `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    `name` TEXT NOT NULL,
                    `targetAmount` REAL NOT NULL,
                    `targetDate` INTEGER,
                    `contributionRule` TEXT NOT NULL,
                    `colorHex` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `completedAt` INTEGER
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_goals_completedAt` ON `goals` (`completedAt`)"
            )

            // ---- goal_contributions ----
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `goal_contributions` (
                    `goalId` INTEGER NOT NULL,
                    `transactionId` INTEGER NOT NULL,
                    `markedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`goalId`, `transactionId`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_goal_contributions_goalId` " +
                        "ON `goal_contributions` (`goalId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_goal_contributions_transactionId` " +
                        "ON `goal_contributions` (`transactionId`)"
            )

            // ---- transactions.accountId + note ----
            // SQLite ALTER TABLE … ADD COLUMN can't carry a FOREIGN KEY clause —
            // Room re-asserts the FK constraint via its own validation, but the
            // raw column add is enough here. The index makes account-scoped
            // queries cheap (we'll add a lot of them in Phase E).
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `accountId` INTEGER")
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `note` TEXT")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_transactions_accountId` " +
                        "ON `transactions` (`accountId`)"
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `insights` (
                    `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    `naturalKey` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `severity` TEXT NOT NULL,
                    `typeLabel` TEXT NOT NULL,
                    `headline` TEXT NOT NULL,
                    `body` TEXT,
                    `ctaLabel` TEXT,
                    `ctaArgs` TEXT,
                    `generatedAt` INTEGER NOT NULL,
                    `snoozedUntil` INTEGER,
                    `dismissedAt` INTEGER
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_insights_naturalKey` " +
                        "ON `insights` (`naturalKey`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_insights_generatedAt` " +
                        "ON `insights` (`generatedAt`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_insights_dismissedAt` " +
                        "ON `insights` (`dismissedAt`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_insights_snoozedUntil` " +
                        "ON `insights` (`snoozedUntil`)"
            )
        }
    }
}
