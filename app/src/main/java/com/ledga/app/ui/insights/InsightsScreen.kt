package com.ledga.app.ui.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledga.app.data.db.entity.Insight
import com.ledga.app.data.db.entity.InsightSeverity
import com.ledga.app.data.db.entity.InsightType
import com.ledga.app.ui.components.v2.BentoCard
import com.ledga.app.ui.components.v2.LedgaTopBar
import com.ledga.app.ui.theme.LedgaAccent
import com.ledga.app.ui.theme.LedgaAccentDeep
import com.ledga.app.ui.theme.LedgaAccentSoft
import com.ledga.app.ui.theme.LedgaDanger
import com.ledga.app.ui.theme.LedgaDangerSoft
import com.ledga.app.ui.theme.LedgaText
import com.ledga.app.ui.theme.LedgaWarning
import com.ledga.app.ui.theme.LedgaWarningSoft
import com.ledga.app.ui.theme.Space

@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel = hiltViewModel(),
    onSeeTransactionsForCategory: (Long) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        LedgaTopBar(title = "Insights")
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Space.Screen),
            verticalArrangement = Arrangement.spacedBy(Space.s5),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = 120.dp,
            ),
        ) {
            item { Spacer(modifier = Modifier.height(Space.s2)) }

            if (state.isLoading) {
                item { LoadingPlaceholder() }
            } else if (state.insights.isEmpty()) {
                item { EmptyState() }
            } else {
                item {
                    Text(
                        text = "${state.insights.size} active ${if (state.insights.size == 1) "insight" else "insights"}",
                        style = LedgaText.BodyM,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.insights, key = { it.id }) { insight ->
                    InsightCard(
                        insight = insight,
                        onDismiss = { viewModel.dismiss(insight.id) },
                        onSnooze = { viewModel.snooze(insight.id) },
                        onCta = {
                            val args = insight.ctaArgs ?: return@InsightCard
                            // very simple kv parser
                            val kv = args.split("=").takeIf { it.size == 2 } ?: return@InsightCard
                            if (kv[0] == "category") {
                                kv[1].toLongOrNull()?.let(onSeeTransactionsForCategory)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightCard(
    insight: Insight,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    onCta: () -> Unit,
) {
    val tone = toneFor(insight.severity)
    BentoCard(
        tonal = tone.tonal,
        tonalColor = tone.background,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.Icon(
                imageVector = iconFor(insight.type),
                contentDescription = null,
                tint = tone.icon,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = insight.typeLabel,
                style = LedgaText.Overline,
                color = tone.icon,
            )
        }
        Text(
            text = insight.headline,
            style = LedgaText.TitleS,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!insight.body.isNullOrBlank()) {
            Text(
                text = insight.body,
                style = LedgaText.BodyM,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.s3),
            horizontalArrangement = Arrangement.spacedBy(Space.s5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (insight.ctaLabel != null) {
                Text(
                    text = insight.ctaLabel,
                    style = LedgaText.BodyM,
                    color = tone.icon,
                    modifier = Modifier.clickable { onCta() },
                )
            }
            Box(modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.clickable { onSnooze() },
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.Replay,
                    contentDescription = "Snooze",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Snooze 30d",
                    style = LedgaText.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Got it",
                style = LedgaText.BodyM,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable { onDismiss() },
            )
        }
    }
}

@Composable
private fun EmptyState() {
    BentoCard(
        overline = "All clear",
        title = "Nothing unusual right now",
        icon = Icons.Filled.AutoAwesome,
        iconTint = LedgaAccentDeep,
        tonal = true,
        tonalColor = LedgaAccentSoft,
    ) {
        Text(
            text = "Ledga checks your activity every day and surfaces anything " +
                    "worth your attention here. Quiet means things look healthy.",
            style = LedgaText.BodyM,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LoadingPlaceholder() {
    BentoCard {
        Text(
            text = "Looking through your activity…",
            style = LedgaText.BodyM,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class Tone(val background: Color, val icon: Color, val tonal: Boolean)

private fun toneFor(severity: InsightSeverity): Tone = when (severity) {
    InsightSeverity.ALERT -> Tone(background = LedgaDangerSoft, icon = LedgaDanger, tonal = true)
    InsightSeverity.WARN -> Tone(background = LedgaWarningSoft, icon = LedgaWarning, tonal = true)
    InsightSeverity.NUDGE -> Tone(background = LedgaAccentSoft, icon = LedgaAccentDeep, tonal = true)
    InsightSeverity.INFO -> Tone(background = Color.Transparent, icon = LedgaAccent, tonal = false)
}

private fun iconFor(type: InsightType): ImageVector = when (type) {
    InsightType.ANOMALY -> Icons.Filled.WarningAmber
    InsightType.RECURRING -> Icons.Filled.NotificationImportant
    InsightType.FEE_TIP -> Icons.Filled.Lightbulb
    InsightType.FULIZA -> Icons.Filled.ReportProblem
    // Full clear gets the check, partial gets a swap icon. The rule sets
    // severity = NUDGE for full and INFO for partial, so we drive icon
    // choice off the same axis via the helper below.
    InsightType.FULIZA_AUTO_PAY -> Icons.Filled.SwapHoriz
    InsightType.POSITIVE_NUDGE -> Icons.Filled.AutoAwesome
}

@Suppress("unused") // available for future per-insight icon override
private fun fulizaAutoPayIconFor(severity: com.ledga.app.data.db.entity.InsightSeverity): ImageVector =
    if (severity == com.ledga.app.data.db.entity.InsightSeverity.NUDGE) Icons.Filled.CheckCircle
    else Icons.Filled.SwapHoriz
