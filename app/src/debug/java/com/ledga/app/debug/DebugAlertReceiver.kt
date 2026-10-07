package com.ledga.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.sqlite.db.SimpleSQLiteQuery
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.notify.AlertWords
import com.ledga.app.notify.FulizaDue
import com.ledga.app.notify.Notifier
import com.ledga.app.notify.SummaryAlerts
import com.ledga.core.model.FlowKind
import com.ledga.core.time.Periods
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Ledga dev only (R113). `adb shell am broadcast -a com.ledga.app.DEBUG_ALERT -n com.ledga.app.dev/com.ledga.app.debug.DebugAlertReceiver --es kind <kind>`
 * posts one sample alert built from this phone's own newest payments, through the real `Notifier`, channel and tap:
 * kinds large, draw, due, daily, weekly. Keys start "debug-", so no real alert's key is used up; `clear` removes them.
 */
@AndroidEntryPoint
class DebugAlertReceiver : BroadcastReceiver() {
    @Inject lateinit var db: LedgaDatabase
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var summaries: SummaryAlerts
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra("kind") ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (kind == "clear") clear(context) else sample(kind)
            } catch (e: Exception) {
                Log.w(TAG, "debug alert $kind failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun sample(kind: String) {
        val now = clock.instant()
        val today = Periods.dateOf(now)
        val recent = db.transactionsDao().recent(lineId = null, categoryKey = null, limit = 500).first()
        val alert = when (kind) {
            "large" -> recent.firstOrNull { it.flow == FlowKind.SPEND }?.let { AlertWords.large(it, db.categoriesDao().get(it.categoryKey)?.name) }
            "draw" -> recent.firstOrNull { (it.fulizaDrawnCents ?: 0L) > 0L }?.let(AlertWords::fulizaDraw)
            "due" -> recent.firstOrNull { (it.fulizaOutstandingCents ?: 0L) > 0L && it.fulizaDueDate != null }?.let { tx ->
                AlertWords.fulizaDue(FulizaDue(tx.lineId, tx.fulizaOutstandingCents ?: 0L, tx.fulizaDueDate ?: today, 3), null)
            }
            "daily" -> (0L..30L).asSequence().map { today.minusDays(it) }.firstNotNullOfOrNull { summaries.dailyAlert(it, today) }
            "weekly" -> summaries.weeklyAlert(now)
            else -> null
        }
        if (alert == null) {
            Log.w(TAG, "no sample for $kind")
            return
        }
        notifier.send(alert.copy(key = "debug-$kind-${now.toEpochMilli()}"))
    }

    private fun clear(context: Context) {
        val keys = buildList {
            db.query(SimpleSQLiteQuery("SELECT `key` FROM alerts WHERE `key` LIKE 'debug-%'")).use { c -> while (c.moveToNext()) add(c.getString(0)) }
        }
        keys.forEach { NotificationManagerCompat.from(context).cancel(Notifier.idOf(it)) }
        // Through Room's transaction, so Alerts and the bell see the change.
        db.runInTransaction(Runnable { db.openHelper.writableDatabase.execSQL("DELETE FROM alerts WHERE `key` LIKE 'debug-%'") })
    }

    private companion object {
        const val TAG = "Ledga"
    }
}
