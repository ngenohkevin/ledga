package com.ledga.app.ui.components.v2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.theme.LedgaAccent
import com.ledga.app.ui.theme.LedgaAccentDeep
import com.ledga.app.ui.theme.LedgaAccentSoft
import com.ledga.app.ui.theme.LedgaText

/**
 * Pill chip — filter rails, category chips, segmented controls.
 *
 * Selected state uses accent-soft bg + accent-deep text + accent border
 * for the deliberate, glanceable "this is on" look from the spec.
 */
@Composable
fun LedgaChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val bg = if (selected) LedgaAccentSoft else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) LedgaAccentDeep else MaterialTheme.colorScheme.onSurface
    val border = if (selected) BorderStroke(1.dp, LedgaAccent) else null

    Surface(
        modifier = modifier
            .height(32.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(999.dp),
        color = bg,
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                style = LedgaText.BodyM,
                color = fg,
            )
        }
    }
}
