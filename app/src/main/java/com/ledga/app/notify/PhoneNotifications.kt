package com.ledga.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ledga.app.R

/** One notification as Ledga hands it to Android. [id] is stable per alert key. */
data class PhoneNotification(val id: Int, val channel: String, val title: String, val body: String, val opened: OpenedNotification)

/** Android's notification shade (spec §11). Tests use a fake. */
interface PhoneNotifications {
    /** R109: Android lets Ledga post on [channel]: its own switch, Android 13's permission, and the channel's switch. */
    fun allowed(channel: String): Boolean

    fun post(n: PhoneNotification)
}

class AndroidPhoneNotifications(private val context: Context) : PhoneNotifications {
    private val manager: NotificationManagerCompat get() = NotificationManagerCompat.from(context)

    override fun allowed(channel: String): Boolean =
        manager.areNotificationsEnabled() && permitted() &&
            manager.getNotificationChannelCompat(channel)?.importance != NotificationManagerCompat.IMPORTANCE_NONE

    @SuppressLint("MissingPermission") // checked just before, in permitted()
    override fun post(n: PhoneNotification) {
        if (!permitted()) return
        val tap = PendingIntent.getActivity(
            context,
            n.id,
            NotificationIntents.intent(context, n.opened),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, n.channel)
            .setSmallIcon(R.drawable.ic_stat_ledga)
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        manager.notify(n.id, notification)
    }

    private fun permitted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
