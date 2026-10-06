package com.ledga.app.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType

/**
 * Week/Month/Year, Transactions/Spending/People, 6M/12M/Year (spec §10.4): a plate track with the selected
 * segment on surface. Each segment is a TalkBack tab; the control is 48 dp tall.
 */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    val pill = RoundedCornerShape(percent = 50)
    BoxWithConstraints(modifier) {
        // One size for every label, the largest at which all fit (down to 8 sp), then ellipsis; never cut mid-word.
        // Shrunk one by one, a longer word was drawn smaller than its neighbours.
        val label = rememberSharedLabelStyle(options, maxWidth)
        Row(Modifier.fillMaxWidth().clip(pill).background(c.plate).padding(SEGMENT_INSET).selectableGroup()) {
            options.forEachIndexed { index, text ->
                val on = index == selected
                val raised = if (on && !c.isDark) Modifier.shadow(1.dp, pill, ambientColor = c.shadow, spotColor = c.shadow) else Modifier
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp)
                        .then(raised)
                        .clip(pill)
                        .background(if (on) c.surface else Color.Transparent)
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(index) })
                        .padding(horizontal = Spacing.s),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text, style = label, color = if (on) c.ink else c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private val SEGMENT_INSET = 3.dp
private const val MIN_SEGMENT_SP = 8f

/** The largest shared label size at which every option fits its segment of a control [width] wide. */
@Composable
private fun rememberSharedLabelStyle(options: List<String>, width: Dp): TextStyle {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(options, width, density) {
        val base = LedgaType.label
        if (options.isEmpty()) return@remember base
        val slot = with(density) { (width.toPx() - SEGMENT_INSET.toPx() * 2) / options.size - Spacing.s.toPx() * 2 }
        var size = base.fontSize.value
        while (size > MIN_SEGMENT_SP) {
            val style = base.copy(fontSize = size.sp)
            if (options.all { measurer.measure(it, style, maxLines = 1, softWrap = false, density = density).size.width <= slot }) return@remember style
            size -= 0.5f
        }
        base.copy(fontSize = MIN_SEGMENT_SP.sp)
    }
}
