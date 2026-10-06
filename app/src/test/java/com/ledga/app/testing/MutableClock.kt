package com.ledga.app.testing

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock a test moves by hand. Its zone is UTC on purpose: Ledga's dates must come out in Nairobi time anyway. */
class MutableClock(@Volatile var instant: Instant) : Clock() {
    override fun instant(): Instant = instant
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
}
