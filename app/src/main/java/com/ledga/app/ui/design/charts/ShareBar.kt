package com.ledga.app.ui.design.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.components.CategoryIcon
import com.ledga.app.ui.design.components.WellSize
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.ChartTones
import com.ledga.app.ui.design.type.LedgaType

/**
 * A "Where it went" row (spec §10.4 Spending): icon, name and amount, a track filled to [fraction] in the category
 * colour, and a caption ("34% · 12 payments"). It is one TalkBack item.
 */
@Composable
fun ShareBar(
    iconKey: String,
    name: String,
    amount: String,
    fraction: Float,
    color: Color,
    caption: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val c = LedgaTheme.colors
    val track = RoundedCornerShape(3.dp)
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CategoryIcon(iconKey, contentDescription = null, size = WellSize.Small)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = LedgaType.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(amount, style = LedgaType.amount, color = c.ink, maxLines = 1, softWrap = false)
            }
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(6.dp).clip(track).background(c.barTrack)) {
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(track).background(ChartTones.strong(color, c.surface)))
            }
            Text(caption, Modifier.padding(top = 3.dp), style = LedgaType.caption, color = c.muted)
        }
    }
}
