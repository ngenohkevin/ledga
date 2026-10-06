package com.ledga.app.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
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
    Row(modifier.clip(pill).background(c.plate).padding(3.dp).selectableGroup()) {
        options.forEachIndexed { index, label ->
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
                // Shrinks to fit (down to 8 sp) at large font scales, then ellipsizes; never cuts a word in half.
                Text(
                    label,
                    style = LedgaType.label,
                    color = if (on) c.ink else c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = LedgaType.label.fontSize, stepSize = 0.5.sp),
                )
            }
        }
    }
}
