package com.ledga.app.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Sizes
import com.ledga.app.ui.design.type.LedgaType

@Immutable
data class NavTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** Spec §10.4 + 4e D2: four tabs, Home · Activity · Categories · You. */
object LedgaTabs {
    val Home = NavTab("Home", Ph.House, Ph.HouseFill)
    val Activity = NavTab("Activity", Ph.Receipt, Ph.ReceiptFill)
    val Categories = NavTab("Categories", Ph.SquaresFour, Ph.SquaresFourFill)
    val You = NavTab("You", Ph.UserCircle, Ph.UserCircleFill)
    val all = listOf(Home, Activity, Categories, You)
}

/**
 * Bottom navigation (spec §10.4): icon + label, and the active tab shows the filled icon on a primarySoft pill.
 * It pads itself for the gesture/navigation bar (edge-to-edge). Each tab is a TalkBack tab, at least 64 dp tall.
 */
@Composable
fun LedgaBottomBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, tabs: List<NavTab> = LedgaTabs.all) {
    val c = LedgaTheme.colors
    Column(modifier.fillMaxWidth().background(c.navBar)) {
        Box(Modifier.fillMaxWidth().height(Sizes.hairline).background(c.line))
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .heightIn(min = 64.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val on = index == selected
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp) // never fillMaxHeight: under a weighted spacer that would take the screen
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(index) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (on) c.primarySoft else Color.Transparent)
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            if (on) tab.selectedIcon else tab.icon,
                            contentDescription = null,
                            tint = if (on) c.primary else c.muted,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        tab.label,
                        Modifier.padding(top = 2.dp),
                        style = LedgaType.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = if (on) c.primary else c.muted,
                    )
                }
            }
        }
    }
}
