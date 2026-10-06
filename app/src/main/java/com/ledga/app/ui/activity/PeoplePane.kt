package com.ledga.app.ui.activity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ledga.app.data.derive.PeopleDirection
import com.ledga.app.ui.design.components.EmptyState
import com.ledga.app.ui.design.components.Leading
import com.ledga.app.ui.design.components.SearchField
import com.ledga.app.ui.design.components.Segment
import com.ledga.app.ui.design.components.SegmentedControl
import com.ledga.app.ui.design.components.SkeletonRow
import com.ledga.app.ui.design.components.ValueRow
import com.ledga.app.ui.design.components.cardSegment
import com.ledga.app.ui.design.format.AmountFormat
import com.ledga.app.ui.design.format.DateLabels
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.core.money.Decimals
import java.time.LocalDate
import kotlin.math.roundToLong

/** Everything the People segment can do; `ActivityTab` wires it to the ViewModel and the person sheet. */
data class PeopleActions(
    val onDirection: (PeopleDirection) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onMinimum: (Long) -> Unit = {},
    val onOpen: (PersonRowUi) -> Unit = {},
)

/**
 * Activity › People (spec §10.4, R42):
 * - "Sent to" / "Received from";
 * - a search by name or phone digits;
 * - a minimum total;
 * - one card of people, biggest total first.
 */
@Composable
fun PeoplePane(ui: PeopleUi, actions: PeopleActions, modifier: Modifier = Modifier) {
    val received = ui.direction == PeopleDirection.RECEIVED
    val narrowed = ui.query.isNotBlank() || ui.minCents > 0
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl)) {
        item(key = "direction") {
            SegmentedControl(listOf("Sent to", "Received from"), ui.direction.ordinal, { actions.onDirection(PeopleDirection.entries[it]) }, Modifier.fillMaxWidth())
        }
        item(key = "search") { SearchField(ui.query, actions.onQuery, "Name or phone", Modifier.padding(top = Spacing.s)) }
        if (ui.maxCents > 0) item(key = "minimum") { MinimumTotal(ui, actions) }
        when {
            !ui.loaded -> items(4) { SkeletonRow() }
            ui.rows.isEmpty() -> item(key = "empty") {
                when {
                    narrowed -> EmptyState("fluent_busts_in_silhouette", "No one matches", "Try another name or a lower minimum.")
                    received -> EmptyState("fluent_busts_in_silhouette", "No one has paid you yet", "People who send you money show up here.")
                    else -> EmptyState("fluent_busts_in_silhouette", "You haven't sent money to anyone yet", "People you send money to show up here.")
                }
            }
            else -> itemsIndexed(ui.rows, key = { _, r -> r.key }) { i, r -> PersonRow(r, i, ui.rows.size, received, ui.today, actions) }
        }
    }
}

@Composable
private fun MinimumTotal(ui: PeopleUi, actions: PeopleActions) {
    val c = LedgaTheme.colors
    val maxShillings = (ui.maxCents / 100).toFloat()
    val label = if (ui.minCents == 0L) "Any total" else "At least ${AmountFormat.CURRENCY} ${AmountFormat.plain(ui.minCents, Decimals.NEVER)}"
    Column(Modifier.fillMaxWidth().padding(top = Spacing.s)) {
        Text(label, style = LedgaType.caption, color = c.muted)
        Slider(
            value = (ui.minCents / 100).toFloat().coerceIn(0f, maxShillings),
            // Whole hundreds of shillings: a slider can't land on Ksh 4,137.
            onValueChange = { shillings -> actions.onMinimum((shillings / 100f).roundToLong() * 100L * 100L) },
            valueRange = 0f..maxShillings,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "Minimum total"
                stateDescription = label
            },
            colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary, inactiveTrackColor = c.barTrack),
        )
    }
}

@Composable
private fun PersonRow(row: PersonRowUi, index: Int, count: Int, received: Boolean, today: LocalDate?, actions: PeopleActions) {
    val segment = when {
        count == 1 -> Segment.Single
        index == 0 -> Segment.Top
        index == count - 1 -> Segment.Bottom
        else -> Segment.Middle
    }
    val day = DateLabels.nairobiDate(row.lastAt)
    val last = if (today != null && day.year != today.year) DateLabels.date(day) else DateLabels.dayMonth(day)
    val payments = "${row.count} ${if (row.count == 1) "payment" else "payments"}"
    val total = "${AmountFormat.CURRENCY} ${AmountFormat.plain(row.totalCents, Decimals.NEVER)}"
    ValueRow(
        leading = Leading.Avatar(row.name, inflow = received),
        title = row.name,
        subtitle = payments,
        value = total,
        valueColor = if (received) LedgaTheme.colors.inflow else LedgaTheme.colors.ink,
        // Under the total, not as the subtitle's tail: a tail never truncates, and "last 24 Dec 2025" beside a long
        // name and a seven-figure total doesn't fit at 1.3× (app/DESIGN.md: keep the tail short).
        detail = "last $last",
        speech = "${row.name}, $payments, $total ${if (received) "received" else "sent"}",
        modifier = Modifier.padding(top = if (index == 0) Spacing.m else 0.dp).cardSegment(segment, dividerAbove = index > 0),
        onClick = { actions.onOpen(row) },
        onClickLabel = "Show their payments",
    )
}
