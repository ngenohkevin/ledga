package com.ledga.app.data.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ledga.app.data.room.V6Seed
import java.time.Instant

/**
 * v1.x (schema 5) -> v2 (schema 6), SQL only (spec §8 step 2). v1 user data is staged in `legacy_*` tables for
 * LegacyImporter (Kotlin, once); v1 tables are dropped children-first; v6 is created, seeded and marked for a
 * rebuild (meta versions 0). Legacy SMS get a provisional unique hash until the importer computes the real one (R10).
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Stage what v6 needs from v1 before the tables (whose names v6 reuses) go.
        db.execSQL(
            "CREATE TABLE `legacy_tx` (`code` TEXT NOT NULL PRIMARY KEY, `categoryId` INTEGER, `note` TEXT, `carTag` TEXT, " +
                "`accountId` INTEGER, `type` TEXT NOT NULL, `direction` TEXT NOT NULL, `recipientName` TEXT, `accountNumber` TEXT)",
        )
        db.execSQL(
            "INSERT INTO `legacy_tx` SELECT `transactionCode`, `categoryId`, `note`, `carTag`, `accountId`, `type`, `direction`, " +
                "`recipientName`, `accountNumber` FROM `transactions`",
        )
        db.execSQL("CREATE TABLE `legacy_rules` AS SELECT `id`, `categoryId`, `matchType`, `matchValue` FROM `category_rules`")
        db.execSQL("CREATE TABLE `legacy_categories` AS SELECT `id`, `name`, `icon`, `color`, `isDefault`, `isTransfer` FROM `categories`")
        db.execSQL(
            "CREATE TABLE `legacy_sms` AS SELECT `transactionCode` AS `code`, `rawSms` AS `body`, `timestamp` AS `receivedAt`, " +
                "`accountId` AS `lineId` FROM `transactions`",
        )
        db.execSQL(
            "CREATE TABLE `legacy_lines` AS SELECT `id`, `subscriptionId`, `phoneNumber`, `displayName`, `colorHex`, `isPrimary`, " +
                "`createdAt` FROM `mpesa_accounts`",
        )

        // 2. Drop v1, children before parents (budgets/category_rules/transactions reference categories/mpesa_accounts).
        listOf("goal_contributions", "goals", "insights", "budgets", "category_rules", "transactions", "categories", "mpesa_accounts")
            .forEach { db.execSQL("DROP TABLE IF EXISTS `$it`") }

        // 3. Create v6.
        V6Schema.CREATE.forEach(db::execSQL)

        // 4. Lines (same ids, so sms.lineId = v1 accountId stays valid), then legacy SMS (§8 step 2).
        db.execSQL(
            "INSERT INTO `lines` (`id`, `subscriptionId`, `phoneNumber`, `displayName`, `color`, `isPrimary`, `createdAt`) " +
                "SELECT `id`, `subscriptionId`, `phoneNumber`, `displayName`, `colorHex`, `isPrimary`, `createdAt` FROM `legacy_lines`",
        )
        db.execSQL(
            "INSERT INTO `sms` (`sender`, `body`, `bodyHash`, `receivedAt`, `subscriptionId`, `lineId`, `code`, `source`, `status`, " +
                "`statusReason`, `parserVersion`) SELECT 'MPESA', `body`, 'legacy:' || `code`, `receivedAt`, NULL, `lineId`, `code`, " +
                "'LEGACY', 'PARSED', NULL, 0 FROM `legacy_sms` ORDER BY `receivedAt`, `code`",
        )
        db.execSQL("DROP TABLE `legacy_sms`")
        db.execSQL("DROP TABLE `legacy_lines`")

        // 5. Seeds; versions 0 = a rebuild is needed.
        V6Seed.categories(db)
        V6Seed.systemRules(db, Instant.now())
        V6Seed.versions(db, parserVersion = 0, derivationVersion = 0)
    }
}
