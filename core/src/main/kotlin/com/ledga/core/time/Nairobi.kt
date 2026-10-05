package com.ledga.core.time

import java.time.ZoneId

/** M-Pesa prints Kenyan local time; every date, period and bucket in Ledga uses this zone. */
object Nairobi {
    val ZONE: ZoneId = ZoneId.of("Africa/Nairobi")
}
