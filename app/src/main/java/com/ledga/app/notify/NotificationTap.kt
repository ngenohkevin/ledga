package com.ledga.app.notify

import android.content.Context
import android.content.Intent
import com.ledga.app.MainActivity
import java.time.LocalDate

/** What a tapped notification opens (R100, owner 2026-10-07: the related screen). */
sealed interface NotificationTap {
    /** A large payment or a Fuliza draw: that payment, over Alerts. */
    data class Payment(val code: String) : NotificationTap

    /** A Fuliza reminder: Home's Fuliza sheet. */
    data object Fuliza : NotificationTap

    /** A summary: Activity's payments on those Nairobi days, [from] to [to], both included. */
    data class Spending(val from: LocalDate, val to: LocalDate) : NotificationTap
}

/** A tapped notification: the alert it showed ([alertKey], marked read by the tap) and what it opens. */
data class OpenedNotification(val alertKey: String, val tap: NotificationTap)

/** Puts an [OpenedNotification] on the intent a notification launches, and reads it back in `MainActivity`. */
object NotificationIntents {
    private const val ALERT = "com.ledga.app.alert"
    private const val TAP = "com.ledga.app.tap"
    private const val CODE = "com.ledga.app.code"
    private const val FROM = "com.ledga.app.from"
    private const val TO = "com.ledga.app.to"
    private val KEYS = listOf(ALERT, TAP, CODE, FROM, TO)

    /** Starts Ledga, or reaches the one already open (`onNewIntent`), on its current screen. */
    fun intent(context: Context, opened: OpenedNotification): Intent = put(
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        opened,
    )

    fun put(intent: Intent, opened: OpenedNotification): Intent = intent.apply {
        putExtra(ALERT, opened.alertKey)
        when (val tap = opened.tap) {
            is NotificationTap.Payment -> putExtra(TAP, "payment").putExtra(CODE, tap.code)
            NotificationTap.Fuliza -> putExtra(TAP, "fuliza")
            is NotificationTap.Spending -> putExtra(TAP, "spending").putExtra(FROM, tap.from.toString()).putExtra(TO, tap.to.toString())
        }
    }

    /** Null for any other launch, and for an intent [clear] has already emptied. */
    fun read(intent: Intent?): OpenedNotification? {
        if (intent == null) return null
        val key = intent.getStringExtra(ALERT) ?: return null
        val tap = when (intent.getStringExtra(TAP)) {
            "payment" -> NotificationTap.Payment(intent.getStringExtra(CODE) ?: return null)
            "fuliza" -> NotificationTap.Fuliza
            "spending" -> NotificationTap.Spending(date(intent.getStringExtra(FROM)) ?: return null, date(intent.getStringExtra(TO)) ?: return null)
            else -> return null
        }
        return OpenedNotification(key, tap)
    }

    /** A rotation or a restart reads the launch intent again: once handled, it must open nothing. */
    fun clear(intent: Intent) = KEYS.forEach(intent::removeExtra)

    private fun date(text: String?): LocalDate? = text?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
