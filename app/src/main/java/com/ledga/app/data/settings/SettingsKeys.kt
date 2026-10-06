package com.ledga.app.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** v2's DataStore keys. None reuses a v1 name, so a half-migrated file can never be misread. */
internal object SettingsKeys {
    val APPEARANCE = stringPreferencesKey("appearance")
    val TEXT_SIZE = stringPreferencesKey("text_size")
    val DISPLAY_NAME = stringPreferencesKey("display_name")
    val ONBOARDED = booleanPreferencesKey("onboarded")
    val SELECTED_LINE = longPreferencesKey("selected_line")
    val NOTIFY_DAILY = booleanPreferencesKey("notify_daily")
    val DAILY_MINUTE = intPreferencesKey("notify_daily_minute")
    val NOTIFY_WEEKLY = booleanPreferencesKey("notify_weekly")
    val NOTIFY_LARGE = booleanPreferencesKey("notify_large")
    val LARGE_CENTS = longPreferencesKey("notify_large_cents")
    val NOTIFY_FULIZA = booleanPreferencesKey("notify_fuliza")
    val SMS_WATERMARK = longPreferencesKey("sms_watermark")
    val FULL_RESCAN_OWED = booleanPreferencesKey("full_rescan_owed")
    val NOTIFICATION_NUDGE_DISMISSED = booleanPreferencesKey("notification_nudge_dismissed")
}
