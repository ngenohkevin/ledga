package com.ledga.app.ui.activity

import com.ledga.app.ui.lines.LinePicker
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.charts.ColumnChart
import com.ledga.app.ui.design.charts.ShareBar
import com.ledga.app.ui.design.components.AmountText
import com.ledga.app.ui.design.components.DeltaBadge
import com.ledga.app.ui.design.components.DeltaTone
import com.ledga.app.ui.design.components.EmptyState
import com.ledga.app.ui.design.components.LedgaCard
import com.ledga.app.ui.design.components.RowDivider
import com.ledga.app.ui.design.components.SectionHeader
import com.ledga.app.ui.design.components.SkeletonRow
import com.ledga.app.ui.design.format.AmountFormat
import com.ledga.app.ui.design.format.DateLabels
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.CategoryPalette
import com.ledga.app.ui.design.tokens.Sizes
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.core.money.Decimals
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Everything the Spending segment can do; `ActivityTab` wires it to the ViewModel. */
data class SpendingActions(
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onSelect: (Int) -> Unit = {},
    val onByGroup: (Boolean) -> Unit = {},
    val onShare: (ShareRow) -> Unit = {},
    val onLine: (Long?) -> Unit = {},
)

/**
 * Activity › Spending (spec §10.4, mockup `spending`):
 * - the month stepper;
 * - the spent card with its delta, fees and money in;
 * - the 8–12 month chart (the selected month strong, the current one hatched);
 * - "Where it went" by category or group.
 */
@Composable
fun SpendingPane(ui: SpendingUi, actions: SpendingActions, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    val month = ui.month
    if (!ui.loaded || month == null) {
        Column(modifier.fillMaxWidth().padding(horizontal = Spacing.screen)) { repeat(4) { SkeletonRow() } }
        return
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        LinePicker(ui.line, actions.onLine)
        MonthStepper(month, ui.canGoBack, ui.canGoForward, actions)
        LedgaCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                val name = month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                Text(if (ui.isCurrent) "Spent so far in $name" else "Spent in $name", Modifier.weight(1f), style = LedgaType.caption, color = c.muted)
                ui.deltaPercent?.takeIf { it != 0 }?.let { Delta(it, ui.comparedWith) }
            }
            AmountText(ui.totals.spentCents, Modifier.padding(top = 2.dp), style = LedgaType.amountL, decimals = Decimals.NEVER)
            Text("incl. ${ksh(ui.totals.feeCents)} fees · received ${ksh(ui.totals.inCents)}", style = LedgaType.caption, color = c.muted)
            ColumnChart(
                bars = ui.bars,
                colors = listOf(c.chartPrimary),
                summary = chartSummary(ui),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.m),
                selectedIndex = ui.selectedIndex,
                onSelect = actions.onSelect,
                dimUnselected = true,
                height = 120.dp,
            )
        }
        SectionHeader("Where it went", actionLabel = if (ui.byGroup) "By category" else "By group", onAction = { actions.onByGroup(!ui.byGroup) })
        if (ui.shares.isEmpty()) {
            LedgaCard(Modifier.fillMaxWidth()) {
                EmptyState("fluent_coin", "Nothing spent in ${DateLabels.monthYear(month)}", "Payments made this month show up here, by category.")
            }
        } else {
            LedgaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                ui.shares.forEachIndexed { i, row ->
                    if (i > 0) RowDivider(Modifier.padding(horizontal = Spacing.m))
                    ShareBar(
                        iconKey = row.icon3d,
                        name = row.name,
                        amount = ksh(row.cents),
                        fraction = row.fraction,
                        color = CategoryPalette.resolve(row.colorKey, row.color, row.colorDark).pick(c.isDark),
                        caption = shareCaption(row),
                        onClick = { actions.onShare(row) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthStepper(month: YearMonth, canBack: Boolean, canForward: Boolean, actions: SpendingActions) {
    val c = LedgaTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StepButton(Ph.CaretLeft, "Previous month", canBack, actions.onPrevious)
        Text(
            DateLabels.monthYear(month),
            Modifier.weight(1f).semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
            style = LedgaType.section,
            color = c.ink,
            textAlign = TextAlign.Center,
        )
        StepButton(Ph.CaretRight, "Next month", canForward, actions.onNext)
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LedgaTheme.colors
    IconButton(onClick = onClick, enabled = enabled) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(c.surface).border(Sizes.hairline, c.line, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = if (enabled) c.ink2 else c.faint, modifier = Modifier.size(Sizes.iconSmall))
        }
    }
}

/** "▲ 9% vs Aug": up is Bad (red), down is Good (green); TalkBack hears it in words. */
@Composable
private fun Delta(percent: Int, comparedWith: String) {
    val up = percent > 0
    val arrow = if (up) Char(0x25B2) else Char(0x25BC)
    DeltaBadge(
        "$arrow ${abs(percent)}% vs $comparedWith",
        if (up) DeltaTone.Bad else DeltaTone.Good,
        speech = "${abs(percent)} percent ${if (up) "more" else "less"} than $comparedWith",
    )
}

private fun ksh(cents: Long): String = "${AmountFormat.CURRENCY} ${AmountFormat.plain(cents, Decimals.NEVER)}"

/** "32% · 23 payments"; a sliver reads "<1%" rather than "0%". */
private fun shareCaption(row: ShareRow): String {
    val percent = (row.fraction * 100).roundToInt()
    val share = if (percent == 0 && row.cents > 0) "<1%" else "$percent%"
    return "$share · ${row.count} ${if (row.count == 1) "payment" else "payments"}"
}

/** The chart's TalkBack summary (`app/DESIGN.md`: every ColumnChart gets one). */
private fun chartSummary(ui: SpendingUi): String {
    val first = ui.months.firstOrNull() ?: return "Spending by month"
    val selected = ui.selectedIndex?.let { i -> " ${DateLabels.monthYear(ui.months[i])}: ${ksh(ui.bars[i].total)}." }.orEmpty()
    return "Spending by month from ${DateLabels.monthYear(first)} to ${DateLabels.monthYear(ui.months.last())}.$selected"
}
