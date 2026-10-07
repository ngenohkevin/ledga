package com.ledga.app.ui.lines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledga.app.ui.activity.RangeDatePicker
import com.ledga.app.ui.app.DetailFrame
import com.ledga.app.ui.app.GroupLabel
import com.ledga.app.ui.app.grouped
import com.ledga.app.ui.design.components.ChoiceChip
import com.ledga.app.ui.design.components.EmptyState
import com.ledga.app.ui.design.components.LedgaCard
import com.ledga.app.ui.design.components.ListRow
import com.ledga.app.ui.design.components.PrimaryPill
import com.ledga.app.ui.design.components.RowTrailing
import com.ledga.app.ui.design.format.DateLabels
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.app.ui.tx.TxText
import kotlinx.coroutines.launch

data class UnassignedActions(
    val onBack: () -> Unit = {},
    val onPlace: () -> Unit = {},
    val onLine: (Long) -> Unit = {},
    val onFrom: () -> Unit = {},
    val onTo: () -> Unit = {},
    val onMove: () -> Unit = {},
)

private val DOTS = Char(0x2026)

private fun payments(n: Int) = "${grouped(n)} ${if (n == 1) "payment" else "payments"}"

/** Not on a line (R116, R128): by balance, then by date. Stateless. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnassignedContent(ui: UnassignedUi, actions: UnassignedActions, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    DetailFrame("Not on a line", onBack = actions.onBack, modifier = modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            when {
                !ui.loaded -> return@Column
                ui.lines.isEmpty() -> return@Column EmptyState("fluent_mobile_phone_with_arrow", "No lines yet", "A line appears when Ledga sees M-Pesa messages from a SIM.")
                ui.unassigned == 0 -> return@Column EmptyState("fluent_check_mark_button", "Every payment is on a line", "Payments Ledga can't put on a line show up here.")
            }
            Text(
                "${payments(ui.unassigned)} ${if (ui.unassigned == 1) "isn't" else "aren't"} on a line: Ledga couldn't tell which SIM " +
                    "${if (ui.unassigned == 1) "it" else "they"} came from.",
                style = LedgaType.body,
                color = c.ink2,
            )
            Section("By balance") {
                Text("Ledga follows each line's M-Pesa balance from one payment to the next.", style = LedgaType.caption, color = c.muted)
                if (ui.counting) {
                    Text("Counting$DOTS", Modifier.padding(top = Spacing.s), style = LedgaType.body, color = c.ink2)
                } else {
                    ui.shares.forEach { s ->
                        Text("${s.label} · ${payments(s.count)}", Modifier.padding(top = Spacing.s), style = LedgaType.bodyStrong, color = c.ink)
                    }
                    if (ui.left > 0) Text("${grouped(ui.left)} still unclear", Modifier.padding(top = Spacing.s), style = LedgaType.body, color = c.ink2)
                }
                PrimaryPill("Put them on their lines", actions.onPlace, Modifier.fillMaxWidth().padding(top = Spacing.m), enabled = !ui.counting && !ui.busy && ui.placeable > 0)
            }
            Section("By date") {
                Text("Moves the payments not on a line between two dates to one line.", style = LedgaType.caption, color = c.muted)
                FlowRow(
                    Modifier.selectableGroup().padding(top = Spacing.s),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    ui.lines.forEach { l -> ChoiceChip(TxText.lineLabel(l), ui.rangeLine == l.id, { actions.onLine(l.id) }, role = Role.RadioButton) }
                }
                ui.from?.let { ListRow("From", Modifier.padding(top = Spacing.s), trailing = RowTrailing.Value(DateLabels.date(it)), onClick = actions.onFrom) }
                ui.to?.let { ListRow("To", trailing = RowTrailing.Value(DateLabels.date(it)), onClick = actions.onTo) }
                Text(ui.inRange?.let { "${payments(it)} in these dates" } ?: "Counting$DOTS", style = LedgaType.body, color = c.ink2)
                val line = ui.lines.firstOrNull { it.id == ui.rangeLine }
                PrimaryPill(
                    "Move to ${line?.displayName.orEmpty()}",
                    actions.onMove,
                    Modifier.fillMaxWidth().padding(top = Spacing.m),
                    enabled = line != null && !ui.busy && (ui.inRange ?: 0) > 0,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    GroupLabel(title)
    LedgaCard(Modifier.fillMaxWidth(), content = content)
}

private enum class Edge { FROM, TO }

/** The route: the date picker, and the snackbar with Undo after each placement. */
@Composable
fun UnassignedScreen(onBack: () -> Unit, vm: UnassignedViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var picking by rememberSaveable { mutableStateOf<Edge?>(null) }
    val announce: (Moved) -> Unit = { moved ->
        scope.launch {
            val text = if (moved.byBalance) "Put ${payments(moved.count)} on their lines" else "Moved ${payments(moved.count)} to ${moved.lineLabel}"
            if (snackbar.showSnackbar(text, actionLabel = "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.undo(moved)
        }
    }
    Box(Modifier.fillMaxSize()) {
        UnassignedContent(
            ui,
            UnassignedActions(
                onBack = onBack,
                onPlace = { vm.placeByBalance(announce) },
                onLine = vm::chooseLine,
                onFrom = { picking = Edge.FROM },
                onTo = { picking = Edge.TO },
                onMove = { vm.moveRange(announce) },
            ),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(Spacing.l))
    }
    val edge = picking ?: return
    val today = ui.today ?: return
    val initial = (if (edge == Edge.FROM) ui.from else ui.to) ?: today
    RangeDatePicker(
        initial = initial,
        today = today,
        onPicked = { day ->
            if (edge == Edge.FROM) vm.setFrom(day) else vm.setTo(day)
            picking = null
        },
        onDismiss = { picking = null },
    )
}
