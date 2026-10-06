package com.ledga.app.ui.design.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.icons.Fluent
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.type.LedgaType

/** Icon-well sizes (R20): rows use Medium, tiles and pickers Small, tracker detail Large. */
enum class WellSize(val box: Dp, val radius: Dp) {
    Small(34.dp, 11.dp),
    Medium(40.dp, 13.dp),
    Large(44.dp, 14.dp),
}

/**
 * A Fluent 3D icon on a `plate` well, the same in both themes (spec §10.3). [contentDescription] is the
 * category name, or null when text beside the icon already names it.
 */
@Composable
fun CategoryIcon(iconKey: String, contentDescription: String?, modifier: Modifier = Modifier, size: WellSize = WellSize.Medium) {
    Box(
        modifier.size(size.box).clip(RoundedCornerShape(size.radius)).background(LedgaTheme.colors.plate),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(Fluent.resOf(iconKey)), contentDescription, Modifier.size(size.box * 0.66f))
    }
}

/** Initial avatar for people without a category icon: inflow on primarySoft, outflow on plate (spec §10.3). */
@Composable
fun InitialAvatar(name: String, inflow: Boolean, modifier: Modifier = Modifier, size: WellSize = WellSize.Medium) {
    val c = LedgaTheme.colors
    Box(
        modifier.size(size.box).clip(CircleShape).background(if (inflow) c.primarySoft else c.plate),
        contentAlignment = Alignment.Center,
    ) {
        Text(Initials.of(name), style = LedgaType.label, color = if (inflow) c.onPrimarySoft else c.ink2)
    }
}

object Initials {
    /** "Jane Doe" → "JD", "naivas" → "N", "0712***678" → "#". Letters only; at most two. */
    fun of(name: String): String {
        val letters = name.trim().split(' ').mapNotNull { word -> word.firstOrNull { it.isLetter() } }
        return if (letters.isEmpty()) "#" else letters.take(2).joinToString("") { it.uppercaseChar().toString() }
    }
}
