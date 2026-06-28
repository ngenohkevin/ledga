package com.ledga.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
        // Tapping opens the app. Without a content intent the tap does nothing
        // and setAutoCancel never fires, so the notification just sits in the
        // shade until manually swiped — the "can't mark it read" complaint.
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission not granted
        }
    }
}
