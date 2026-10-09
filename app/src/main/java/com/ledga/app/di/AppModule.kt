package com.ledga.app.di

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
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
    fun smsAccess(@ApplicationContext context: Context): SmsAccess = object : SmsAccess {
        override fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

        override fun mayBeRestricted(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return false
            // Final review M5: anything but a store install counts. Ledga's own updates leave the source unspecified, and
            // a wrong guess costs only words: the steps grant SMS whether or not Android restricted it.
            val source = runCatching { context.packageManager.getInstallSourceInfo(context.packageName).packageSource }.getOrNull()
            return source != PackageInstaller.PACKAGE_SOURCE_STORE
        }
    }

    @Provides
    fun notificationAccess(@ApplicationContext context: Context): NotificationAccess = object : NotificationAccess {
        override fun shouldAsk(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

        // R109: Android's own switch, on every version (it is also off while Android 13's permission is refused).
        override fun enabled(): Boolean = !shouldAsk() && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @Provides
    fun phoneAccess(@ApplicationContext context: Context): PhoneAccess = PhoneAccess {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }
}
