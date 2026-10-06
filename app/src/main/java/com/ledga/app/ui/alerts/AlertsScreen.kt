package com.ledga.app.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledga.app.ui.app.DetailFrame
import com.ledga.app.ui.design.components.CategoryIcon
import com.ledga.app.ui.design.components.EmptyState
import com.ledga.app.ui.design.components.Segment
import com.ledga.app.ui.design.components.SkeletonRow
import com.ledga.app.ui.design.components.cardSegment
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Sizes
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.app.ui.tx.CategoryPickerHost
import com.ledga.app.ui.tx.OpenSheets
import com.ledga.app.ui.tx.TransactionSheetHost
import java.time.LocalDate
import kotlinx.coroutines.launch

/** What Alerts' taps do. Every default does nothing, for screenshots and tests. */
data class AlertsActions(val onBack: () -> Unit = {}, val onOpenTx: (String) -> Unit = {})

/** Alerts (R71): newest first, in one card; a payment's alert opens it. */
@Composable
fun AlertsContent(ui: AlertsUi, actions: AlertsActions, modifier: Modifier = Modifier) {
    DetailFrame("Alerts", onBack = actions.onBack, modifier = modifier) {
        when {
            !ui.loaded -> Column(Modifier.padding(horizontal = Spacing.screen)) { repeat(4) { SkeletonRow() } }
            ui.alerts.isEmpty() -> EmptyState("fluent_bell", "No alerts yet", "Big payments, Fuliza and your daily and weekly summaries show up here.")
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl)) {
                itemsIndexed(ui.alerts, key = { _, a -> a.key }) { i, a ->
                    val segment = when {
                        ui.alerts.size == 1 -> Segment.Single
                        i == 0 -> Segment.Top
                        i == ui.alerts.lastIndex -> Segment.Bottom
                        else -> Segment.Middle
                    }
                    AlertItem(a, ui.today, Modifier.cardSegment(segment, dividerAbove = i > 0), a.code?.let { code -> { actions.onOpenTx(code) } })
                }
            }
        }
    }
}

@Composable
private fun AlertItem(a: AlertUi, today: LocalDate?, modifier: Modifier, onClick: (() -> Unit)?) {
    val c = LedgaTheme.colors
    val tap = if (onClick != null) Modifier.clickable(onClickLabel = "Open payment", role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier
            .fillMaxWidth()
            .then(tap)
            .semantics(mergeDescendants = true) { if (today != null) contentDescription = AlertText.speech(a, today) }
            .heightIn(min = Sizes.touchTarget)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        CategoryIcon(AlertText.icon(a.type), contentDescription = null)
        Column(Modifier.weight(1f)) {
            Text(a.title, style = LedgaType.bodyStrong, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(a.body, style = LedgaType.caption, color = c.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (today != null) Text(AlertText.time(a.at, today), Modifier.padding(top = Spacing.xs), style = LedgaType.caption, color = c.muted)
        }
        // "New" is in the row's TalkBack phrase; the dot is decoration.
        if (a.isNew) Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(c.primary))
    }
}

/** Alerts (route): the payment sheet and picker over it, and Undo after Hide in its own snackbar host (R71). */
@Composable
fun AlertsScreen(onBack: () -> Unit, vm: AlertsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var sheets by rememberSaveable(stateSaver = OpenSheets.Saver) { mutableStateOf(OpenSheets()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        AlertsContent(ui, AlertsActions(onBack = onBack, onOpenTx = { sheets = sheets.copy(payment = it) }))
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(Spacing.l))
    }
    TransactionSheetHost(
        code = sheets.payment,
        onDismiss = { sheets = sheets.copy(payment = null) },
        onHidden = { code ->
            sheets = sheets.afterHide()
            scope.launch {
                if (snackbar.showSnackbar("Payment hidden", actionLabel = "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.undoHide(code)
            }
        },
        onChangeCategory = { sheets = sheets.copy(picker = it) },
    )
    CategoryPickerHost(sheets.picker, onDismiss = { sheets = sheets.copy(picker = null) })
}
