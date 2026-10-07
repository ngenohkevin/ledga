package com.ledga.app.data.backup

import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.settings.SettingsStore
import com.ledga.core.derive.RuleOrigin
import com.ledga.core.model.Categories
import java.time.Clock

/** The database and the portable settings as one [BackupData] (R118). */
class BackupReader(
    private val db: LedgaDatabase,
    private val settings: SettingsStore,
    private val device: DeviceId,
    private val appVersion: String,
    private val clock: Clock,
) {
    suspend fun read(): BackupData {
        val sms = buildList {
            var after = 0L
            while (true) {
                val page = db.smsDao().pageAfter(after, PAGE)
                if (page.isEmpty()) break
                page.mapTo(this) { SmsEntry(it.sender, it.body, it.receivedAt.toEpochMilli(), it.subscriptionId, it.lineId, it.source.name) }
                after = page.last().id
            }
        }
        val rules = db.rulesDao().all()
        return BackupData(
            writtenAt = clock.millis(),
            appVersion = appVersion,
            device = runCatching { device.value() }.getOrNull(),
            counts = BackupCounts(sms.size, db.transactionsDao().countShown()),
            sms = sms,
            overrides = db.overridesDao().all().map {
                OverrideEntry(it.code, it.categoryKey, it.note, it.lineId, it.ownAccount, it.hidden, it.updatedAt.toEpochMilli())
            },
            rules = rules.filter { it.origin == RuleOrigin.USER }.map {
                RuleEntry(it.field.name, it.pattern, it.action.name, it.categoryKey, it.priority, it.createdAt.toEpochMilli(), it.enabled)
            },
            systemRulesOff = rules.filter { it.origin == RuleOrigin.SYSTEM && !it.enabled }.map {
                RuleKey(it.field.name, it.pattern, it.action.name, it.categoryKey)
            },
            categories = db.categoriesDao().all().filter { it.origin == CategoryOrigin.USER || changedFromSeed(it) }.map {
                CategoryEntry(it.key, it.name, it.groupKey.name, it.icon3d, it.color, it.colorDark, it.tracked, it.sortOrder, it.origin.name, it.archived)
            },
            lines = db.linesDao().all().map {
                LineEntry(it.id, it.subscriptionId, it.phoneNumber, it.displayName, it.color, it.isPrimary, it.createdAt.toEpochMilli())
            },
            settings = PortableSettings.of(settings.current()),
        )
    }

    companion object {
        private const val PAGE = 1000

        /** R118: a built-in category the person renamed, re-iconed, re-coloured or (un)tracked. */
        fun changedFromSeed(c: CategoryRow): Boolean {
            val seed = Categories.seed(c.key) ?: return true
            return c.name != seed.name || c.icon3d != seed.icon3d || c.color != null || c.colorDark != null || c.tracked != seed.tracked
        }
    }
}
