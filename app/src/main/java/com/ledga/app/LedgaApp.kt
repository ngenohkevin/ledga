package com.ledga.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ledga.app.data.legacy.PreV6Snapshot
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LedgaApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        // Spec §8 step 1: copy a v1 database aside before anything in this process (activity, receiver or worker) can
        // open it. After the upgrade this is a 100-byte header read (R29). A failure must not stop the app.
        try {
            PreV6Snapshot(this).takeIfNeeded()
        } catch (e: Exception) {
            Log.w("Ledga", "pre-v6 snapshot failed", e)
        }
        super.onCreate()
    }
}
