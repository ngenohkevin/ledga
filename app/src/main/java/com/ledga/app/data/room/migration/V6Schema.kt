package com.ledga.app.data.room.migration

import com.ledga.app.data.room.LedgerView

/** The schema-6 DDL exactly as Room generates it (guarded by V6SchemaTest against schemas/.../6.json). */
object V6Schema {
    val CREATE: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `lines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `subscriptionId` INTEGER, `phoneNumber` TEXT, `displayName` TEXT NOT NULL, `color` TEXT NOT NULL, `isPrimary` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_lines_subscriptionId` ON `lines` (`subscriptionId`)",
        "CREATE TABLE IF NOT EXISTS `sms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sender` TEXT NOT NULL, `body` TEXT NOT NULL, `bodyHash` TEXT NOT NULL, `receivedAt` INTEGER NOT NULL, `subscriptionId` INTEGER, `lineId` INTEGER, `code` TEXT, `source` TEXT NOT NULL, `status` TEXT NOT NULL, `statusReason` TEXT, `parserVersion` INTEGER NOT NULL, FOREIGN KEY(`lineId`) REFERENCES `lines`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_sms_bodyHash` ON `sms` (`bodyHash`)",
        "CREATE INDEX IF NOT EXISTS `index_sms_code` ON `sms` (`code`)",
        "CREATE INDEX IF NOT EXISTS `index_sms_lineId` ON `sms` (`lineId`)",
        "CREATE TABLE IF NOT EXISTS `transactions` (`code` TEXT NOT NULL, `lineId` INTEGER, `occurredAt` INTEGER NOT NULL, `occurredAtApprox` INTEGER NOT NULL, `kind` TEXT NOT NULL, `flow` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `feeCents` INTEGER NOT NULL, `balanceCents` INTEGER, `counterpartyName` TEXT, `counterpartyPhone` TEXT, `counterpartyAccount` TEXT, `counterpartyKey` TEXT, `destinationCountry` TEXT, `reversesCode` TEXT, `isReversed` INTEGER NOT NULL, `fulizaDrawnCents` INTEGER, `fulizaFeeCents` INTEGER, `fulizaOutstandingCents` INTEGER, `fulizaLimitCents` INTEGER, `fulizaDueDate` TEXT, `categoryKey` TEXT NOT NULL, `note` TEXT, `isHidden` INTEGER NOT NULL, `searchText` TEXT NOT NULL, `smsCount` INTEGER NOT NULL, PRIMARY KEY(`code`))",
        "CREATE INDEX IF NOT EXISTS `index_transactions_occurredAt` ON `transactions` (`occurredAt`)",
        "CREATE INDEX IF NOT EXISTS `index_transactions_flow_occurredAt` ON `transactions` (`flow`, `occurredAt`)",
        "CREATE INDEX IF NOT EXISTS `index_transactions_categoryKey_occurredAt` ON `transactions` (`categoryKey`, `occurredAt`)",
        "CREATE INDEX IF NOT EXISTS `index_transactions_lineId` ON `transactions` (`lineId`)",
        "CREATE INDEX IF NOT EXISTS `index_transactions_counterpartyKey` ON `transactions` (`counterpartyKey`)",
        "CREATE INDEX IF NOT EXISTS `index_transactions_reversesCode` ON `transactions` (`reversesCode`)",
        "CREATE TABLE IF NOT EXISTS `overrides` (`code` TEXT NOT NULL, `categoryKey` TEXT, `note` TEXT, `lineId` INTEGER, `ownAccount` INTEGER, `hidden` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`code`))",
        "CREATE TABLE IF NOT EXISTS `rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `field` TEXT NOT NULL, `pattern` TEXT NOT NULL, `action` TEXT NOT NULL, `categoryKey` TEXT, `origin` TEXT NOT NULL, `priority` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `enabled` INTEGER NOT NULL DEFAULT 1)",
        "CREATE TABLE IF NOT EXISTS `categories` (`key` TEXT NOT NULL, `name` TEXT NOT NULL, `groupKey` TEXT NOT NULL, `icon3d` TEXT NOT NULL, `color` TEXT, `colorDark` TEXT, `tracked` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `origin` TEXT NOT NULL, `archived` INTEGER NOT NULL, PRIMARY KEY(`key`))",
        "CREATE TABLE IF NOT EXISTS `alerts` (`key` TEXT NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `targetCode` TEXT, `createdAt` INTEGER NOT NULL, `readAt` INTEGER, PRIMARY KEY(`key`))",
        "CREATE INDEX IF NOT EXISTS `index_alerts_createdAt` ON `alerts` (`createdAt`)",
        "CREATE TABLE IF NOT EXISTS `meta` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))",
        "CREATE VIEW `ledger` AS ${LedgerView.QUERY}",
    )
}
