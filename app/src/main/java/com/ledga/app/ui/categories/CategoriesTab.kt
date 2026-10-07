package com.ledga.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.trackers.CategoryMeasure
import com.ledga.app.ui.app.DetailFrame
import com.ledga.app.ui.app.GroupLabel
import com.ledga.app.ui.design.charts.ChartLegend
import com.ledga.app.ui.design.charts.ColumnChart
import com.ledga.app.ui.design.components.AddChip
import com.ledga.app.ui.design.components.AmountText
import com.ledga.app.ui.design.components.ChoiceChip
import com.ledga.app.ui.design.components.EmptyState
import com.ledga.app.ui.design.components.Leading
import com.ledga.app.ui.design.components.LedgaCard
import com.ledga.app.ui.design.components.LedgaModalSheet
import com.ledga.app.ui.design.components.LinkButton
import com.ledga.app.ui.design.components.ListRow
import com.ledga.app.ui.design.components.PrimaryPill
import com.ledga.app.ui.design.components.RowDivider
import com.ledga.app.ui.design.components.RowTrailing
import com.ledga.app.ui.design.components.SearchField
import com.ledga.app.ui.design.components.SectionHeader
import com.ledga.app.ui.design.components.SegmentedControl
import com.ledga.app.ui.design.components.SkeletonRow
import com.ledga.app.ui.design.components.ValueRow
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.CategoryPalette
import com.ledga.app.ui.design.tokens.Sizes
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.app.ui.lines.LinePicker
import com.ledga.app.ui.trackers.TrackerText
import com.ledga.app.ui.tx.TxText
import com.ledga.core.model.CategoryGroup
import com.ledga.core.money.Decimals
import java.util.Locale

/** What the Categories tab says (4e D2, R92, R95, R97). */
object CategoriesTabText {
    fun monthLine(m: CategoryMeasure): String = when (m) {
        CategoryMeasure.SPENT -> "Spent this month"
        CategoryMeasure.RECEIVED -> "Received this month"
        CategoryMeasure.MOVED -> "Moved this month"
    }

    fun createLabel(query: String): String = "Create \"${query.trim()}\""

    fun archivedLabel(n: Int): String = "Archived ($n)"
}

/** What the Categories tab's taps do. Every default does nothing, for screenshots and tests. */
data class CategoriesTabActions(
    val onRange: (TrackerRange) -> Unit = {},
    val onLine: (Long?) -> Unit = {},
    val onOpen: (String) -> Unit = {},
    val onTrackNew: () -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onNew: () -> Unit = {},
    val onCreate: (String) -> Unit = {},
)

/**
 * The Categories tab (4e D2), one scrolling list:
 * - the title with "+" (New category, R92) and the search;
 * - searching: the matches, or "Create '<query>'";
 * - otherwise: the line chip, the trackers' stacked chart and rows (4c), All categories by group with this month's amount
 *   (R95), and Archived (N), folded until opened (R97).
 */
@Composable
fun CategoriesTabContent(ui: CategoriesTabUi, actions: CategoriesTabActions, modifier: Modifier = Modifier) {
    var archivedOpen by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        item(key = "title") { TabTitle(actions.onNew) }
        item(key = "search") { SearchField(ui.query, actions.onQuery, "Search categories", Modifier.fillMaxWidth()) }
        when {
            !ui.loaded -> items(3) { SkeletonRow() }
            ui.searching && ui.results.isEmpty() -> item(key = "none") {
                EmptyState(
                    "fluent_magnifying_glass_tilted_left",
                    "No category called \"${ui.query.trim()}\"",
                    "Make it, then file payments into it.",
                    actionLabel = CategoriesTabText.createLabel(ui.query),
                    onAction = { actions.onCreate(ui.query.trim()) },
                )
            }
            ui.searching -> item(key = "results") { RowsCard(ui.results, actions, markArchived = true) }
            else -> {
                if (ui.line.showChip) item(key = "line") { LinePicker(ui.line, actions.onLine) }
                if (ui.trackers.isEmpty()) {
                    item(key = "no-trackers") { NoTrackers(actions) }
                } else {
                    item(key = "chart") { StackedCard(ui, actions) }
                    item(key = "trackers") { TrackerList(ui, actions) }
                }
                item(key = "all") { SectionHeader("All categories") }
                ui.groups.forEach { g ->
                    item(key = "g-${g.group.name}") {
                        Column {
                            GroupLabel(g.group.displayName)
                            RowsCard(g.rows, actions, markArchived = false)
                        }
                    }
                }
                if (ui.archived.isNotEmpty()) {
                    item(key = "archived") {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            ArchivedToggle(ui.archived.size, archivedOpen) { archivedOpen = it }
                            if (archivedOpen) RowsCard(ui.archived, actions, markArchived = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabTitle(onNew: () -> Unit) {
    val c = LedgaTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Categories", Modifier.semantics { heading() }, style = LedgaType.screenTitle, color = c.ink)
            Text("Your trackers, then every category", style = LedgaType.caption, color = c.muted)
        }
        IconButton(onClick = onNew) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.surface).border(Sizes.hairline, c.line, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Ph.Plus, contentDescription = "New category", tint = c.ink2, modifier = Modifier.size(Sizes.iconSmall))
            }
        }
    }
}

@Composable
private fun NoTrackers(actions: CategoriesTabActions) {
    LedgaCard(Modifier.fillMaxWidth()) {
        Text("Nothing tracked yet. Track bills and running costs to see them month by month.", style = LedgaType.body, color = LedgaTheme.colors.muted)
        LinkButton("+ Track a category", actions.onTrackNew)
    }
}

@Composable
private fun RowsCard(rows: List<CategoryLineUi>, actions: CategoriesTabActions, markArchived: Boolean) {
    LedgaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
        rows.forEachIndexed { i, r ->
            if (i > 0) RowDivider(Modifier.padding(horizontal = Spacing.m))
            val subtitle = if (markArchived && r.category.archived) "Archived" else CategoriesTabText.monthLine(r.measure)
            val amount = TrackerText.ksh(r.monthCents)
            ValueRow(
                leading = Leading.Icon(r.category.icon3d),
                title = r.category.name,
                subtitle = subtitle,
                value = amount,
                speech = "${r.category.name}, $amount, $subtitle",
                onClick = { actions.onOpen(r.category.key) },
                onClickLabel = "Open category",
            )
        }
    }
}

/** R97: "Archived (N)", a disclosure row; open or not is kept for the visit. */
@Composable
private fun ArchivedToggle(count: Int, open: Boolean, onOpen: (Boolean) -> Unit) {
    val c = LedgaTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = open, role = Role.Button, onValueChange = onOpen)
            .semantics { stateDescription = if (open) "Shown" else "Hidden" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(CategoriesTabText.archivedLabel(count), Modifier.weight(1f), style = LedgaType.label, color = c.muted)
        Icon(if (open) Ph.CaretUp else Ph.CaretDown, contentDescription = null, tint = c.muted, modifier = Modifier.size(Sizes.iconSmall))
    }
}

/** R92: a name (1–30 characters) and a group, Everyday first; "Create '<query>'" fills the name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewCategoryContent(name: String, onName: (String) -> Unit, group: CategoryGroup, onGroup: (CategoryGroup) -> Unit, onSave: () -> Unit, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        OutlinedTextField(name, { onName(it.take(TransactionEdits.CATEGORY_NAME_MAX)) }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
        Text("Group", Modifier.padding(top = Spacing.l, bottom = Spacing.s), style = LedgaType.label, color = c.muted)
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            CategoryGroup.entries.forEach { g -> ChoiceChip(g.displayName, selected = g == group, onClick = { onGroup(g) }, role = Role.RadioButton) }
        }
        PrimaryPill("Create", onSave, Modifier.fillMaxWidth().padding(top = Spacing.l), enabled = name.isNotBlank())
    }
}

/** The Categories tab (route): "Track a category" and New category open over it; a new category opens its page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesTab(onOpen: (String) -> Unit, vm: CategoriesTabViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf<String?>(null) }
    CategoriesTabContent(
        ui,
        CategoriesTabActions(
            onRange = vm::setRange,
            onLine = vm::selectLine,
            onOpen = onOpen,
            onTrackNew = { picking = true },
            onQuery = vm::setQuery,
            onNew = { creating = "" },
            onCreate = { creating = it },
        ),
    )
    if (picking) {
        LedgaModalSheet(onDismiss = { picking = false }, title = "Track a category") {
            TrackCategoryContent(ui.untracked, onTrack = {
                vm.track(it)
                picking = false
            })
        }
    }
    creating?.let { prefill ->
        var name by rememberSaveable(prefill) { mutableStateOf(prefill) }
        var group by rememberSaveable(prefill) { mutableStateOf(CategoryGroup.EVERYDAY) }
        LedgaModalSheet(onDismiss = { creating = null }, title = "New category") {
            NewCategoryContent(name, { name = it }, group, { group = it }, onSave = {
                vm.create(name, group) { key ->
                    creating = null
                    vm.setQuery("")
                    onOpen(key)
                }
            })
        }
    }
}

@Composable
private fun StackedCard(ui: CategoriesTabUi, actions: CategoriesTabActions) {
    val c = LedgaTheme.colors
    val colors = ui.trackers.map { CategoryPalette.resolve(it.category.key, it.category.color, it.category.colorDark).pick(c.isDark) }
    val average = ui.averageCents
    LedgaCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(ui.heading, Modifier.weight(1f), style = LedgaType.label, color = c.muted)
            SegmentedControl(TrackerRange.entries.map { it.label }, ui.range.ordinal, { actions.onRange(TrackerRange.entries[it]) }, Modifier.weight(1.2f))
        }
        AmountText(average ?: ui.thisMonthCents, Modifier.padding(top = Spacing.s), style = LedgaType.amountL, decimals = Decimals.NEVER)
        Text(
            if (average != null) "avg per month · this month so far ${TrackerText.ksh(ui.thisMonthCents)}" else "so far this month",
            style = LedgaType.caption,
            color = c.muted,
        )
        ColumnChart(
            bars = ui.bars,
            colors = colors,
            summary = "Tracked costs by month: " + ui.bars.joinToString(", ") { it.speech },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.m),
            average = average,
            height = 140.dp,
        )
        ChartLegend(ui.trackers.mapIndexed { i, t -> t.category.name to colors[i] }, Modifier.padding(top = Spacing.s))
    }
}

@Composable
private fun TrackerList(ui: CategoriesTabUi, actions: CategoriesTabActions) {
    val today = ui.today ?: return
    LedgaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
        ui.trackers.forEachIndexed { i, s ->
            if (i > 0) RowDivider(Modifier.padding(horizontal = Spacing.m))
            val context = TrackerText.rowContext(s, today)
            val amount = TrackerText.ksh(s.thisMonth.total.cents)
            ValueRow(
                leading = Leading.Icon(s.category.icon3d),
                title = s.category.name,
                subtitle = context,
                value = amount,
                detail = TrackerText.rowDetail(s),
                speech = "${s.category.name}, $amount this month, $context",
                onClick = { actions.onOpen(s.category.key) },
                onClickLabel = "Open tracker",
            )
        }
        if (ui.untracked.isNotEmpty()) {
            RowDivider(Modifier.padding(horizontal = Spacing.m))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LinkButton("+ Track another category", actions.onTrackNew) }
        }
    }
}

/** "Track a category" (R50): untracked spending categories, by group; a tap tracks one. */
@Composable
fun TrackCategoryContent(categories: List<CategoryRow>, onTrack: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    if (categories.isEmpty()) {
        EmptyState("fluent_check_mark_button", "Everything is tracked", "Every spending category already has a tracker.", modifier)
        return
    }
    LazyColumn(modifier.fillMaxWidth()) {
        CategoryGroup.entries.forEach { group ->
            val inGroup = categories.filter { it.groupKey == group }
            if (inGroup.isNotEmpty()) {
                item(key = "group-${group.name}") {
                    Text(
                        group.displayName.uppercase(Locale.ENGLISH),
                        Modifier.padding(top = Spacing.m, bottom = Spacing.xs).semantics { heading() },
                        style = LedgaType.overline,
                        color = c.muted,
                    )
                }
                items(inGroup, key = { it.key }) { cat -> ListRow(cat.name, iconKey = cat.icon3d, trailing = RowTrailing.None, onClick = { onTrack(cat.key) }) }
            }
        }
    }
}
