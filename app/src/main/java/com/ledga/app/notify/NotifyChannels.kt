package com.ledga.app.notify

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.ledga.app.data.alerts.AlertType

/**
 * Ledga's notification channels (spec §11, R102). Summaries and Large payments keep v1's ids, so an upgraded phone keeps
 * the person's Android settings for them; Fuliza is new; v1's budget channel goes with budgets (spec §2). Updates
 * (Phase 6) adds its own. All at default importance, as v1's were.
 */
object NotifyChannels {
    const val SUMMARIES = "spending_summaries"
    const val LARGE = "large_transactions"
    const val FULIZA = "fuliza"
    const val V1_BUDGET = "budget_alerts"

    fun of(type: AlertType): String = when (type) {
        AlertType.LARGE -> LARGE
        AlertType.FULIZA_DRAW, AlertType.FULIZA_DUE -> FULIZA
        AlertType.DAILY, AlertType.WEEKLY, AlertType.OTHER -> SUMMARIES
    }

    /** Every start (`LedgaApp`): creates the channels, renames v1's, and deletes v1's budget channel. Idempotent. */
    fun ensure(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannelsCompat(
            listOf(
                channel(SUMMARIES, "Summaries", "What you spent each day and each week"),
                channel(LARGE, "Large payments", "When a payment of your chosen amount or more goes out"),
                channel(FULIZA, "Fuliza", "When Fuliza covers a payment, and before it's due"),
            ),
        )
        manager.deleteNotificationChannel(V1_BUDGET)
    }

    private fun channel(id: String, name: String, description: String): NotificationChannelCompat =
        NotificationChannelCompat.Builder(id, NotificationManagerCompat.IMPORTANCE_DEFAULT).setName(name).setDescription(description).build()
}
