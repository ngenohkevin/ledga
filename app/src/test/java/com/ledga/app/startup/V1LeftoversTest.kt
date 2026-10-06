package com.ledga.app.startup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.ledga.app.work.RebuildWorker
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
class V1LeftoversTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `v1's periodic jobs are cancelled and its update preferences deleted`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        val wm = WorkManager.getInstance(context)
        val never = Constraints.Builder().setRequiresCharging(true).build() // keeps them queued, as on a phone between runs
        V1Leftovers.JOBS.forEach {
            wm.enqueueUniquePeriodicWork(it, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<RebuildWorker>(1, TimeUnit.DAYS).setConstraints(never).build())
        }
        context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE).edit().putLong("last_check", 1L).commit()

        V1Leftovers(context, wm).invoke()

        V1Leftovers.JOBS.forEach { assertEquals(WorkInfo.State.CANCELLED, wm.getWorkInfosForUniqueWork(it).get().single().state, it) }
        assertFalse(context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE).contains("last_check"))
    }
}
