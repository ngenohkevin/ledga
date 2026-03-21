package com.ledga.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ledga.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val FONT_SCALE = stringPreferencesKey("font_scale")
        private val DAILY_SUMMARY = booleanPreferencesKey("daily_summary")
        private val DAILY_SUMMARY_HOUR = intPreferencesKey("daily_summary_hour")
        private val WEEKLY_SUMMARY = booleanPreferencesKey("weekly_summary")
        private val BUDGET_ALERTS = booleanPreferencesKey("budget_alerts")
        private val LARGE_TXN_ALERT = booleanPreferencesKey("large_txn_alert")
        private val LARGE_TXN_THRESHOLD = doublePreferencesKey("large_txn_threshold")
    }

    fun getThemeMode(): Flow<ThemeMode> = dataStore.data.map { prefs ->
        when (prefs[THEME_MODE]) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    fun hasCompletedOnboarding(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setOnboardingCompleted() {
        dataStore.edit { it[ONBOARDING_COMPLETED] = true }
    }

    // --- Notification settings ---

    fun getDailySummaryEnabled(): Flow<Boolean> = dataStore.data.map { it[DAILY_SUMMARY] ?: true }
    suspend fun setDailySummaryEnabled(enabled: Boolean) { dataStore.edit { it[DAILY_SUMMARY] = enabled } }

    fun getDailySummaryHour(): Flow<Int> = dataStore.data.map { it[DAILY_SUMMARY_HOUR] ?: 20 } // 8 PM default
    suspend fun setDailySummaryHour(hour: Int) { dataStore.edit { it[DAILY_SUMMARY_HOUR] = hour } }

    fun getWeeklySummaryEnabled(): Flow<Boolean> = dataStore.data.map { it[WEEKLY_SUMMARY] ?: true }
    suspend fun setWeeklySummaryEnabled(enabled: Boolean) { dataStore.edit { it[WEEKLY_SUMMARY] = enabled } }

    fun getBudgetAlertsEnabled(): Flow<Boolean> = dataStore.data.map { it[BUDGET_ALERTS] ?: true }
    suspend fun setBudgetAlertsEnabled(enabled: Boolean) { dataStore.edit { it[BUDGET_ALERTS] = enabled } }

    fun getLargeTransactionAlertEnabled(): Flow<Boolean> = dataStore.data.map { it[LARGE_TXN_ALERT] ?: false }
    suspend fun setLargeTransactionAlertEnabled(enabled: Boolean) { dataStore.edit { it[LARGE_TXN_ALERT] = enabled } }

    fun getLargeTransactionThreshold(): Flow<Double> = dataStore.data.map { it[LARGE_TXN_THRESHOLD] ?: 5000.0 }
    suspend fun setLargeTransactionThreshold(amount: Double) { dataStore.edit { it[LARGE_TXN_THRESHOLD] = amount } }

    // --- Font scale ---

    fun getFontScale(): Flow<FontScale> = dataStore.data.map { prefs ->
        when (prefs[FONT_SCALE]) {
            "SMALL" -> FontScale.SMALL
            "MEDIUM" -> FontScale.MEDIUM
            "LARGE" -> FontScale.LARGE
            "EXTRA_LARGE" -> FontScale.EXTRA_LARGE
            else -> FontScale.SYSTEM
        }
    }

    suspend fun setFontScale(scale: FontScale) {
        dataStore.edit { it[FONT_SCALE] = scale.name }
    }
}

enum class FontScale(val label: String, val scale: Float) {
    SYSTEM("System Default", 1.0f),
    SMALL("Small", 0.85f),
    MEDIUM("Medium", 1.0f),
    LARGE("Large", 1.15f),
    EXTRA_LARGE("Extra Large", 1.3f)
}
