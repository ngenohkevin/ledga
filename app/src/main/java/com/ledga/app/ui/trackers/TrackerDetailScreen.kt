package com.ledga.app.ui.trackers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.ui.design.charts.ChartTooltip
import com.ledga.app.ui.design.charts.ColumnChart
import com.ledga.app.ui.design.components.AddChip
import com.ledga.app.ui.design.components.CategoryIcon
import com.ledga.app.ui.design.components.EmptyState
import com.ledga.app.ui.design.components.LedgaCard
import com.ledga.app.ui.design.components.LedgaModalSheet
import com.ledga.app.ui.design.components.PrimaryPill
import com.ledga.app.ui.design.components.RowDivider
import com.ledga.app.ui.design.components.RuleChip
import com.ledga.app.ui.design.components.SectionHeader
import com.ledga.app.ui.design.components.SegmentedControl
import com.ledga.app.ui.design.components.SkeletonRow
import com.ledga.app.ui.design.components.StatTile
import com.ledga.app.ui.design.components.WellSize
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.CategoryPalette
import com.ledga.app.ui.design.tokens.Sizes
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.app.ui.lines.LinePicker
import com.ledga.app.ui.tx.CategoryPickerHost
import com.ledga.app.ui.tx.OpenSheets
import com.ledga.app.ui.tx.TransactionSheetHost
import com.ledga.app.ui.tx.TxText
import com.ledga.core.derive.RuleOrigin
import com.ledga.core.time.PeriodType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ledga.app.ui.design.components.TxRow as TransactionRow
import com.ledga.app.ui.app.RoundButton
import com.ledga.app.ui.rules.AddRuleSheet
import com.ledga.app.ui.rules.RenameSheet

/** What Tracker detail's taps do. Every default does nothing, for screenshots and tests. */
data class TrackerDetailActions(
    val onBack: () -> Unit = {},
    val onRange: (DetailRange) -> Unit = {},
    val onSelect: (Int) -> Unit = {},
    val onLine: (Long?) -> Unit = {},
    val onEdit: () -> Unit = {},
    val onRemoveRule: (RuleChipUi) -> Unit = {},
    val onAddRule: () -> Unit = {},
    val onRename: () -> Unit = {},
    val onStopTracking: () -> Unit = {},
    val onOpenTx: (String) -> Unit = {},
    val onPickCategory: (String) -> Unit = {},
    val onSeeAll: () -> Unit = {},
)

/**
 * Tracker detail (spec §10.4, mockup Electricity tracker), full screen with no bottom bar, so it pads the system bars
 * itself:
 * - the top bar (back, icon, name, overflow);
 * - the line chip;
 * - the chart card (6M / 12M / All, average line, tooltip);
 * - the stat tiles;
 * - "Matched by";
 * - Payments.
 */
@Composable
fun TrackerDetailContent(ui: TrackerDetailUi, actions: TrackerDetailActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))) {
        DetailTopBar(ui, actions)
        val category = ui.category
        when {
            !ui.loaded -> Column(Modifier.padding(horizontal = Spacing.screen)) { repeat(4) { SkeletonRow() } }
            ui.missing || category == null -> EmptyState("fluent_bar_chart", "This tracker is gone", "Its category no longer exists.", actionLabel = "Back", onAction = actions.onBack)
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                LinePicker(ui.line, actions.onLine)
                ChartCard(ui, category, actions)
                StatTiles(ui)
                MatchedBy(ui, actions)
                Payments(ui, actions)
            }
        }
    }
}

@Composable
private fun DetailTopBar(ui: TrackerDetailUi, actions: TrackerDetailActions) {
    val c = LedgaTheme.colors
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        RoundButton(Ph.ArrowLeft, "Back", actions.onBack)
        val category = ui.category
        if (category == null) {
            Spacer(Modifier.weight(1f))
        } else {
            CategoryIcon(category.icon3d, contentDescription = null, size = WellSize.Small)
            Text(category.name, Modifier.weight(1f).semantics { heading() }, style = LedgaType.screenTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box {
                RoundButton(Ph.DotsThree, "More options") { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.surfaceSheet) {
                    DropdownMenuItem(
                        text = { Text("Rename", style = LedgaType.body, color = c.ink) },
                        onClick = {
                            menu = false
                            actions.onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Stop tracking", style = LedgaType.body, color = c.ink) },
                        onClick = {
                            menu = false
                            actions.onStopTracking()
                        },
                    )
                }
            }
        }
    }
}


@Composable
private fun ChartCard(ui: TrackerDetailUi, category: CategoryRow, actions: TrackerDetailActions) {
    val c = LedgaTheme.colors
    val color = CategoryPalette.resolve(category.key, category.color, category.colorDark).pick(c.isDark)
    val years = ui.buckets.firstOrNull()?.period?.type == PeriodType.YEAR
    LedgaCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(if (years) "Year by year" else "Month by month", Modifier.weight(1f), style = LedgaType.label, color = c.muted)
            SegmentedControl(DetailRange.entries.map { it.label }, ui.range.ordinal, { actions.onRange(DetailRange.entries[it]) }, Modifier.weight(1.2f))
        }
        ColumnChart(
            bars = ui.bars,
            colors = listOf(color),
            summary = "${category.name} by ${if (years) "year" else "month"}: " + ui.bars.joinToString(", ") { it.speech },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.m),
            selectedIndex = ui.selectedIndex,
            onSelect = actions.onSelect,
            dimUnselected = true,
            average = ui.averageCents,
            showGrid = true,
            height = 170.dp,
            tooltip = { i ->
                ui.buckets.getOrNull(i)?.let { b ->
                    val (title, caption) = TrackerText.tooltip(b, i == ui.buckets.lastIndex)
                    ChartTooltip(title, caption)
                }
            },
        )
    }
}

@Composable
private fun StatTiles(ui: TrackerDetailUi) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            StatTile("This month", TrackerText.ksh(ui.thisMonthCents), Modifier.weight(1f))
            StatTile("Last month", TrackerText.ksh(ui.lastMonthCents), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            StatTile("Avg / month", ui.monthlyAverageCents?.let(TrackerText::ksh) ?: "Not yet", Modifier.weight(1f))
            StatTile("${ui.year} so far", TrackerText.ksh(ui.yearSoFarCents), Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchedBy(ui: TrackerDetailUi, actions: TrackerDetailActions) {
    val c = LedgaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionHeader(
            "Matched by",
            actionLabel = when {
                ui.rules.isEmpty() -> null
                ui.editing -> "Done"
                else -> "Edit"
            },
            onAction = if (ui.rules.isEmpty()) null else actions.onEdit,
        )
        // On a card, like Payments: a chip's plate barely differs from the screen's canvas (contrast ~1.02).
        LedgaCard(Modifier.fillMaxWidth()) {
            if (ui.rules.isEmpty()) {
                Text("No rules yet. Payments you file here yourself still count.", Modifier.padding(bottom = Spacing.s), style = LedgaType.caption, color = c.muted)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                ui.rules.forEach { r -> RuleChip(r.label, onRemove = if (ui.editing) ({ actions.onRemoveRule(r) }) else null) }
                AddChip("Add rule", actions.onAddRule)
            }
        }
    }
}

@Composable
private fun Payments(ui: TrackerDetailUi, actions: TrackerDetailActions) {
    val c = LedgaTheme.colors
    val today = ui.today ?: return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionHeader("Payments", actionLabel = "See all", onAction = actions.onSeeAll)
        LedgaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
            if (ui.payments.isEmpty()) Text("No payments yet.", Modifier.padding(Spacing.l), style = LedgaType.body, color = c.muted)
            ui.payments.forEachIndexed { i, row ->
                if (i > 0) RowDivider(Modifier.padding(horizontal = Spacing.m))
                val (subtitle, tail) = TrackerText.paymentLine(row, today)
                TransactionRow(
                    leading = TxText.leading(row, ui.category?.icon3d ?: "fluent_package"),
                    title = TxText.title(row),
                    subtitle = subtitle,
                    subtitleTail = tail,
                    amountCents = row.amountCents,
                    inflow = TxText.isInflow(row.flow),
                    speech = TxText.speech(row, today),
                    onClick = { actions.onOpenTx(row.code) },
                    onLongClick = { actions.onPickCategory(row.code) },
                    onClickLabel = "Open payment",
                    onLongClickLabel = "Change category",
                )
            }
        }
    }
}

/** Tracker detail (route): the sheets over it, and Undo after removing a rule (R49) or hiding a payment. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerDetailRoute(onBack: () -> Unit, onSeeAll: () -> Unit, vm: TrackerDetailViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    var sheets by rememberSaveable(stateSaver = OpenSheets.Saver) { mutableStateOf(OpenSheets()) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun offerUndo(message: String, undo: () -> Unit) {
        scope.launch {
            if (snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) undo()
        }
    }
    Box(Modifier.fillMaxSize()) {
        TrackerDetailContent(
            ui,
            TrackerDetailActions(
                onBack = onBack,
                onRange = vm::setRange,
                onSelect = vm::select,
                onLine = vm::selectLine,
                onEdit = vm::toggleEditing,
                onRemoveRule = { chip ->
                    vm.removeRule(chip.id) { removed ->
                        val what = if (removed.row.origin == RuleOrigin.SYSTEM) "Built-in rule switched off" else "Rule removed"
                        offerUndo(what) { vm.restoreRule(removed) }
                    }
                },
                onAddRule = { adding = true },
                onRename = { renaming = true },
                onStopTracking = { vm.stopTracking(onBack) },
                onOpenTx = { sheets = sheets.copy(payment = it) },
                onPickCategory = { sheets = sheets.copy(picker = it) },
                onSeeAll = {
                    vm.openAll()
                    onSeeAll()
                },
            ),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(Spacing.l))
    }
    if (adding) {
        val close = {
            adding = false
            vm.clearPreview()
        }
        AddRuleSheet(ui.category?.name.orEmpty(), preview, onCount = vm::previewRule, onSave = { n, a -> vm.addRule(n, a, close) }, onClose = close)
    }
    if (renaming) RenameSheet(ui.category?.name.orEmpty(), onSave = { name, done -> vm.rename(name, done) }, onClose = { renaming = false })
    TransactionSheetHost(
        code = sheets.payment,
        onDismiss = { sheets = sheets.copy(payment = null) },
        onHidden = { code ->
            sheets = sheets.afterHide()
            offerUndo("Payment hidden") { vm.undoHide(code) }
        },
        onChangeCategory = { sheets = sheets.copy(picker = it) },
    )
    CategoryPickerHost(sheets.picker, onDismiss = { sheets = sheets.copy(picker = null) })
}

