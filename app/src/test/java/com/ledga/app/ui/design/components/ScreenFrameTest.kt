package com.ledga.app.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.ledga.app.testing.snapScreen
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Proves the Phase 4 screen harness: a canvas, a title and the bottom bar, four ways. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi") // 2x pixels; snapScreen's size option keeps the density
class ScreenFrameTest {
    @Test
    fun frame() = snapScreen("frame") {
        Column(Modifier.fillMaxSize().background(LedgaTheme.colors.canvas)) {
            Text("Trackers", Modifier.padding(Spacing.screen), style = LedgaType.screenTitle, color = LedgaTheme.colors.ink)
            Spacer(Modifier.weight(1f))
            LedgaBottomBar(selected = 2, onSelect = {})
        }
    }
}
