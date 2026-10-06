package com.ledga.app.ui.alerts

import com.ledga.app.data.alerts.AlertType
import com.ledga.app.ui.home.HomeText
import java.time.Instant
import java.time.LocalDate

/** What Alerts and Home's bell say (R71). */
object AlertText {
    fun icon(type: AlertType): String = when (type) {
        AlertType.LARGE -> "fluent_dollar_banknote"
        AlertType.FULIZA_DRAW, AlertType.FULIZA_DUE -> "fluent_credit_card"
        AlertType.DAILY, AlertType.WEEKLY -> "fluent_bar_chart"
        AlertType.OTHER -> "fluent_bell"
    }

    /** "7:42 PM" today, then "Yesterday", "2 Oct", "30 Oct 2025": Home's Recent wording. */
    fun time(at: Instant, today: LocalDate): String = HomeText.rowTime(at, today)

    fun speech(a: AlertUi, today: LocalDate): String =
        (if (a.isNew) "New. " else "") + "${a.title.trimEnd('.')}. ${a.body.trimEnd('.')}. ${time(a.at, today)}"

    fun badge(unread: Int): String? = when {
        unread <= 0 -> null
        unread > 9 -> "9+"
        else -> unread.toString()
    }

    fun bellLabel(unread: Int): String = if (unread > 0) "Alerts, $unread unread" else "Alerts"
}
