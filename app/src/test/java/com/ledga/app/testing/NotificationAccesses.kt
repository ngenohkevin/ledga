package com.ledga.app.testing

import com.ledga.app.ui.onboarding.NotificationAccess

/** R109: Android 8–12 (or a person who switched them off) — notifications off, and no dialog that could ask. */
val OFF_IN_SETTINGS = object : NotificationAccess {
    override fun shouldAsk(): Boolean = false

    override fun enabled(): Boolean = false
}
