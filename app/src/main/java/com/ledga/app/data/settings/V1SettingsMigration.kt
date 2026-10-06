package com.ledga.app.data.settings

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ledga.app.ui.design.theme.Appearance
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Spec §8 step 4, refinements R15/R30. v1's settings live in the same DataStore file (`ledga_settings`). DataStore
 * runs this once, before the first read: each value moves to its v2 key and every v1 key is removed, the obsolete
 * ones too (budget alerts, the dismissed update, the SIM-backfill and parser-fixup markers). A v2 key that already
 * exists wins. A value v1 could not have meant (an unknown theme, a negative threshold) is dropped: the default applies.
 */
object V1SettingsMigration : DataMigration<Preferences> {
    private val THEME_MODE = stringPreferencesKey("theme_mode")
    private val FONT_SCALE = stringPreferencesKey("font_scale")
    private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    private val DAILY_SUMMARY = booleanPreferencesKey("daily_summary")
    private val DAILY_SUMMARY_HOUR = intPreferencesKey("daily_summary_hour")
    private val WEEKLY_SUMMARY = booleanPreferencesKey("weekly_summary")
    private val LARGE_TXN_ALERT = booleanPreferencesKey("large_txn_alert")
    private val LARGE_TXN_THRESHOLD = doublePreferencesKey("large_txn_threshold")
    private val SELECTED_ACCOUNT_ID = longPreferencesKey("selected_account_id")
    private val SMS_SYNC_WATERMARK = longPreferencesKey("sms_sync_watermark")

    /** Every key v1.x ever wrote. */
    val V1_KEYS: Set<String> = setOf(
        "theme_mode", "font_scale", "onboarding_completed", "daily_summary", "daily_summary_hour", "weekly_summary",
        "budget_alerts", "large_txn_alert", "large_txn_threshold", "selected_account_id", "dismissed_update_version",
        "sim_backfill_done", "parser_fixup_version", "sms_sync_watermark",
    )

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData.asMap().keys.any { it.name in V1_KEYS }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val out = currentData.toMutablePreferences()
        fun <T : Any> carry(key: Preferences.Key<T>, value: T?) {
            if (value != null && !out.contains(key)) out[key] = value
        }
        carry(SettingsKeys.APPEARANCE, currentData[THEME_MODE]?.let(::appearanceOf)?.name)
        carry(SettingsKeys.TEXT_SIZE, currentData[FONT_SCALE]?.let { v -> TextSize.entries.firstOrNull { it.name == v } }?.name)
        carry(SettingsKeys.ONBOARDED, currentData[ONBOARDING_COMPLETED])
        carry(SettingsKeys.NOTIFY_DAILY, currentData[DAILY_SUMMARY])
        carry(SettingsKeys.DAILY_MINUTE, currentData[DAILY_SUMMARY_HOUR]?.takeIf { it in 0..23 }?.let { it * 60 })
        carry(SettingsKeys.NOTIFY_WEEKLY, currentData[WEEKLY_SUMMARY])
        carry(SettingsKeys.NOTIFY_LARGE, currentData[LARGE_TXN_ALERT])
        carry(SettingsKeys.LARGE_CENTS, currentData[LARGE_TXN_THRESHOLD]?.let(::cents))
        carry(SettingsKeys.SELECTED_LINE, currentData[SELECTED_ACCOUNT_ID]?.takeIf { it > 0 }) // -1 meant "all lines"
        carry(SettingsKeys.SMS_WATERMARK, currentData[SMS_SYNC_WATERMARK]?.takeIf { it > 0 })
        for (key in currentData.asMap().keys) {
            @Suppress("UNCHECKED_CAST")
            if (key.name in V1_KEYS) out.remove(key as Preferences.Key<Any>)
        }
        return out.toPreferences()
    }

    override suspend fun cleanUp() = Unit

    private fun appearanceOf(v1: String): Appearance = when (v1) {
        "LIGHT" -> Appearance.LIGHT
        "DARK" -> Appearance.DARK
        else -> Appearance.SYSTEM
    }

    /** v1 stored shillings as a Double; null for anything that isn't a positive, finite, sane amount. */
    private fun cents(shillings: Double): Long? =
        if (!shillings.isFinite() || shillings <= 0.0 || shillings > 1e12) {
            null
        } else {
            BigDecimal.valueOf(shillings).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        }
}
