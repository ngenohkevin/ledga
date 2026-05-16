package com.ledga.app.ui.components.v2

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.theme.LedgaText
import com.ledga.app.ui.theme.Radius

/**
 * Tab definition for [PillTabBar]. Keep labels SHORT — they render uppercase
 * at 10sp and need to fit comfortably in a quarter-width pill segment.
 */
data class PillTab(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * Floating pill tab bar — spec from LEDGA_REDESIGN.md §2.3 and the Mobile
 * Apps guideline.
 *
 * Container padding: 12 top / 21 sides+bottom (clears the home-indicator).
 * Pill: 62h, 36 radius, 1px outline, 4px inner padding.
 * Active item: solid accent fill + ink-on-accent label.
 */
@Composable
fun PillTabBar(
    tabs: List<PillTab>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(tabs.size in 3..5) {
        "PillTabBar supports 3–5 tabs only (got ${tabs.size})."
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(PaddingValues(start = 21.dp, end = 21.dp, top = 12.dp, bottom = 21.dp)),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            shape = RoundedCornerShape(Radius.TabBarPill),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { tab ->
                    PillTabItem(
                        tab = tab,
                        selected = tab.key == selectedKey,
                        onClick = { onSelect(tab.key) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PillTabItem(
    tab: PillTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else androidx.compose.ui.graphics.Color.Transparent,
        label = "pill-bg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "pill-fg",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = fg,
            modifier = Modifier.size(18.dp),
        )
        Box(modifier = Modifier.height(4.dp))
        Text(
            text = tab.label.uppercase(),
            style = LedgaText.Overline,
            color = fg,
        )
    }
}
