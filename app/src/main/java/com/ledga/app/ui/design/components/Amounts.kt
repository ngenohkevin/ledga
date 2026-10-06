package com.ledga.app.ui.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ledga.app.ui.design.format.AmountFormat
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Motion
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.core.money.Decimals

/**
 * "Ksh" small and the amount large on one baseline (the balance card, spec §10.4). It shrinks to fit one line,
 * down to [minFontSize], so a seven-figure balance at a large font scale never wraps or clips. Shows the magnitude.
 */
@Composable
fun AmountText(
    cents: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = LedgaType.balance,
    color: Color = LedgaTheme.colors.ink,
    unitColor: Color = LedgaTheme.colors.ink2,
    decimals: Decimals = Decimals.AUTO,
    minFontSize: TextUnit = 18.sp,
) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(fontSize = 0.5.em, fontWeight = FontWeight.Bold, color = unitColor, letterSpacing = 0.em)) {
            append(AmountFormat.CURRENCY)
        }
        append(' ')
        append(AmountFormat.plain(cents, decimals))
    }
    val min = if (style.fontSize.value < minFontSize.value) style.fontSize else minFontSize
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis, // past minFontSize: "…", never a silently shorter number
        autoSize = TextAutoSize.StepBased(minFontSize = min, maxFontSize = style.fontSize, stepSize = 1.sp),
    )
}

/**
 * [AmountText] that counts from the old amount to the new one over 600 ms (spec §10.1), and lands exactly on it.
 * With "Remove animations" on (`LedgaTheme.reducedMotion`) it jumps straight to the new amount.
 */
@Composable
fun AnimatedAmount(
    cents: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = LedgaType.balance,
    color: Color = LedgaTheme.colors.ink,
    decimals: Decimals = Decimals.AUTO,
) {
    val reduced = LedgaTheme.reducedMotion
    var from by remember { mutableLongStateOf(cents) }
    var to by remember { mutableLongStateOf(cents) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(cents, reduced) {
        if (cents == to) return@LaunchedEffect
        from = lerpCents(from, to, progress.value)
        to = cents
        if (reduced) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(Motion.AMOUNT_MS, easing = Motion.EmphasizedDecelerate))
        }
    }
    AmountText(lerpCents(from, to, progress.value), modifier, style, color, decimals = decimals)
}

/** The displayed amount at [t]; exactly [to] once t reaches 1, so float rounding never shows a wrong final value. */
internal fun lerpCents(from: Long, to: Long, t: Float): Long =
    if (t >= 1f) to else from + Math.round((to - from).toDouble() * t)
