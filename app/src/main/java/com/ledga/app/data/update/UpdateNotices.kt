package com.ledga.app.data.update

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
import com.ledga.app.notify.NotifyChannels
import com.ledga.app.notify.UpdateIntents

/** Spec §11 "Updates": a download's progress and "Update ready to install". Not logged in Alerts (R144). */
interface UpdateNotices {
    /** [percent] is null while the size isn't known. */
    fun progress(version: String, percent: Int?)

    fun clearProgress()

    fun ready(version: String)

    fun clearReady()
}

class AndroidUpdateNotices(private val context: Context) : UpdateNotices {
    private val manager: NotificationManagerCompat get() = NotificationManagerCompat.from(context)

    @SuppressLint("MissingPermission") // checked in permitted()
    override fun progress(version: String, percent: Int?) {
        if (!permitted()) return
        val n = builder()
            .setContentTitle("Downloading Ledga $version")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent ?: 0, percent == null)
            .build()
        manager.notify(PROGRESS_ID, n)
    }

    override fun clearProgress() = manager.cancel(PROGRESS_ID)

    @SuppressLint("MissingPermission") // checked in permitted()
    override fun ready(version: String) {
        if (!permitted()) return
        val n = builder()
            .setContentTitle("Update ready to install")
            .setContentText("Ledga $version has downloaded.")
            .setAutoCancel(true)
            .build()
        manager.notify(READY_ID, n)
    }

    override fun clearReady() = manager.cancel(READY_ID)

    private fun builder(): NotificationCompat.Builder = NotificationCompat.Builder(context, NotifyChannels.UPDATES)
        .setSmallIcon(R.drawable.ic_stat_ledga)
        .setContentIntent(
            PendingIntent.getActivity(context, TAP_REQUEST, UpdateIntents.intent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )

    private fun permitted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val PROGRESS_ID = 7_301
        const val READY_ID = 7_302
        private const val TAP_REQUEST = 7_300
    }
}
