package com.ledga.app.ui.you

import com.ledga.app.testing.snapScreen
import com.ledga.app.ui.app.ShellFrame
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class UnreadableScreensTest {
    private val messages = listOf(
        UnreadableMessage(2, "MPESA", "TJK4AB12UX Confirmed. Ksh250.00 paid to SAMPLE MERCHANT by a route Ledga hasn't met on 5/10/26 at 2:15 PM. New M-PESA balance is Ksh1,750.00.", Instant.parse("2026-10-05T11:15:00Z")),
        UnreadableMessage(1, "M-PESA", "TJK4AB12UY Confirmed. A SAMPLE SERVICE you have not used before charged Ksh30.00 on 1/10/26 at 11:00 AM.", Instant.parse("2026-10-01T08:00:00Z")),
    )

    @Test
    fun unreadable() = snapScreen("unreadable") { ShellFrame(null, onSelect = {}) { UnreadableContent(UnreadableUi(true, messages), UnreadableActions()) } }
}
