package com.ledga.app.ui.app

import com.ledga.app.testing.snapScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Spec §15.2: the shell's screens, light/dark × 1.0/1.3. Synthetic numbers only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class ShellScreensTest {

    @Test
    fun recovery() = snapScreen("recovery") {
        RecoveryScreen("Migration didn't properly handle: transactions", canShare = true, onShare = {}, onRetry = {})
    }
}
