package com.ledga.app.ui.components.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.theme.LedgaAccent
import com.ledga.app.ui.theme.LedgaAccentSoft
import com.ledga.app.ui.theme.LedgaDanger
import com.ledga.app.ui.theme.LedgaDangerSoft
import com.ledga.app.ui.theme.LedgaText
import com.ledga.app.ui.theme.Space

/**
 * A compact stat card — overline, big value, optional delta chip.
 *
 * Used everywhere a key number lives: Spent / Balance / Top category /
 * Net cashflow / Fees. Keeps numbers consistent in scale and treatment.
 */
@Composable
fun StatCard(
    overline: String,
    value: String,
    modifier: Modifier = Modifier,
    delta: DeltaChip? = null,
    caption: String? = null,
    tonal: Boolean = false,
) {
    BentoCard(
        modifier = modifier,
        tonal = tonal,
    ) {
        Text(
            text = overline.uppercase(),
            style = LedgaText.Overline,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = LedgaText.DisplayM,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (delta != null || caption != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s3),
            ) {
                if (delta != null) DeltaChipUI(delta)
                if (caption != null) {
                    Text(
                        text = caption,
                        style = LedgaText.BodyM,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

enum class DeltaDirection { Up, Down, Flat }

data class DeltaChip(
    val direction: DeltaDirection,
    val label: String,
    /** Default semantics: Up = inflow (good) when [positiveIsGood] = true. */
    val positiveIsGood: Boolean = true,
)

@Composable
private fun DeltaChipUI(delta: DeltaChip) {
    val good = when (delta.direction) {
        DeltaDirection.Up -> delta.positiveIsGood
        DeltaDirection.Down -> !delta.positiveIsGood
        DeltaDirection.Flat -> true
    }
    val bg = if (good) LedgaAccentSoft else LedgaDangerSoft
    val fg = if (good) LedgaAccent else LedgaDanger
    val icon = when (delta.direction) {
        DeltaDirection.Up -> Icons.Filled.ArrowUpward
        DeltaDirection.Down -> Icons.Filled.ArrowDownward
        DeltaDirection.Flat -> null
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(12.dp),
                )
            }
            Text(
                text = delta.label,
                style = LedgaText.Caption,
                color = fg,
            )
        }
    }
}

