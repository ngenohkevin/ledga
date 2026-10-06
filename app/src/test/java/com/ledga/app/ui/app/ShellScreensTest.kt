package com.ledga.app.ui.app

import com.ledga.app.testing.snapScreen
import com.ledga.app.work.HistoryProgress
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** Spec §15.2: the shell's screens, light/dark × 1.0/1.3. Synthetic numbers only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class ShellScreensTest {
    private val first = Instant.parse("2023-03-12T07:00:00Z")
    private val last = Instant.parse("2026-10-06T06:00:00Z")

    @Test
    fun home() = snapScreen("shell_home") {
        ShellFrame(Tab.HOME, onSelect = {}) {
            InterimHome(InterimHomeState(true, 7_412, first, last, 1_111_100L, HistoryProgress(1_200, 7_412)), onAllowSms = {})
        }
    }

    @Test
    fun noSms() = snapScreen("shell_no_sms") {
        ShellFrame(Tab.HOME, onSelect = {}) { InterimHome(InterimHomeState(smsGranted = false), onAllowSms = {}) }
    }

    @Test
    fun comingNext() = snapScreen("shell_coming") {
        ShellFrame(Tab.ACTIVITY, onSelect = {}) { ComingNext(Tab.ACTIVITY) }
    }

    @Test
    fun recovery() = snapScreen("recovery") {
        RecoveryScreen("Migration didn't properly handle: transactions", canShare = true, onShare = {}, onRetry = {})
    }
}
