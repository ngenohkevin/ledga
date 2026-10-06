package com.ledga.app.ui.design.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ledga.app.ui.design.theme.LedgaTheme

/** M3 switch in palette C. Pass a null [onCheckedChange] when the whole row toggles (see [ListRow]). */
@Composable
fun LedgaSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onPrimary,
            checkedTrackColor = c.primary,
            checkedBorderColor = c.primary,
            uncheckedThumbColor = c.muted,
            uncheckedTrackColor = c.plate,
            uncheckedBorderColor = c.muted,
        ),
    )
}
