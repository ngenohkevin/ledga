package com.ledga.app.notify

import android.content.Context
import android.content.Intent
import com.ledga.app.MainActivity

/** R144: a tap on an update notification opens You → Updates. It carries no alert: update notices aren't in Alerts. */
object UpdateIntents {
    private const val OPEN = "com.ledga.app.open"
    private const val UPDATES = "updates"

    fun intent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(OPEN, UPDATES)

    fun read(intent: Intent?): Boolean = intent?.getStringExtra(OPEN) == UPDATES

    /** Once handled, a rotation or a restart must open nothing. */
    fun clear(intent: Intent) = intent.removeExtra(OPEN)
}
