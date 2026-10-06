package com.ledga.app.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Sizes
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType

private val Pill = RoundedCornerShape(percent = 50)

/** Activity's filter chips (spec §10.4): selected is ink on canvas, unselected surface with a line border. */
@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = LedgaTheme.colors
    val fg = if (selected) c.canvas else c.ink2
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = Sizes.chipHeight)
            .clip(Pill)
            .background(if (selected) c.ink else c.surface)
            .border(Sizes.hairline, if (selected) c.ink else c.line, Pill)
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = Spacing.m, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
        Text(text, style = LedgaType.label, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Read-only facts on the transaction sheet: paybill account, fee, Fuliza covered, owed. */
enum class ChipTone { Neutral, Good, Warning, Danger }

@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier, tone: ChipTone = ChipTone.Neutral) {
    val c = LedgaTheme.colors
    val (container, content) = when (tone) {
        ChipTone.Neutral -> c.plate to c.ink2
        ChipTone.Good -> c.primarySoft to c.onPrimarySoft
        ChipTone.Warning -> c.warningSoft to c.warning
        ChipTone.Danger -> c.dangerSoft to c.danger
    }
    Text(
        text,
        modifier.clip(Pill).background(container).padding(horizontal = 10.dp, vertical = 5.dp),
        style = LedgaType.label,
        color = content,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/**
 * "Name has KPLC": a tracker's matching rule (spec §10.4 Tracker detail). Removable when [onRemove] is set.
 * The chip reserves 48 dp of height; the × is a small visual button whose hit area Compose widens to 48 dp.
 */
@Composable
fun RuleChip(text: String, modifier: Modifier = Modifier, onRemove: (() -> Unit)? = null) {
    val c = LedgaTheme.colors
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .clip(Pill)
            .background(c.plate)
            .padding(start = 10.dp, end = if (onRemove == null) 10.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = LedgaType.label, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 5.dp))
        if (onRemove != null) {
            Box(
                Modifier
                    .padding(start = 2.dp)
                    .size(22.dp)
                    .clip(Pill)
                    .clickable(role = Role.Button, onClickLabel = "Remove rule $text", onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Ph.XBold, contentDescription = "Remove rule $text", tint = c.muted, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/** "+ Add rule": a dashed outline (faint is decoration) with primary text. */
@Composable
fun AddChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .clip(Pill)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    c.faint,
                    cornerRadius = CornerRadius(size.height / 2),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(Ph.PlusBold, contentDescription = null, tint = c.primary, modifier = Modifier.size(12.dp))
        Text(text, style = LedgaType.label, color = c.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "All lines ▾": opens the line switcher sheet (spec §10.4 balance card). */
@Composable
fun LineChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .clip(Pill)
            .background(c.plate)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 10.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(text, style = LedgaType.label, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Ph.CaretDownBold, contentDescription = null, tint = c.ink2, modifier = Modifier.size(12.dp))
    }
}
