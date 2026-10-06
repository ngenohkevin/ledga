package com.ledga.app.di

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.WorkManager
import com.ledga.app.data.lines.PhoneAccess
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.data.settings.V1SettingsMigration
import com.ledga.app.startup.SmsAccess
import com.ledga.app.time.LiveClock
import com.ledga.app.ui.onboarding.NotificationAccess
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** v1's own settings file, read through the v2 keys (R15/R30). The only DataStore on it. */
    @Provides
    @Singleton
    fun settingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(migrations = listOf(V1SettingsMigration)) {
            context.preferencesDataStoreFile(SettingsStore.FILE_NAME)
        }

    @Provides
    fun contentResolver(@ApplicationContext context: Context): ContentResolver = context.contentResolver

    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemUTC()

    /** Spec §7.6 (R34): the one live-period clock; `MainActivity` pokes it on resume. */
    @Provides
    @Singleton
    fun liveClock(clock: Clock): LiveClock = LiveClock(clock)

    @Provides
    @Singleton
    fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    fun smsAccess(@ApplicationContext context: Context): SmsAccess = SmsAccess {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    }

    @Provides
    fun notificationAccess(@ApplicationContext context: Context): NotificationAccess = NotificationAccess {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }

    @Provides
    fun phoneAccess(@ApplicationContext context: Context): PhoneAccess = PhoneAccess {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }
}
