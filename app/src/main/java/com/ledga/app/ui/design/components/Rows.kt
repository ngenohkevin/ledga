package com.ledga.app.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.format.AmountFormat
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Sizes
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType

/** What a row shows on its left: a category's 3D icon, or a person's initials. */
sealed interface Leading {
    data class Icon(val key: String) : Leading
    data class Avatar(val name: String, val inflow: Boolean) : Leading
}

/**
 * The one list row behind transactions, trackers, people and "Where it went". The title ellipsizes; the value never
 * truncates (Review Focus #1). With [speech], TalkBack reads that one phrase for the whole row.
 */
@Composable
fun ValueRow(
    leading: Leading,
    title: String,
    subtitle: String?,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = LedgaTheme.colors.ink,
    detail: String? = null,
    detailColor: Color = LedgaTheme.colors.muted,
    speech: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    subtitleTail: String? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
) {
    val c = LedgaTheme.colors
    val interaction = if (onClick != null || onLongClick != null) {
        // TalkBack names both actions ("Open payment", "Change category"): Phase 3 deferred M10.
        Modifier.combinedClickable(
            onClickLabel = onClickLabel,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onClick = onClick ?: {},
        )
    } else {
        Modifier
    }
    Row(
        modifier
            .fillMaxWidth()
            .then(interaction)
            .semantics(mergeDescendants = true) { if (speech != null) contentDescription = speech }
            .heightIn(min = Sizes.touchTarget)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        when (leading) {
            is Leading.Icon -> CategoryIcon(leading.key, contentDescription = null)
            is Leading.Avatar -> InitialAvatar(leading.name, leading.inflow)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = LedgaType.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null || subtitleTail != null) {
                // The tail (a time, an amount) never truncates; the subtitle before it ellipsizes instead.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            Modifier.weight(1f, fill = false),
                            style = LedgaType.caption,
                            color = c.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (subtitleTail != null) {
                        Text(
                            if (subtitle != null) " · $subtitleTail" else subtitleTail,
                            style = LedgaType.caption,
                            color = c.muted,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = LedgaType.amount, color = valueColor, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Text(detail, style = LedgaType.amountCaption, color = detailColor, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * A transaction row (spec §10.4): outflows in ink with a minus (U+2212), inflows in inflow green with a "+".
 * Pass the category (or a Fuliza note, "Fuliza Ksh 300") as [subtitle] and the time as [subtitleTail], so the
 * time stays whole at large font scales. Keep the tail short: it never truncates.
 */
@Composable
fun TxRow(
    leading: Leading,
    title: String,
    subtitle: String,
    amountCents: Long,
    inflow: Boolean,
    speech: String,
    modifier: Modifier = Modifier,
    balanceText: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    subtitleTail: String? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
) = ValueRow(
    leading = leading,
    title = title,
    subtitle = subtitle,
    value = AmountFormat.signed(amountCents, inflow),
    modifier = modifier,
    valueColor = if (inflow) LedgaTheme.colors.inflow else LedgaTheme.colors.ink,
    detail = balanceText,
    speech = speech,
    onClick = onClick,
    onLongClick = onLongClick,
    subtitleTail = subtitleTail,
    onClickLabel = onClickLabel,
    onLongClickLabel = onLongClickLabel,
)

/** First item of a day card: "TODAY ……… Out 4,500 · In 5,000" (spec §10.4). A TalkBack heading. */
@Composable
fun DayHeader(label: String, outCents: Long, inCents: Long, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    val totals = buildList {
        if (outCents != 0L) add("Out ${AmountFormat.plain(outCents)}")
        if (inCents != 0L) add("In ${AmountFormat.plain(inCents)}")
    }.joinToString(" · ")
    Row(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { heading() }
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = LedgaType.overline, color = c.muted)
        if (totals.isNotEmpty()) Text(totals, style = LedgaType.amountCaption, color = c.muted)
    }
}

/** A 1 dp lineSubtle rule between rows inside a card. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(Sizes.hairline).background(LedgaTheme.colors.lineSubtle))
}

/** What sits at the right of a [ListRow]. */
sealed interface RowTrailing {
    data object Chevron : RowTrailing
    data object None : RowTrailing
    data class Value(val text: String) : RowTrailing
    data class Toggle(val checked: Boolean, val onCheckedChange: (Boolean) -> Unit) : RowTrailing
}

/**
 * A You/settings row: optional 3D icon, title (+ optional badge), subtitle and a trailing chevron, value or switch.
 * A [RowTrailing.Toggle] row toggles as a whole and is a single switch for TalkBack.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconKey: String? = null,
    badge: String? = null,
    trailing: RowTrailing = RowTrailing.Chevron,
    onClick: (() -> Unit)? = null,
) {
    val c = LedgaTheme.colors
    val interaction = when {
        trailing is RowTrailing.Toggle -> Modifier.toggleable(value = trailing.checked, role = Role.Switch, onValueChange = trailing.onCheckedChange)
        onClick != null -> Modifier.clickable(role = Role.Button, onClick = onClick)
        else -> Modifier
    }
    Row(
        modifier
            .fillMaxWidth()
            .then(interaction)
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (iconKey != null) CategoryIcon(iconKey, contentDescription = null, size = WellSize.Small)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, Modifier.weight(1f, fill = false), style = LedgaType.bodyStrong, color = c.ink)
                if (badge != null) {
                    Text(
                        badge,
                        Modifier.clip(RoundedCornerShape(percent = 50)).background(c.primarySoft).padding(horizontal = 6.dp, vertical = 2.dp),
                        style = LedgaType.overline,
                        color = c.onPrimarySoft,
                    )
                }
            }
            if (subtitle != null) Text(subtitle, style = LedgaType.caption, color = c.muted)
        }
        when (trailing) {
            RowTrailing.Chevron -> Icon(Ph.CaretRightBold, contentDescription = null, tint = c.muted, modifier = Modifier.size(Sizes.iconSmall))
            RowTrailing.None -> Unit
            is RowTrailing.Value -> Text(trailing.text, style = LedgaType.body, color = c.muted)
            is RowTrailing.Toggle -> LedgaSwitch(checked = trailing.checked, onCheckedChange = null)
        }
    }
}
