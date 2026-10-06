package com.ledga.app.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Sizes
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType

/** Green action pill (spec §10.1 "green actions"). */
@Composable
fun PrimaryPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) =
    Pill(text, onClick, modifier, icon, LedgaTheme.colors.primary, LedgaTheme.colors.onPrimary)

/** Secondary action pill on primarySoft. */
@Composable
fun SoftPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) =
    Pill(text, onClick, modifier, icon, LedgaTheme.colors.primarySoft, LedgaTheme.colors.onPrimarySoft)

/** A quiet action on a sheet ("Share", "Hide"): a line border and ink2 text on whatever is behind it. */
@Composable
fun OutlinePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = LedgaTheme.colors
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .border(Sizes.hairline, c.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = c.ink2, modifier = Modifier.size(Sizes.iconSmall))
        Text(text, style = LedgaType.label, color = c.ink2)
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit, modifier: Modifier, icon: ImageVector?, container: Color, content: Color) {
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(percent = 50))
            .background(container)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(Sizes.iconSmall))
        Text(text, style = LedgaType.label, color = content)
    }
}

/** A text action in primary green ("See all", "+ Add rule"), with a 48 dp touch target. */
@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        style = LedgaType.label,
        color = LedgaTheme.colors.primary,
    )
}

/** Spending up is Bad (red), down is Good (green). The caller writes the text ("▲ 9% vs Aug"). */
enum class DeltaTone { Good, Bad, Neutral }

/**
 * Spending up is Bad (red), down is Good (green). The caller writes the text ("▲ 9% vs Aug") and, for TalkBack,
 * [speech] ("9 percent more than Aug"): the arrow glyphs would otherwise be read out by name (Phase 3 deferred M10).
 */
@Composable
fun DeltaBadge(text: String, tone: DeltaTone, modifier: Modifier = Modifier, speech: String? = null) {
    val c = LedgaTheme.colors
    val (container, content) = when (tone) {
        DeltaTone.Good -> c.primarySoft to c.onPrimarySoft
        DeltaTone.Bad -> c.dangerSoft to c.danger
        DeltaTone.Neutral -> c.plate to c.ink2
    }
    val spoken = if (speech != null) Modifier.clearAndSetSemantics { contentDescription = speech } else Modifier
    Text(
        text,
        modifier.then(spoken).clip(RoundedCornerShape(percent = 50)).background(container).padding(horizontal = 8.dp, vertical = 3.dp),
        style = LedgaType.label,
        color = content,
    )
}

/** "Recent ……… See all": a section title (a TalkBack heading) with an optional action. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = LedgaType.section, color = LedgaTheme.colors.ink)
        if (actionLabel != null && onAction != null) LinkButton(actionLabel, onAction)
    }
}
