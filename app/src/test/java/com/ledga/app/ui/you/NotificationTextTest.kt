package com.ledga.app.ui.you

import com.ledga.app.data.settings.Settings
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** R75: what You → Notifications says, and the threshold as typed. */
class NotificationTextTest {
    @Test
    fun `times read like M-Pesa's, midnight included`() {
        assertEquals("12:00 AM", NotificationText.time(0))
        assertEquals("8:00 PM", NotificationText.time(20 * 60))
        assertEquals("11:59 PM", NotificationText.time(24 * 60 - 1))
        assertEquals("8 PM", NotificationText.shortTime(20 * 60))
        assertEquals("8:30 PM", NotificationText.shortTime(20 * 60 + 30))
    }

    @Test
    fun `the threshold saves only from Ksh 100 to Ksh 1,000,000`() {
        assertNull(NotificationText.parseThreshold("0"))
        assertNull(NotificationText.parseThreshold("abc"))
        assertNull(NotificationText.parseThreshold("99"))
        assertNull(NotificationText.parseThreshold("1,000,001"))
        assertEquals(10_000L, NotificationText.parseThreshold("100"))
        assertEquals(500_050L, NotificationText.parseThreshold(" 5,000.50 "))
        assertEquals(100_000_000L, NotificationText.parseThreshold("1,000,000"))
    }

    @Test
    fun `You's row sums up what is on, or says why nothing can arrive`() {
        assertEquals("Daily 8 PM · Weekly Sun · Fuliza", NotificationText.summary(Settings(), allowed = true))
        assertEquals(
            "Daily 9:30 PM · Weekly Sun · Large ${Char(0x2265)} 5k · Fuliza",
            NotificationText.summary(Settings(dailySummaryMinute = 21 * 60 + 30, notifyLarge = true), allowed = true),
        )
        assertEquals("All off", NotificationText.summary(Settings(notifyDaily = false, notifyWeekly = false, notifyFuliza = false), allowed = true))
        assertEquals("Off for Ledga in Android settings", NotificationText.summary(Settings(), allowed = false))
    }

    @Test
    fun `an early summary time says it covers the day before (final review I2)`() {
        assertEquals("What you spent the day before, at 7:00 AM. Skipped on days with no spending.", NotificationText.dailyDetail(7 * 60))
        assertEquals("What you spent the day before, at 12:00 AM. Skipped on days with no spending.", NotificationText.dailyDetail(0))
        assertEquals("What you spent that day, at 12:00 PM. Skipped on days with no spending.", NotificationText.dailyDetail(12 * 60))
        assertEquals("What you spent that day, at 8:00 PM. Skipped on days with no spending.", NotificationText.dailyDetail(20 * 60))
    }
}
