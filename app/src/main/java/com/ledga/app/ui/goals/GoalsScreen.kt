package com.ledga.app.ui.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledga.app.data.repository.GoalStatus
import com.ledga.app.data.repository.GoalWithProgress
import com.ledga.app.ui.components.parseColor
import com.ledga.app.ui.components.v2.BackLeading
import com.ledga.app.ui.components.v2.BentoCard
import com.ledga.app.ui.components.v2.LedgaTopBar
import com.ledga.app.ui.components.v2.onTonal
import com.ledga.app.ui.components.v2.ProgressRing
import com.ledga.app.ui.components.v2.TopBarIconButton
import com.ledga.app.ui.theme.LedgaAccent
import com.ledga.app.ui.theme.LedgaAccentDeep
import com.ledga.app.ui.theme.LedgaAccentSoft
import com.ledga.app.ui.theme.LedgaDanger
import com.ledga.app.ui.theme.LedgaDangerSoft
import com.ledga.app.ui.theme.LedgaText
import com.ledga.app.ui.theme.LedgaWarning
import com.ledga.app.ui.theme.LedgaWarningSoft
import com.ledga.app.ui.theme.Space
import com.ledga.app.util.CurrencyFormatter
import com.ledga.app.util.DateUtils

@Composable
fun GoalsScreen(
    onBack: () -> Unit,
    viewModel: GoalsViewModel = hiltViewModel(),
    onOpenGoal: (Long) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    var addOpen by remember { mutableStateOf(false) }

    if (addOpen) {
        AddGoalSheet(
            onDismiss = { addOpen = false },
            onCreate = { name, target, date, rule, color ->
                viewModel.create(name, target, date, rule, color)
                addOpen = false
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LedgaTopBar(
            title = "Goals",
            leading = { BackLeading(onBack) },
            trailing = {
                TopBarIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "Add goal",
                    onClick = { addOpen = true },
                )
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.Screen)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(Space.Section),
        ) {
            when {
                state.isLoading -> Unit
                state.goals.isEmpty() -> EmptyState()
                else -> state.goals.forEach { gp ->
                    GoalCard(gp = gp, onClick = { onOpenGoal(gp.goal.id) })
                }
            }

            Box(modifier = Modifier.padding(bottom = Space.s5))
        }
    }
}

@Composable
private fun EmptyState() {
    BentoCard(
        overline = "Set a goal",
        title = "Track rent, school fees, a trip",
        icon = Icons.Filled.Flag,
        iconTint = LedgaAccentDeep,
        tonal = true,
        tonalColor = LedgaAccentSoft,
    ) {
        Text(
            text = "Pick what you're saving toward and how you'll contribute. " +
                    "Ledga keeps the progress moving on its own — no manual entry.",
            style = LedgaText.BodyM,
            color = onTonal(LedgaAccentSoft),
        )
    }
}

@Composable
private fun GoalCard(gp: GoalWithProgress, onClick: () -> Unit) {
    val ringColor = parseColor(gp.goal.colorHex)
    BentoCard(onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s5),
        ) {
            ProgressRing(
                progress = gp.percent,
                size = 72.dp,
                strokeWidth = 6.dp,
                progressColor = ringColor,
                label = "${(gp.percent * 100).toInt()}%",
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = gp.goal.name,
                    style = LedgaText.TitleM,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${CurrencyFormatter.formatKshCompact(gp.currentAmount)} of " +
                            CurrencyFormatter.formatKshCompact(gp.goal.targetAmount),
                    style = LedgaText.BodyM,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusBadge(status = gp.status, eta = gp.eta, targetDate = gp.goal.targetDate)
            }
        }
    }
}

@Composable
private fun StatusBadge(status: GoalStatus, eta: Long?, targetDate: Long?) {
    val (bg, fg, icon, text) = when (status) {
        GoalStatus.Completed -> StatusVisual(
            bg = LedgaAccentSoft, fg = LedgaAccentDeep,
            icon = Icons.Filled.Check, text = "Completed",
        )
        GoalStatus.OnTrack -> StatusVisual(
            bg = LedgaAccentSoft, fg = LedgaAccentDeep,
            icon = null, text = buildEtaText("On track", eta, targetDate),
        )
        GoalStatus.Ahead -> StatusVisual(
            bg = LedgaAccentSoft, fg = LedgaAccentDeep,
            icon = null, text = buildEtaText("Ahead", eta, targetDate),
        )
        GoalStatus.Behind -> StatusVisual(
            bg = LedgaWarningSoft, fg = LedgaWarning,
            icon = null, text = buildEtaText("Behind", eta, targetDate),
        )
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(12.dp),
                )
            }
            Text(text = text, style = LedgaText.Caption, color = fg)
        }
    }
}

private data class StatusVisual(
    val bg: androidx.compose.ui.graphics.Color,
    val fg: androidx.compose.ui.graphics.Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector?,
    val text: String,
)

private fun buildEtaText(prefix: String, eta: Long?, targetDate: Long?): String {
    val ref = eta ?: targetDate ?: return prefix
    return "$prefix · ETA ${DateUtils.formatDate(ref)}"
}
