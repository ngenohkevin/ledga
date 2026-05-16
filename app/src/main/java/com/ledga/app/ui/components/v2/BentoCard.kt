package com.ledga.app.ui.components.v2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.theme.LedgaText
import com.ledga.app.ui.theme.Radius
import com.ledga.app.ui.theme.Space

/**
 * Generic bento card — the workhorse container of the v2 UI.
 *
 * Variants:
 *   - tonal = false  -> default white surface + 1px outline (light theme)
 *   - tonal = true   -> filled with [tonalColor], no outline (e.g. accent-soft)
 *
 * Optional header row (icon + title + trailing chevron). Content goes in [content].
 */
@Composable
fun BentoCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    overline: String? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    tonal: Boolean = false,
    tonalColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(Space.Card),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val container = if (tonal) tonalColor else MaterialTheme.colorScheme.surface
    val border = if (tonal) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.Card))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(Radius.Card),
        color = container,
        border = border,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            if (overline != null || title != null || icon != null || trailing != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s4),
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint ?: MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (overline != null) {
                            Text(
                                text = overline.uppercase(),
                                style = LedgaText.Overline,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (title != null) {
                            Text(
                                text = title,
                                style = LedgaText.TitleS,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    if (trailing != null) trailing()
                }
            }
            content()
        }
    }
}

/**
 * Small 40×40 icon tile with rounded corners, category-color tint.
 * Used in transaction rows, category chips, account avatars.
 */
@Composable
fun CategoryIconTile(
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(Radius.IconTile))
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}
