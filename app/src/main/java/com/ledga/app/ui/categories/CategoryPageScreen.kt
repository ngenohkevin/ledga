package com.ledga.app.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.trackers.CategoryMeasure
import com.ledga.app.ui.app.DetailFrame
import com.ledga.app.ui.app.RoundButton
import com.ledga.app.ui.design.charts.ChartTooltip
import com.ledga.app.ui.design.charts.ColumnChart
import com.ledga.app.ui.design.components.AddChip
import com.ledga.app.ui.design.components.Banner
import com.ledga.app.ui.design.components.BannerTone
import com.ledga.app.ui.design.components.EmptyState
import com.ledga.app.ui.design.components.LedgaCard
import com.ledga.app.ui.design.components.LedgaModalSheet
import com.ledga.app.ui.design.components.LedgaSwitch
import com.ledga.app.ui.design.components.Leading
import com.ledga.app.ui.design.components.ListRow
import com.ledga.app.ui.design.components.PrimaryPill
import com.ledga.app.ui.design.components.RowDivider
import com.ledga.app.ui.design.components.RowTrailing
import com.ledga.app.ui.design.components.SectionHeader
import com.ledga.app.ui.design.components.SegmentedControl
import com.ledga.app.ui.design.components.SkeletonRow
import com.ledga.app.ui.design.components.StatTile
import com.ledga.app.ui.design.components.TxRow as TransactionRow
import com.ledga.app.ui.design.components.ValueRow
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Sizes
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.tokens.CategoryPalette
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.app.ui.lines.LinePicker
import com.ledga.app.ui.rules.AddRuleSheet
import com.ledga.app.ui.rules.RenameSheet
import com.ledga.app.ui.trackers.TrackerText
import com.ledga.app.ui.tx.CategoryPickerHost
import com.ledga.app.ui.tx.OpenSheets
import com.ledga.app.ui.tx.TransactionSheetHost
import com.ledga.app.ui.tx.TxText
import com.ledga.core.model.Categories
import com.ledga.core.time.PeriodType
import kotlinx.coroutines.launch

/** What a category's page does. Every default does nothing, for screenshots and tests. */
data class CategoryPageActions(
    val onBack: () -> Unit = {},
    val onRange: (DetailRange) -> Unit = {},
    val onSelect: (Int) -> Unit = {},
    val onLine: (Long?) -> Unit = {},
    val onRename: () -> Unit = {},
    val onTracked: (Boolean) -> Unit = {},
    val onOpenTx: (String) -> Unit = {},
    val onPickCategory: (String) -> Unit = {},
    val onSeeAll: () -> Unit = {},
    val onPlace: (TopPlaceUi) -> Unit = {},
    val onIcon: () -> Unit = {},
    val onColour: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onArchive: () -> Unit = {},
    val onBringBack: () -> Unit = {},
    val onRuleEnabled: (Long, Boolean) -> Unit = { _, _ -> },
    val onDeleteRule: (RuleUi) -> Unit = {},
    val onAddRule: () -> Unit = {},
)

/**
 * A category's page (4e spec §3.2), pushed full screen:
 * - the top bar (Back, icon, name, ⋮ Rename and Track / Stop tracking);
 * - the line chip, the chart card (6M / 12M / All, average, tooltip), the tiles, Top places and Payments — or, with no
 *   payment at all, one empty state;
 * - Settings: icon, colour, Reset to default, Show on Trackers, the rules, Archive.
 */
@Composable
fun CategoryPageContent(ui: CategoryPageUi, actions: CategoryPageActions, modifier: Modifier = Modifier) {
    val category = ui.category
    DetailFrame(
        category?.name.orEmpty(),
        onBack = actions.onBack,
        modifier = modifier,
        iconKey = category?.icon3d,
        actions = { if (category != null) PageMenu(ui, category, actions) },
    ) {
        when {
            !ui.loaded -> Column(Modifier.padding(horizontal = Spacing.screen)) { repeat(4) { SkeletonRow() } }
            ui.missing || category == null -> EmptyState(
                "fluent_package",
                "This category isn't here",
                "It may belong to a backup from a newer Ledga.",
                actionLabel = "Back",
                onAction = actions.onBack,
            )
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                if (category.archived) {
                    Banner("Archived. It's hidden from the category picker, the filters and Trackers.", BannerTone.Info, actionLabel = "Bring back", onAction = actions.onBringBack)
                }
                if (ui.empty) {
                    EmptyState("fluent_bar_chart", "No payments in ${category.name} yet", CategoryPageText.emptyBody(ui.measure))
                } else {
                    LinePicker(ui.line, actions.onLine)
                    ChartCard(ui, category, actions)
                    StatTiles(ui)
                    if (ui.topPlaces.isNotEmpty()) TopPlaces(ui, actions)
                    Payments(ui, actions)
                }
                Settings(ui, category, actions)
            }
        }
    }
}

@Composable
private fun PageMenu(ui: CategoryPageUi, category: CategoryRow, actions: CategoryPageActions) {
    val c = LedgaTheme.colors
    var menu by remember { mutableStateOf(false) }
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
            if (ui.canTrack) {
                DropdownMenuItem(
                    text = { Text(if (category.tracked) "Stop tracking" else "Track", style = LedgaType.body, color = c.ink) },
                    onClick = {
                        menu = false
                        actions.onTracked(!category.tracked)
                    },
                )
            }
        }
    }
}

@Composable
private fun ChartCard(ui: CategoryPageUi, category: CategoryRow, actions: CategoryPageActions) {
    val c = LedgaTheme.colors
    val color = CategoryPalette.resolve(category.key, category.color, category.colorDark).pick(c.isDark)
    val years = ui.buckets.firstOrNull()?.period?.type == PeriodType.YEAR
    LedgaCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Column(Modifier.weight(1f)) {
                Text(if (years) "Year by year" else "Month by month", style = LedgaType.label, color = c.muted)
                Text(CategoryPageText.measureLine(ui.measure), style = LedgaType.caption, color = c.muted)
            }
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
private fun StatTiles(ui: CategoryPageUi) {
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

@Composable
private fun TopPlaces(ui: CategoryPageUi, actions: CategoryPageActions) {
    val c = LedgaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionHeader("Top places")
        LedgaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
            ui.topPlaces.forEachIndexed { i, p ->
                if (i > 0) RowDivider(Modifier.padding(horizontal = Spacing.m))
                val amount = TrackerText.ksh(p.totalCents)
                val line = CategoryPageText.placeLine(p.count)
                ValueRow(
                    leading = Leading.Avatar(p.name, inflow = ui.measure == CategoryMeasure.RECEIVED),
                    title = p.name,
                    subtitle = line,
                    value = amount,
                    speech = "${p.name}, $amount, $line",
                    onClick = { actions.onPlace(p) },
                    onClickLabel = "Show their payments",
                )
            }
        }
        Text(CategoryPageText.PLACES_NOTE, style = LedgaType.caption, color = c.muted)
    }
}

@Composable
private fun Payments(ui: CategoryPageUi, actions: CategoryPageActions) {
    val c = LedgaTheme.colors
    val today = ui.today ?: return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionHeader("Payments", actionLabel = "See all", onAction = actions.onSeeAll)
        LedgaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
            if (ui.payments.isEmpty()) Text("None on this line.", Modifier.padding(Spacing.l), style = LedgaType.body, color = c.muted)
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

@Composable
private fun Settings(ui: CategoryPageUi, category: CategoryRow, actions: CategoryPageActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionHeader("Settings")
        LedgaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
            ListRow("Icon", subtitle = CategoryText.iconName(category.icon3d), iconKey = category.icon3d, onClick = actions.onIcon)
            RowDivider(Modifier.padding(horizontal = Spacing.m))
            ListRow("Colour", subtitle = CategoryText.swatchName(category.color), onClick = actions.onColour)
            if (ui.canReset) {
                RowDivider(Modifier.padding(horizontal = Spacing.m))
                ListRow("Reset to default", subtitle = "Its own icon and colour again", trailing = RowTrailing.None, onClick = actions.onReset)
            }
            if (ui.canTrack) {
                RowDivider(Modifier.padding(horizontal = Spacing.m))
                ListRow("Show on Trackers", subtitle = "On Home and at the top of Categories", trailing = RowTrailing.Toggle(category.tracked, actions.onTracked))
            }
        }
    }
    Rules(ui, actions)
    if (ui.own && !category.archived) {
        LedgaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
            ListRow("Archive category", subtitle = "Hide it from the picker. Its payments and rules stay.", trailing = RowTrailing.None, onClick = actions.onArchive)
        }
    }
}

@Composable
private fun Rules(ui: CategoryPageUi, actions: CategoryPageActions) {
    val c = LedgaTheme.colors
    val own = ui.category?.key == Categories.OWN_ACCOUNTS
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionHeader("Rules")
        LedgaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
            if (ui.rules.isEmpty()) {
                Text(
                    if (own) "None yet. A payment's \"My own account\" switch makes one." else "No rules yet. Payments you file here yourself still count.",
                    Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                    style = LedgaType.caption,
                    color = c.muted,
                )
            }
            ui.rules.forEachIndexed { i, r ->
                if (i > 0) RowDivider(Modifier.padding(horizontal = Spacing.m))
                RuleItem(r, actions)
            }
            if (ui.canAddRule) AddChip("Add rule", actions.onAddRule, Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s))
        }
    }
}

/** R73: the whole row is the rule's switch; your own rule also has Delete. */
@Composable
private fun RuleItem(r: RuleUi, actions: CategoryPageActions) {
    val c = LedgaTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = r.enabled, role = Role.Switch, onValueChange = { actions.onRuleEnabled(r.id, it) })
            .heightIn(min = 56.dp)
            .padding(start = 14.dp, end = Spacing.s, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Column(Modifier.weight(1f)) {
            Text(r.label, style = LedgaType.bodyStrong, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(CategoryText.ruleOrigin(r), style = LedgaType.caption, color = c.muted)
        }
        if (!r.builtIn) {
            IconButton(onClick = { actions.onDeleteRule(r) }) {
                Icon(Ph.Trash, contentDescription = "Delete rule: ${r.label}", tint = c.ink2, modifier = Modifier.size(Sizes.iconSmall))
            }
        }
        LedgaSwitch(checked = r.enabled, onCheckedChange = null)
    }
}

/** The sheets a category's page opens. */
private enum class PageSheet { RENAME, ICON, COLOUR, ADD_RULE, ARCHIVE }

/** A category's page (route): its sheets, the payment sheet, and Undo after Stop tracking (R89), a deleted rule (R73) or Hide. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPageScreen(onBack: () -> Unit, onSeeAll: () -> Unit, vm: CategoryPageViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<PageSheet?>(null) }
    var sheets by rememberSaveable(stateSaver = OpenSheets.Saver) { mutableStateOf(OpenSheets()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun offerUndo(message: String, undo: () -> Unit) {
        scope.launch { if (snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) undo() }
    }
    val category = ui.category
    Box(Modifier.fillMaxSize()) {
        CategoryPageContent(
            ui,
            CategoryPageActions(
                onBack = onBack,
                onRange = vm::setRange,
                onSelect = vm::select,
                onLine = vm::selectLine,
                onRename = { sheet = PageSheet.RENAME },
                onTracked = { on ->
                    vm.setTracked(on)
                    if (!on && category != null) offerUndo("Stopped tracking ${category.name}") { vm.setTracked(true) }
                },
                onOpenTx = { sheets = sheets.copy(payment = it) },
                onPickCategory = { sheets = sheets.copy(picker = it) },
                onSeeAll = {
                    vm.openAll()
                    onSeeAll()
                },
                onPlace = {
                    vm.openPlace(it.counterpartyKey)
                    onSeeAll()
                },
                onIcon = { sheet = PageSheet.ICON },
                onColour = { sheet = PageSheet.COLOUR },
                onReset = vm::resetLooks,
                onArchive = { sheet = PageSheet.ARCHIVE },
                onBringBack = { vm.setArchived(false) },
                onRuleEnabled = vm::setRuleEnabled,
                onDeleteRule = { r -> vm.deleteRule(r.id) { removed -> offerUndo("Rule deleted") { vm.restoreRule(removed) } } },
                onAddRule = { sheet = PageSheet.ADD_RULE },
            ),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(Spacing.l))
    }
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
    if (category == null) return
    val close = { sheet = null }
    when (sheet) {
        PageSheet.RENAME -> RenameSheet(category.name, onSave = { name, done -> vm.rename(name, done) }, onClose = close)
        PageSheet.ICON -> IconPickerSheet(category.icon3d, onPick = {
            vm.setIcon(it)
            close()
        }, onDismiss = close)
        PageSheet.COLOUR -> LedgaModalSheet(onDismiss = close, title = "Colour") {
            ColourChoiceContent(category.color) {
                vm.setColor(it)
                close()
            }
        }
        PageSheet.ADD_RULE -> {
            val closeRule = {
                sheet = null
                vm.clearPreview()
            }
            AddRuleSheet(category.name, preview, onCount = vm::previewRule, onSave = { n, a -> vm.addRule(n, a, closeRule) }, onClose = closeRule)
        }
        PageSheet.ARCHIVE -> LedgaModalSheet(onDismiss = close, title = "Archive ${category.name}?") {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(CategoryText.archiveText(ui.paymentCount, ui.rules.count { it.enabled }), style = LedgaType.body, color = LedgaTheme.colors.ink2)
                PrimaryPill("Archive", {
                    vm.setArchived(true)
                    close()
                }, Modifier.fillMaxWidth().padding(top = Spacing.l))
            }
        }
        null -> Unit
    }
}
