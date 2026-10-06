package com.ledga.app.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Radii
import com.ledga.app.ui.design.tokens.Sizes
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType

/** A white card: 24 dp corners, 1 dp line border, a 1 dp/4 % shadow in light only (spec §10.1). */
@Composable
fun LedgaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.l),
    content: @Composable ColumnScope.() -> Unit,
) = Surfaced(RoundedCornerShape(Radii.card), modifier, onClick, contentPadding, content)

/** A tile (tracker strip, small cards): like [LedgaCard] with 20 dp corners. */
@Composable
fun LedgaTile(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.m),
    content: @Composable ColumnScope.() -> Unit,
) = Surfaced(RoundedCornerShape(Radii.tile), modifier, onClick, contentPadding, content)

@Composable
private fun Surfaced(
    shape: Shape,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    contentPadding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LedgaTheme.colors
    val elevation = if (c.isDark) Modifier else Modifier.shadow(1.dp, shape, clip = false, ambientColor = c.shadow, spotColor = c.shadow)
    Column(
        modifier
            .then(elevation)
            .clip(shape)
            .background(c.surface)
            .border(Sizes.hairline, c.line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** Tracker detail's stat tiles ("This month", "Avg / month"): label over an amount, read as one item by TalkBack. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    val shape = RoundedCornerShape(Radii.stat)
    Column(
        modifier
            .clip(shape)
            .background(c.surface)
            .border(Sizes.hairline, c.line, shape)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.m, vertical = 10.dp),
    ) {
        Text(label, style = LedgaType.caption, color = c.muted)
        Text(value, style = LedgaType.amountM, color = c.ink)
    }
}

/** Where a lazy-list item sits inside one visual card. A day of transactions spans many paged items. */
enum class Segment { Single, Top, Middle, Bottom }

/**
 * Draws the card surface and border for one item of a card that spans several lazy-list items, so a paged list
 * still looks like PayPal's day cards (spec §10.4). [dividerAbove] adds the lineSubtle rule between rows.
 * The rounded rect is extended past every edge this segment shares and then clipped, so only this item's part shows.
 * Whatever the item draws after this modifier (a clickable's ripple, a pressed colour) is clipped to the card's
 * outer corners, so it never bleeds past the rounding (Phase 3 deferred M16).
 */
@Composable
fun Modifier.cardSegment(segment: Segment, dividerAbove: Boolean = false): Modifier {
    val c = LedgaTheme.colors
    val roundTop = segment == Segment.Top || segment == Segment.Single
    val roundBottom = segment == Segment.Bottom || segment == Segment.Single
    val top = if (roundTop) Radii.card else 0.dp
    val bottom = if (roundBottom) Radii.card else 0.dp
    return drawBehind {
        val r = Radii.card.toPx()
        val stroke = Sizes.hairline.toPx()
        val drawTop = if (roundTop) 0f else -2 * r
        val drawBottom = if (roundBottom) size.height else size.height + 2 * r
        clipRect {
            drawRoundRect(c.surface, topLeft = Offset(0f, drawTop), size = Size(size.width, drawBottom - drawTop), cornerRadius = CornerRadius(r))
            drawRoundRect(
                c.line,
                topLeft = Offset(stroke / 2, drawTop + stroke / 2),
                size = Size(size.width - stroke, drawBottom - drawTop - stroke),
                cornerRadius = CornerRadius(r),
                style = Stroke(stroke),
            )
            if (dividerAbove) drawLine(c.lineSubtle, Offset(stroke, stroke / 2), Offset(size.width - stroke, stroke / 2), stroke)
        }
    }.clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
}
