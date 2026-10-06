package com.ledga.app.ui.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.icons.Fluent
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Sizes

/**
 * The large 3D icon on a raised tile that opens each onboarding step and the recovery screen (mockup `.hero3d`).
 * Decorative: the title next to it says what it means.
 */
@Composable
fun HeroIcon(iconKey: String, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier.size(96.dp).clip(shape).background(c.surface).border(Sizes.hairline, c.line, shape),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(Fluent.resOf(iconKey)), contentDescription = null, modifier = Modifier.size(62.dp))
    }
}
