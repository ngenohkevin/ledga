package com.ledga.app.ui.lines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ledga.app.data.lines.LineChoice
import com.ledga.app.ui.design.components.LedgaModalSheet
import com.ledga.app.ui.design.components.LineChip
import com.ledga.app.ui.design.components.RowDivider
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Sizes
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.app.ui.tx.TxText

const val ALL_LINES = "All lines"

/** "All lines", or the chosen line as "Business ··78" (R47). */
fun lineChipLabel(choice: LineChoice): String = choice.selected?.let(TxText::lineLabel) ?: ALL_LINES

/** The switcher's rows: All lines, then each line, the chosen one ticked. One radio group for TalkBack. */
@Composable
fun LineSwitcherContent(choice: LineChoice, onSelect: (Long?) -> Unit, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    val options = listOf<Pair<Long?, String>>(null to ALL_LINES) + choice.lines.map { it.id to TxText.lineLabel(it) }
    Column(modifier.fillMaxWidth().selectableGroup()) {
        options.forEachIndexed { i, (id, label) ->
            if (i > 0) RowDivider()
            val on = id == choice.lineId
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(id) })
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Icon(Ph.SimCard, contentDescription = null, tint = c.ink2, modifier = Modifier.size(Sizes.icon))
                Text(label, Modifier.weight(1f), style = LedgaType.bodyStrong, color = c.ink)
                if (on) Icon(Ph.CheckBold, contentDescription = null, tint = c.primary, modifier = Modifier.size(Sizes.icon))
            }
        }
    }
}

/**
 * The line chip (spec §10.4 balance card) with its switcher sheet. It appears on every screen the choice narrows
 * (R47), so the filter is never hidden, and not at all on a phone with fewer than two lines.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinePicker(choice: LineChoice, onSelect: (Long?) -> Unit, modifier: Modifier = Modifier) {
    if (!choice.showChip) return
    var open by rememberSaveable { mutableStateOf(false) }
    LineChip(lineChipLabel(choice), onClick = { open = true }, modifier = modifier)
    if (open) {
        LedgaModalSheet(onDismiss = { open = false }, title = "Choose a line") {
            LineSwitcherContent(
                choice,
                onSelect = {
                    onSelect(it)
                    open = false
                },
            )
        }
    }
}
