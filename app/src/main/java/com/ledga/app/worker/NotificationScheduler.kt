package com.ledga.app.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    fun scheduleDailySummary(context: Context, hour: Int = 20) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val initialDelay = target.timeInMillis - now.timeInMillis

        val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "daily_summary",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelDailySummary(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("daily_summary")
    }

    fun scheduleWeeklySummary(context: Context) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.WEEK_OF_YEAR, 1)
        }
        val initialDelay = target.timeInMillis - now.timeInMillis

        val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "weekly_summary",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelWeeklySummary(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("weekly_summary")
    }
}
