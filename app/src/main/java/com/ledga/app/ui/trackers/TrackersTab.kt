package com.ledga.app.ui.trackers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.ui.design.charts.ChartLegend
import com.ledga.app.ui.design.charts.ColumnChart
import com.ledga.app.ui.design.components.AmountText
import com.ledga.app.ui.design.components.EmptyState
import com.ledga.app.ui.design.components.Leading
import com.ledga.app.ui.design.components.LedgaCard
import com.ledga.app.ui.design.components.LedgaModalSheet
import com.ledga.app.ui.design.components.LinkButton
import com.ledga.app.ui.design.components.ListRow
import com.ledga.app.ui.design.components.RowDivider
import com.ledga.app.ui.design.components.RowTrailing
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
import com.ledga.core.model.CategoryGroup
import com.ledga.core.money.Decimals
import java.util.Locale

/** What the Trackers tab's taps do. Every default does nothing, for screenshots and tests. */
data class TrackersActions(
    val onRange: (TrackerRange) -> Unit = {},
    val onLine: (Long?) -> Unit = {},
    val onOpen: (String) -> Unit = {},
    val onTrackNew: () -> Unit = {},
)

/**
 * Trackers (spec §10.4, mockup `trackers`), one scrolling list:
 * - the title with "+";
 * - the line chip (R47);
 * - the stacked month-by-month chart with the average and a legend;
 * - one row per tracker;
 * - "Track another category".
 */
@Composable
fun TrackersContent(ui: TrackersUi, actions: TrackersActions, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        item(key = "title") { TrackersTitle(actions.onTrackNew) }
        if (ui.line.showChip) item(key = "line") { LinePicker(ui.line, actions.onLine) }
        when {
            !ui.loaded -> items(3) { SkeletonRow() }
            ui.trackers.isEmpty() -> item(key = "empty") {
                EmptyState(
                    "fluent_bar_chart",
                    "Nothing tracked yet",
                    "Track bills and running costs to see them month by month.",
                    actionLabel = "Track a category",
                    onAction = actions.onTrackNew,
                )
            }
            else -> {
                item(key = "chart") { StackedCard(ui, actions) }
                item(key = "list") { TrackerList(ui, actions) }
            }
        }
    }
}

@Composable
private fun TrackersTitle(onAdd: () -> Unit) {
    val c = LedgaTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Trackers", Modifier.semantics { heading() }, style = LedgaType.screenTitle, color = c.ink)
            Text("Bills and running costs, month by month", style = LedgaType.caption, color = c.muted)
        }
        IconButton(onClick = onAdd) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.surface).border(Sizes.hairline, c.line, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Ph.Plus, contentDescription = "Track a category", tint = c.ink2, modifier = Modifier.size(Sizes.iconSmall))
            }
        }
    }
}

@Composable
private fun StackedCard(ui: TrackersUi, actions: TrackersActions) {
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
private fun TrackerList(ui: TrackersUi, actions: TrackersActions) {
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

/** The Trackers tab (route); "Track a category" opens over it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackersTab(onOpen: (String) -> Unit, vm: TrackersViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    TrackersContent(ui, TrackersActions(onRange = vm::setRange, onLine = vm::selectLine, onOpen = onOpen, onTrackNew = { picking = true }))
    if (picking) {
        LedgaModalSheet(onDismiss = { picking = false }, title = "Track a category") {
            TrackCategoryContent(
                ui.untracked,
                onTrack = {
                    vm.track(it)
                    picking = false
                },
            )
        }
    }
}
