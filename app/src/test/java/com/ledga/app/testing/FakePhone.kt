package com.ledga.app.testing

import com.ledga.app.notify.PhoneNotification
import com.ledga.app.notify.PhoneNotifications

/** Android's notification shade as a test sets it: [allowed] for every channel, and what was posted. */
class FakePhone(var allowed: Boolean = true) : PhoneNotifications {
    val posted = mutableListOf<PhoneNotification>()

    /** The next post throws, as Android does when the permission is revoked between the check and the post. */
    var failNext = false

    override fun allowed(channel: String): Boolean = allowed

    override fun post(n: PhoneNotification) {
        if (failNext) {
            failNext = false
            throw SecurityException("notifications revoked")
        }
        posted += n
    }
}
