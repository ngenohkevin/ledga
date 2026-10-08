package com.ledga.app.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledga.app.ui.app.DetailFrame
import com.ledga.app.ui.design.components.ChipTone
import com.ledga.app.ui.design.components.EmptyState
import com.ledga.app.ui.design.components.InfoChip
import com.ledga.app.ui.design.components.LedgaCard
import com.ledga.app.ui.design.components.SkeletonRow
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Sizes
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType

/** What Version history's taps do. Every default does nothing, for screenshots and tests. */
data class VersionHistoryActions(
    val onBack: () -> Unit = {},
    val onToggle: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** You → About → Version history (spec §13.4, R142): every release on the channel, newest first; a tap opens its notes. */
@Composable
fun VersionHistoryContent(ui: VersionHistoryUi, actions: VersionHistoryActions, modifier: Modifier = Modifier) {
    DetailFrame("Version history", onBack = actions.onBack, modifier = modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            when {
                !ui.loaded || (ui.rows.isEmpty() && ui.checking) -> repeat(4) { SkeletonRow() }
                ui.rows.isEmpty() -> EmptyState(
                    "fluent_bar_chart",
                    "No releases to show",
                    ui.failureLine ?: "Connect to the internet and Ledga fetches its release history.",
                    actionLabel = "Try again",
                    onAction = actions.onRetry,
                )
                else -> ui.rows.forEach { row -> ReleaseCard(row, open = row.tag in ui.expanded, onToggle = { actions.onToggle(row.tag) }) }
            }
        }
    }
}

@Composable
private fun ReleaseCard(row: HistoryRow, open: Boolean, onToggle: () -> Unit) {
    val c = LedgaTheme.colors
    LedgaCard(Modifier.fillMaxWidth().semantics { stateDescription = if (open) "Notes shown" else "Notes hidden" }, onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Column(Modifier.weight(1f)) {
                Text(row.title, style = LedgaType.bodyStrong, color = c.ink)
                if (row.line.isNotEmpty()) Text(row.line, style = LedgaType.caption, color = c.muted)
            }
            if (row.installed) InfoChip("Installed", tone = ChipTone.Good)
            Icon(if (open) Ph.CaretUp else Ph.CaretDown, contentDescription = null, tint = c.muted, modifier = Modifier.size(Sizes.icon))
        }
        if (open) NotesList(row.notes, Modifier.padding(top = Spacing.m))
    }
}

/** You → About → Version history (route). */
@Composable
fun VersionHistoryScreen(onBack: () -> Unit, vm: VersionHistoryViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    VersionHistoryContent(ui, VersionHistoryActions(onBack = onBack, onToggle = vm::toggle, onRetry = { vm.retry() }))
}
