package com.ledga.app.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ledga.app.ui.design.theme.Appearance
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class V1SettingsMigrationTest {

    @Test
    fun `every v1 setting moves to its v2 key`() = runTest {
        val v1 = preferencesOf(
            stringPreferencesKey("theme_mode") to "DARK",
            stringPreferencesKey("font_scale") to "LARGE",
            booleanPreferencesKey("onboarding_completed") to true,
            booleanPreferencesKey("daily_summary") to false,
            intPreferencesKey("daily_summary_hour") to 21,
            booleanPreferencesKey("weekly_summary") to false,
            booleanPreferencesKey("large_txn_alert") to true,
            doublePreferencesKey("large_txn_threshold") to 7500.5,
            longPreferencesKey("selected_account_id") to 2L,
            longPreferencesKey("sms_sync_watermark") to 1_790_000_000_000L,
        )
        assertTrue(V1SettingsMigration.shouldMigrate(v1))
        assertEquals(
            Settings(
                appearance = Appearance.DARK,
                textSize = TextSize.LARGE,
                onboarded = true,
                selectedLineId = 2L,
                notifyDaily = false,
                dailySummaryMinute = 21 * 60,
                notifyWeekly = false,
                notifyLarge = true,
                largeThresholdCents = 750_050L,
                smsWatermarkMillis = 1_790_000_000_000L,
            ),
            SettingsStore.read(V1SettingsMigration.migrate(v1)),
        )
    }

    @Test
    fun `no v1 key survives the migration, obsolete ones included`() = runTest {
        val after = V1SettingsMigration.migrate(
            preferencesOf(
                stringPreferencesKey("theme_mode") to "LIGHT",
                booleanPreferencesKey("budget_alerts") to true,
                stringPreferencesKey("dismissed_update_version") to "1.5.0",
                booleanPreferencesKey("sim_backfill_done") to true,
                intPreferencesKey("parser_fixup_version") to 1,
            ),
        )
        assertTrue(after.asMap().keys.none { it.name in V1SettingsMigration.V1_KEYS }, after.toString())
        assertFalse(V1SettingsMigration.shouldMigrate(after))
        assertEquals(Appearance.LIGHT, SettingsStore.read(after).appearance)
    }

    @Test
    fun `all lines and odd values fall back to the defaults`() = runTest {
        val s = SettingsStore.read(
            V1SettingsMigration.migrate(
                preferencesOf(
                    stringPreferencesKey("theme_mode") to "PURPLE",
                    stringPreferencesKey("font_scale") to "HUGE",
                    intPreferencesKey("daily_summary_hour") to 31,
                    doublePreferencesKey("large_txn_threshold") to -5.0,
                    longPreferencesKey("selected_account_id") to -1L,
                ),
            ),
        )
        assertEquals(Settings(), s)
        val nan = SettingsStore.read(V1SettingsMigration.migrate(preferencesOf(doublePreferencesKey("large_txn_threshold") to Double.NaN)))
        assertEquals(Settings().largeThresholdCents, nan.largeThresholdCents)
        assertNull(s.selectedLineId)
    }

    @Test
    fun `a v2 value already present wins over the v1 one`() = runTest {
        val s = SettingsStore.read(
            V1SettingsMigration.migrate(
                preferencesOf(SettingsKeys.APPEARANCE to "LIGHT", stringPreferencesKey("theme_mode") to "DARK"),
            ),
        )
        assertEquals(Appearance.LIGHT, s.appearance)
    }

    @Test
    fun `a fresh install has nothing to migrate and reads the defaults`() = runTest {
        assertFalse(V1SettingsMigration.shouldMigrate(emptyPreferences()))
        assertEquals(Settings(), SettingsStore.read(emptyPreferences()))
    }
}
