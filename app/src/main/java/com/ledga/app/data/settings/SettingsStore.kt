package com.ledga.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.ledga.app.ui.design.theme.Appearance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Reads and writes [Settings] (spec §8 step 4). One DataStore over `ledga_settings`, v1's own file (R30). */
@Singleton
class SettingsStore @Inject constructor(private val store: DataStore<Preferences>) {

    /** An unreadable file reads as the defaults rather than taking the app down. */
    val settings: Flow<Settings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::read)

    suspend fun current(): Settings = settings.first()

    suspend fun setAppearance(value: Appearance) = edit { it[SettingsKeys.APPEARANCE] = value.name }

    suspend fun setTextSize(value: TextSize) = edit { it[SettingsKeys.TEXT_SIZE] = value.name }

    /** Blank clears it: the greeting then has no name. */
    suspend fun setDisplayName(value: String?) = edit {
        val name = value?.trim().orEmpty()
        if (name.isEmpty()) it.remove(SettingsKeys.DISPLAY_NAME) else it[SettingsKeys.DISPLAY_NAME] = name
    }

    suspend fun setOnboarded() = edit { it[SettingsKeys.ONBOARDED] = true }

    suspend fun setSelectedLine(id: Long?) = edit {
        if (id == null) it.remove(SettingsKeys.SELECTED_LINE) else it[SettingsKeys.SELECTED_LINE] = id
    }

    /** Never moves backwards, so a scan of older messages can't hide newer ones from the next catch-up. */
    suspend fun advanceWatermark(millis: Long) = edit {
        it[SettingsKeys.SMS_WATERMARK] = maxOf(it[SettingsKeys.SMS_WATERMARK] ?: 0L, millis)
    }

    suspend fun setFullRescanOwed(owed: Boolean) = edit { it[SettingsKeys.FULL_RESCAN_OWED] = owed }

    /** R59: Home's notifications banner is gone for good. */
    suspend fun dismissNotificationNudge() = edit { it[SettingsKeys.NOTIFICATION_NUDGE_DISMISSED] = true }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }

    companion object {
        const val FILE_NAME = "ledga_settings"

        fun read(p: Preferences): Settings {
            val d = Settings()
            return Settings(
                appearance = p[SettingsKeys.APPEARANCE]?.let { v -> Appearance.entries.firstOrNull { it.name == v } } ?: d.appearance,
                textSize = p[SettingsKeys.TEXT_SIZE]?.let { v -> TextSize.entries.firstOrNull { it.name == v } } ?: d.textSize,
                displayName = p[SettingsKeys.DISPLAY_NAME],
                onboarded = p[SettingsKeys.ONBOARDED] ?: d.onboarded,
                selectedLineId = p[SettingsKeys.SELECTED_LINE],
                notifyDaily = p[SettingsKeys.NOTIFY_DAILY] ?: d.notifyDaily,
                dailySummaryMinute = p[SettingsKeys.DAILY_MINUTE]?.takeIf { it in 0 until 24 * 60 } ?: d.dailySummaryMinute,
                notifyWeekly = p[SettingsKeys.NOTIFY_WEEKLY] ?: d.notifyWeekly,
                notifyLarge = p[SettingsKeys.NOTIFY_LARGE] ?: d.notifyLarge,
                largeThresholdCents = p[SettingsKeys.LARGE_CENTS]?.takeIf { it > 0 } ?: d.largeThresholdCents,
                notifyFuliza = p[SettingsKeys.NOTIFY_FULIZA] ?: d.notifyFuliza,
                smsWatermarkMillis = p[SettingsKeys.SMS_WATERMARK] ?: d.smsWatermarkMillis,
                fullRescanOwed = p[SettingsKeys.FULL_RESCAN_OWED] ?: d.fullRescanOwed,
                notificationNudgeDismissed = p[SettingsKeys.NOTIFICATION_NUDGE_DISMISSED] ?: d.notificationNudgeDismissed,
            )
        }
    }
}
