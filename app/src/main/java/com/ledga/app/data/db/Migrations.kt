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
 *   4 - categories gains isTransfer (own-account transfer categories,
 *       excluded from spending) + seed the "My Accounts" category;
 *       transactions gains fulizaLimit (available Fuliza limit from SMS).
 *   5 - transactions gains carTag (user-applied Fuel/Service car-expense tag,
 *       stored as TEXT enum name; null = not a car expense).
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

            // ---- transactions: add accountId (FK → mpesa_accounts) + note ----
            // SQLite cannot add a FOREIGN KEY via ALTER TABLE — the constraint
            // can only be declared at CREATE TABLE time. So we recreate the
            // table: build the new shape, copy rows, drop the old, rename.
            // Room runs this migration inside its own transaction, which defers
            // FK enforcement until commit, so the swap is safe.
            // Defensive: if a previous failed run left a half-built table, drop it.
            db.execSQL("DROP TABLE IF EXISTS `transactions_new`")
            db.execSQL(
                """
                CREATE TABLE `transactions_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `transactionCode` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `amount` REAL NOT NULL,
                    `transactionCost` REAL NOT NULL,
                    `recipientName` TEXT,
                    `recipientPhone` TEXT,
                    `accountNumber` TEXT,
                    `destinationCountry` TEXT,
                    `balance` REAL NOT NULL,
                    `direction` TEXT NOT NULL,
                    `categoryId` INTEGER,
                    `fulizaAmount` REAL,
                    `fulizaOutstanding` REAL,
                    `reversedTransactionCode` TEXT,
                    `rawSms` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `accountId` INTEGER,
                    `note` TEXT,
                    FOREIGN KEY (`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                    FOREIGN KEY (`accountId`) REFERENCES `mpesa_accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `transactions_new` (
                    `id`, `transactionCode`, `type`, `amount`, `transactionCost`,
                    `recipientName`, `recipientPhone`, `accountNumber`, `destinationCountry`,
                    `balance`, `direction`, `categoryId`, `fulizaAmount`, `fulizaOutstanding`,
                    `reversedTransactionCode`, `rawSms`, `timestamp`, `createdAt`,
                    `accountId`, `note`
                )
                SELECT
                    `id`, `transactionCode`, `type`, `amount`, `transactionCost`,
                    `recipientName`, `recipientPhone`, `accountNumber`, `destinationCountry`,
                    `balance`, `direction`, `categoryId`, `fulizaAmount`, `fulizaOutstanding`,
                    `reversedTransactionCode`, `rawSms`, `timestamp`, `createdAt`,
                    NULL, NULL
                FROM `transactions`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `transactions`")
            db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
            // Indices live with the table — recreate every one the entity declares.
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_transactionCode` ON `transactions` (`transactionCode`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_timestamp` ON `transactions` (`timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_type` ON `transactions` (`type`)")
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

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Both changes are additive — no table rebuild, no FK risk.
            db.execSQL(
                "ALTER TABLE `categories` ADD COLUMN `isTransfer` INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE `transactions` ADD COLUMN `fulizaLimit` REAL"
            )
            // Seed the transfer category for existing installs. New installs
            // get it from DefaultData via the onCreate callback.
            db.execSQL(
                """
                INSERT OR IGNORE INTO `categories` (`id`, `name`, `icon`, `color`, `isDefault`, `isTransfer`)
                VALUES (14, 'My Accounts', 'swap_horiz', '#78909C', 1, 1)
                """.trimIndent()
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Additive nullable column — no table rebuild, no FK risk. Stores
            // the CarTag enum name ("FUEL" / "SERVICE"); NULL for the vast
            // majority of rows that aren't car expenses.
            db.execSQL(
                "ALTER TABLE `transactions` ADD COLUMN `carTag` TEXT"
            )
        }
    }
}
