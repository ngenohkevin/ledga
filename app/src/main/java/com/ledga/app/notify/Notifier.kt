package com.ledga.app.notify

import android.util.Log
import com.ledga.app.data.alerts.AlertType
import com.ledga.app.data.room.AlertRow
import com.ledga.app.data.room.LedgaDatabase
import java.time.Clock
import java.time.Duration

/** One alert (spec §11): its dedupe key (spec §7.1), what it says, its payment and what its tap opens. */
data class Alert(val key: String, val type: AlertType, val title: String, val body: String, val targetCode: String?, val tap: NotificationTap)

/**
 * The one writer of `alerts` (spec §7.1, §11). An alert is logged under its key first, then shown on the phone when
 * Android allows; a key already logged posts nothing, ever. An alert Android blocks is still logged (R101): the bell
 * and Alerts are Ledga's own record.
 */
class Notifier(private val db: LedgaDatabase, private val phone: PhoneNotifications, private val clock: Clock) {

    /** False when [alert]'s key was already logged (nothing is shown again). */
    suspend fun send(alert: Alert): Boolean {
        val row = AlertRow(alert.key, alert.type.name, alert.title, alert.body, alert.targetCode, clock.instant(), readAt = null)
        if (db.alertsDao().insertIgnore(row) == -1L) return false
        val channel = NotifyChannels.of(alert.type)
        if (phone.allowed(channel)) {
            try {
                phone.post(PhoneNotification(idOf(alert.key), channel, alert.title, alert.body, OpenedNotification(alert.key, alert.tap)))
            } catch (e: RuntimeException) {
                Log.w(TAG, "could not show an alert; it stays in Alerts", e)
            }
        }
        return true
    }

    /** Spec §7.1: alerts are kept 60 days. */
    suspend fun prune(): Int = db.alertsDao().deleteOlderThan(clock.instant().minus(KEEP))

    companion object {
        val KEEP: Duration = Duration.ofDays(60)
        private const val TAG = "Ledga"

        /** Stable per key: a notification shown again (Android restoring it) replaces itself. */
        fun idOf(key: String): Int = key.hashCode()
    }
}
