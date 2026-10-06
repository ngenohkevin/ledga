package com.ledga.app.ui.design.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.icons.Fluent
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType

/** Every list and card has a designed empty state (spec §10.5): 3D icon, title, explanation, optional action. */
@Composable
fun EmptyState(
    iconKey: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = LedgaTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(c.plate), contentAlignment = Alignment.Center) {
            Image(painterResource(Fluent.resOf(iconKey)), contentDescription = null, modifier = Modifier.size(42.dp))
        }
        Text(title, Modifier.padding(top = Spacing.xs), style = LedgaType.section, color = c.ink, textAlign = TextAlign.Center)
        Text(body, style = LedgaType.body, color = c.muted, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) PrimaryPill(actionLabel, onAction, Modifier.padding(top = Spacing.s))
    }
}

/** The error state: the warning icon and a "Try again" action. */
@Composable
fun ErrorState(title: String, body: String, onRetry: () -> Unit, modifier: Modifier = Modifier) =
    EmptyState("fluent_warning", title, body, modifier, actionLabel = "Try again", onAction = onRetry)

/** A loading block on barTrack. It pulses gently, and holds still when motion is reduced. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val color = LedgaTheme.colors.barTrack
    val pulse = if (LedgaTheme.reducedMotion) {
        null
    } else {
        rememberInfiniteTransition(label = "skeleton")
            .animateFloat(1f, 0.55f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    }
    Box(modifier.graphicsLayer { alpha = pulse?.value ?: 1f }.clip(shape).background(color))
}

/** A loading placeholder shaped like a [TxRow]. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Skeleton(Modifier.size(40.dp), RoundedCornerShape(13.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Skeleton(Modifier.fillMaxWidth(0.6f).height(12.dp))
            Skeleton(Modifier.fillMaxWidth(0.35f).height(10.dp))
        }
        Skeleton(Modifier.size(width = 64.dp, height = 12.dp))
    }
}
