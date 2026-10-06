package com.ledga.app.ui.design.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.ledga.app.testing.SPECIMEN_QUALIFIERS
import com.ledga.app.testing.snap
import com.ledga.app.ui.design.type.LedgaType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SPECIMEN_QUALIFIERS)
class ThemeSpecimenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun theme() = compose.snap("theme") { ThemeSpecimen() }
}

@Composable
private fun ThemeSpecimen() {
    val c = LedgaTheme.colors
    val swatches = listOf(
        "canvas" to c.canvas, "surface" to c.surface, "plate" to c.plate, "primary" to c.primary,
        "primarySoft" to c.primarySoft, "inflow" to c.inflow, "danger" to c.danger, "dangerSoft" to c.dangerSoft,
        "warning" to c.warning, "warningSoft" to c.warningSoft, "barTrack" to c.barTrack,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        swatches.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (name, color) ->
                    Column {
                        Box(Modifier.size(width = 70.dp, height = 28.dp).background(color).border(1.dp, c.line))
                        Text(name, style = LedgaType.caption, color = c.muted)
                    }
                }
            }
        }
        Text("Ksh 12,345.67", style = LedgaType.balance, color = c.ink)
        Text("Screen title", style = LedgaType.screenTitle, color = c.ink)
        Text("Section heading", style = LedgaType.section, color = c.ink)
        Text("Card title", style = LedgaType.cardTitle, color = c.ink)
        Text("Body text in ink2 for longer descriptions that wrap", style = LedgaType.body, color = c.ink2)
        Text("Caption in muted", style = LedgaType.caption, color = c.muted)
        Text("OVERLINE LABEL", style = LedgaType.overline, color = c.muted)
        Column(horizontalAlignment = Alignment.End) {
            Text("111,111.11", style = LedgaType.amount, color = c.ink)
            Text("888,888.88", style = LedgaType.amount, color = c.ink)
        }
    }
}
