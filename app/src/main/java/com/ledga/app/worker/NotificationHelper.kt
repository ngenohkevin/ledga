package com.ledga.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    const val CHANNEL_SUMMARIES = "spending_summaries"
    const val CHANNEL_BUDGET = "budget_alerts"
    const val CHANNEL_LARGE_TXN = "large_transactions"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val summaries = NotificationChannel(
            CHANNEL_SUMMARIES,
            "Spending Summaries",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily and weekly spending summaries"
        }

        val budget = NotificationChannel(
            CHANNEL_BUDGET,
            "Budget Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when budget limits are reached"
        }

        val largeTxn = NotificationChannel(
            CHANNEL_LARGE_TXN,
            "Large Transactions",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts for large transactions"
        }

        manager.createNotificationChannels(listOf(summaries, budget, largeTxn))
    }

    fun showNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String
    ) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission not granted
        }
    }
}
