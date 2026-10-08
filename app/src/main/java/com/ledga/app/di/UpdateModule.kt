package com.ledga.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.WorkManager
import com.ledga.app.BuildConfig
import com.ledga.app.data.update.AndroidApkInspector
import com.ledga.app.data.update.AndroidUpdateNotices
import com.ledga.app.data.update.ApkInspector
import com.ledga.app.data.update.AssetNotes
import com.ledga.app.data.update.BundledNotes
import com.ledga.app.data.update.InstallEvents
import com.ledga.app.data.update.PackageInstallerUpdates
import com.ledga.app.data.update.UpdateEndpoints
import com.ledga.app.data.update.UpdateFiles
import com.ledga.app.data.update.UpdateHttp
import com.ledga.app.data.update.UpdateInstaller
import com.ledga.app.data.update.UpdateNotices
import com.ledga.app.data.update.UpdateService
import com.ledga.app.data.update.UpdateStore
import com.ledga.app.data.update.UrlUpdateHttp
import com.ledga.app.data.update.WhatsNew
import com.ledga.app.work.UpdateWork
import com.ledga.app.work.WorkManagerUpdateWork
import com.ledga.core.update.AppVersion
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

/** The update service's DataStore, apart from the settings one (which is unqualified). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UpdatePreferences

/** Spec §13.4: the update service and what it needs. */
@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {
    /** R133: its own file, never v1's settings file. */
    @Provides
    @Singleton
    @UpdatePreferences
    fun updatesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(UpdateStore.FILE_NAME) }

    @Provides
    @Singleton
    fun updateStore(@UpdatePreferences store: DataStore<Preferences>): UpdateStore = UpdateStore(store)

    /** R139: out of Android backup. */
    @Provides
    @Singleton
    fun updateFiles(@ApplicationContext context: Context): UpdateFiles = UpdateFiles(File(context.noBackupFilesDir, "updates"))

    /** Spec §13.4: GitHub asks every caller to name itself. */
    @Provides
    @Singleton
    fun updateHttp(): UpdateHttp = UrlUpdateHttp("Ledga/${BuildConfig.VERSION_NAME} (+https://github.com/ngenohkevin/ledga)")

    @Provides
    fun apkInspector(@ApplicationContext context: Context): ApkInspector = AndroidApkInspector(context.packageManager)

    @Provides
    @Singleton
    fun updateNotices(@ApplicationContext context: Context): UpdateNotices = AndroidUpdateNotices(context)

    @Provides
    @Singleton
    fun updateInstaller(@ApplicationContext context: Context, events: InstallEvents): UpdateInstaller = PackageInstallerUpdates(context, events)

    @Provides
    @Singleton
    fun updateWork(wm: WorkManager): UpdateWork = WorkManagerUpdateWork(wm)

    /** The installed version comes from the build, which refuses a versionName that isn't one (R132). */
    @Provides
    @Singleton
    fun updateService(
        store: UpdateStore,
        http: UpdateHttp,
        endpoints: UpdateEndpoints,
        files: UpdateFiles,
        work: UpdateWork,
        installer: UpdateInstaller,
        notices: UpdateNotices,
        events: InstallEvents,
        clock: Clock,
    ): UpdateService = UpdateService(
        store, http, endpoints, files, work, installer, notices, events, checkNotNull(AppVersion.ofBuild(BuildConfig.VERSION_NAME)), clock,
    )

    @Provides
    fun bundledNotes(@ApplicationContext context: Context): BundledNotes = AssetNotes(context.assets)

    @Provides
    @Singleton
    fun whatsNew(store: UpdateStore, notes: BundledNotes, updates: UpdateService): WhatsNew = WhatsNew(store, notes, updates.installed)
}
