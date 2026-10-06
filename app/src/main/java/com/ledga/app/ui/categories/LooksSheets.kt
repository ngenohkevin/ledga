package com.ledga.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ledga.app.data.edit.CategoryLooks
import com.ledga.app.ui.design.components.CategoryIcon
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.CategoryPalette
import com.ledga.app.ui.design.tokens.ChartTones
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType

/** R68: the icons a category of your own can take, the current one ringed. A radio group for TalkBack. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconChoiceContent(selected: String, onPick: (String) -> Unit) {
    val c = LedgaTheme.colors
    FlowRow(
        Modifier.verticalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        CategoryLooks.ICONS.forEach { key ->
            val chosen = key == selected
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(if (chosen) 2.dp else 0.dp, if (chosen) c.primary else c.surfaceSheet, RoundedCornerShape(16.dp))
                    .selectable(chosen, role = Role.RadioButton, onClick = { onPick(key) })
                    .semantics { contentDescription = CategoryText.iconName(key) },
                contentAlignment = Alignment.Center,
            ) { CategoryIcon(key, contentDescription = null) }
        }
    }
}

/** R74: the twelve swatches, each at its strong chart tone with its name under it. A radio group for TalkBack. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColourChoiceContent(selected: String?, onPick: (CategoryLooks.Swatch) -> Unit) {
    val c = LedgaTheme.colors
    FlowRow(
        Modifier.verticalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        CategoryLooks.SWATCHES.forEach { s ->
            val chosen = s.light.equals(selected, ignoreCase = true)
            val tone = ChartTones.strong(CategoryPalette.resolve("", s.light, s.dark).pick(c.isDark), c.surfaceSheet)
            Column(
                Modifier.width(64.dp).selectable(chosen, role = Role.RadioButton, onClick = { onPick(s) }).padding(vertical = Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).border(if (chosen) 3.dp else 0.dp, c.ink, CircleShape).padding(if (chosen) 5.dp else 0.dp)
                        .clip(CircleShape).background(tone),
                )
                Text(s.name, Modifier.padding(top = Spacing.xs), style = LedgaType.caption, color = c.ink2, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}
