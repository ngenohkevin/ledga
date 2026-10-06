package com.ledga.app.ui.home

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ledga.app.data.derive.FulizaStatus
import com.ledga.app.data.trackers.TrackerSummary
import com.ledga.app.ui.design.charts.MiniBars
import com.ledga.app.ui.design.charts.SparkBars
import com.ledga.app.ui.design.components.AmountText
import com.ledga.app.ui.design.components.AnimatedAmount
import com.ledga.app.ui.design.components.CategoryIcon
import com.ledga.app.ui.design.components.ChangeBadge
import com.ledga.app.ui.design.components.InitialAvatar
import com.ledga.app.ui.design.components.LedgaCard
import com.ledga.app.ui.design.components.LedgaTile
import com.ledga.app.ui.design.components.RowDivider
import com.ledga.app.ui.design.components.SectionHeader
import com.ledga.app.ui.design.components.SegmentedControl
import com.ledga.app.ui.design.components.WellSize
import com.ledga.app.ui.design.format.DateLabels
import com.ledga.app.ui.design.icons.Fluent
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.CategoryPalette
import com.ledga.app.ui.design.tokens.Radii
import com.ledga.app.ui.design.tokens.Sizes
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.app.ui.lines.LinePicker
import com.ledga.app.ui.trackers.TrackerText
import com.ledga.app.ui.tx.TxText
import com.ledga.core.money.Decimals
import com.ledga.core.time.PeriodType
import java.time.LocalDate
import com.ledga.app.ui.design.components.TxRow as TransactionRow

/** The header (spec §10.4, R55): You's name with the avatar, or the greeting alone; the search button. */
@Composable
internal fun HomeHeader(ui: HomeUi, actions: HomeActions) {
    val c = LedgaTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.s, top = Spacing.m, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val name = ui.name
        if (name != null) {
            Box(
                Modifier.size(Sizes.touchTarget).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Open You", onClick = actions.onProfile),
                contentAlignment = Alignment.Center,
            ) { InitialAvatar(name, inflow = true) }
            Column(Modifier.weight(1f).padding(start = Spacing.s).semantics(mergeDescendants = true) { heading() }) {
                Text(ui.greeting, style = LedgaType.caption, color = c.muted)
                Text(name, style = LedgaType.section, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Text(ui.greeting, Modifier.weight(1f).padding(vertical = Spacing.s).semantics { heading() }, style = LedgaType.screenTitle, color = c.ink)
        }
        IconButton(onClick = actions.onSearch) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.surface).border(Sizes.hairline, c.line, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Ph.MagnifyingGlass, contentDescription = "Search payments", tint = c.ink2, modifier = Modifier.size(Sizes.iconSmall))
            }
        }
    }
}

/**
 * The balance card (spec §10.4): "M-Pesa balance", the line chip (R47), the balance, when it was stated (under All lines
 * on a two-line phone, each line's balance and time instead), the Fuliza strip.
 */
@Composable
internal fun BalanceCard(ui: HomeUi, actions: HomeActions) {
    val c = LedgaTheme.colors
    LedgaCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("M-Pesa balance", Modifier.weight(1f), style = LedgaType.label, color = c.muted)
            LinePicker(ui.line, actions.onLine)
        }
        val balance = ui.balance
        if (balance == null) {
            Text("No balance yet", Modifier.padding(top = Spacing.s), style = LedgaType.amountM, color = c.ink)
            Text("It shows with your next M-Pesa message.", style = LedgaType.caption, color = c.muted)
        } else {
            AnimatedAmount(balance.cents, Modifier.padding(top = Spacing.xs))
            val today = ui.today ?: DateLabels.nairobiDate(balance.updatedAt)
            if (ui.balanceLines.isEmpty()) {
                Text(HomeText.updated(balance.updatedAt, today), style = LedgaType.caption, color = c.muted)
            } else {
                ui.balanceLines.forEach { Text(HomeText.lineBalance(it, today), style = LedgaType.caption, color = c.muted) }
            }
        }
        ui.fuliza?.let { FulizaStrip(it, ui.today, actions.onFuliza, Modifier.padding(top = Spacing.m)) }
    }
}

/** R58: owed in red on dangerSoft with the due date, or "nothing owed" on plate; a tap opens the Fuliza sheet. */
@Composable
internal fun FulizaStrip(status: FulizaStatus, today: LocalDate?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    val owed = status.outstanding.cents > 0
    val (title, detail) = HomeText.fuliza(status, today)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.stat))
            .background(if (owed) c.dangerSoft else c.plate)
            .clickable(role = Role.Button, onClickLabel = "Show Fuliza history", onClick = onClick)
            .padding(horizontal = Spacing.m, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Image(painterResource(Fluent.resOf("fluent_credit_card")), contentDescription = null, modifier = Modifier.size(Sizes.iconLarge))
        Column(Modifier.weight(1f)) {
            Text(title, style = LedgaType.bodyStrong, color = if (owed) c.danger else c.ink)
            if (detail != null) Text(detail, style = LedgaType.caption, color = if (owed) c.ink2 else c.muted)
        }
        Icon(Ph.CaretRightBold, contentDescription = null, tint = if (owed) c.ink2 else c.muted, modifier = Modifier.size(Sizes.iconSmall))
    }
}

private val PERIODS = listOf(PeriodType.WEEK, PeriodType.MONTH, PeriodType.YEAR)

/** The spending card (spec §10.4, R57). The segments switch the period; anywhere else opens Activity › Spending. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpendingCard(card: SpendingCardUi, actions: HomeActions) {
    val c = LedgaTheme.colors
    LedgaCard(Modifier.fillMaxWidth(), onClick = actions.onSpending) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(card.title, Modifier.weight(1f), style = LedgaType.label, color = c.muted)
            SegmentedControl(listOf("Week", "Month", "Year"), PERIODS.indexOf(card.type), { actions.onPeriod(PERIODS[it]) }, Modifier.weight(1.3f))
        }
        AmountText(card.spentCents, Modifier.padding(top = Spacing.s), style = LedgaType.amountL, decimals = Decimals.NEVER)
        // Side by side while both fit, else the badge moves under the fees line: squeezed, the line broke into a sliver.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text("incl. ${HomeText.ksh(card.feeCents)} fees · ${card.span}", Modifier.padding(end = Spacing.s), style = LedgaType.caption, color = c.muted)
            card.deltaPercent?.takeIf { it != 0 }?.let { ChangeBadge(it, card.comparedWith) }
        }
        MiniBars(card.bars, Modifier.padding(top = Spacing.m), inProgressIndex = card.bars.lastIndex, contentDescription = card.summary)
        BarLabels(card.labels, card.shortLabels, emphasized = card.bars.lastIndex, gap = 8.dp)
        RowDivider(Modifier.padding(vertical = Spacing.m))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Figure("Received", card.inCents, Modifier.padding(end = Spacing.m), TextAlign.Start)
            card.averageCents?.let { Figure(card.averageLabel, it, Modifier, TextAlign.End) }
        }
    }
}

/** "Received Ksh 12,500": the label muted, the amount in bold ink. */
@Composable
private fun Figure(label: String, cents: Long, modifier: Modifier, align: TextAlign) {
    val c = LedgaTheme.colors
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = c.muted)) { append("$label ") }
            withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.Bold)) { append(HomeText.ksh(cents)) }
        },
        modifier,
        style = LedgaType.caption,
        textAlign = align,
    )
}

/** The labels under `MiniBars`, in the same slots ([gap]): full while they all fit, else the short ones (R57). TalkBack reads the bars' summary instead. */
@Composable
private fun BarLabels(labels: List<String>, shortLabels: List<String>, emphasized: Int, gap: Dp) {
    val c = LedgaTheme.colors
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = Spacing.xs).clearAndSetSemantics { }) {
        val count = labels.size.coerceAtLeast(1)
        val slot = with(density) { (maxWidth.toPx() - gap.toPx() * (count - 1)) / count }
        val bold = LedgaType.caption.copy(fontWeight = FontWeight.ExtraBold)
        val fits = labels.all { measurer.measure(it, bold, maxLines = 1, softWrap = false, density = density).size.width <= slot }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            (if (fits) labels else shortLabels).forEachIndexed { i, text ->
                Text(
                    text,
                    Modifier.weight(1f),
                    style = if (i == emphasized) bold else LedgaType.caption,
                    color = if (i == emphasized) c.ink else c.muted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** The trackers strip (spec §10.4): one tile per tracked category, scrolling sideways. */
@Composable
internal fun TrackersStrip(trackers: List<TrackerSummary>, actions: HomeActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionHeader("Trackers", actionLabel = "See all", onAction = actions.onAllTrackers)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            items(trackers, key = { it.category.key }) { s -> TrackerTile(s) { actions.onTracker(s.category.key) } }
        }
    }
}

/**
 * A tile is 13 caption font sizes wide: 156 dp at normal text. Android 14 grows a large dp length far less than 12 sp
 * text, so a fixed width cut "usually by the 12th" at 1.3×; a width in the caption's own size grows with it.
 */
private const val TILE_EMS = 13f

@Composable
private fun TrackerTile(s: TrackerSummary, onClick: () -> Unit) {
    val c = LedgaTheme.colors
    val color = CategoryPalette.resolve(s.category.key, s.category.color, s.category.colorDark).pick(c.isDark)
    val width = with(LocalDensity.current) { LedgaType.caption.fontSize.toDp() } * TILE_EMS
    LedgaTile(Modifier.width(width), onClick = onClick) {
        CategoryIcon(s.category.icon3d, contentDescription = null, size = WellSize.Small)
        Text(s.category.name, Modifier.padding(top = Spacing.s), style = LedgaType.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(TrackerText.ksh(s.thisMonth.total.cents), style = LedgaType.amountM, color = c.ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Text(TrackerText.tileCaption(s), style = LedgaType.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        SparkBars(s.months.takeLast(6).map { it.total.cents }, color, Modifier.padding(top = Spacing.s))
    }
}

/** Recent (spec §10.4): the last five payments; a tap opens the payment, a long press the picker (R56: no balance). */
@Composable
internal fun RecentCard(ui: HomeUi, actions: HomeActions) {
    val c = LedgaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionHeader("Recent", actionLabel = "See all", onAction = actions.onAllRecent)
        LedgaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
            if (ui.recent.isEmpty()) Text("No payments on this line yet.", Modifier.padding(Spacing.l), style = LedgaType.body, color = c.muted)
            ui.recent.forEachIndexed { i, row ->
                if (i > 0) RowDivider(Modifier.padding(horizontal = Spacing.m))
                val category = ui.categories[row.categoryKey]
                val today = ui.today ?: DateLabels.nairobiDate(row.occurredAt)
                TransactionRow(
                    leading = TxText.leading(row, category?.icon3d ?: "fluent_package"),
                    title = TxText.title(row),
                    subtitle = TxText.subtitle(row, category?.name ?: "Other"),
                    subtitleTail = HomeText.rowTime(row.occurredAt, today),
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
