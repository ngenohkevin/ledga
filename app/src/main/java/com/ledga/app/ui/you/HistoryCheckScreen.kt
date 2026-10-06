package com.ledga.app.ui.you

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledga.app.ui.app.DetailFrame
import com.ledga.app.ui.app.grouped
import com.ledga.app.ui.design.components.CategoryIcon
import com.ledga.app.ui.design.components.EmptyState
import com.ledga.app.ui.design.components.LedgaCard
import com.ledga.app.ui.design.components.ListRow
import com.ledga.app.ui.design.components.PrimaryPill
import com.ledga.app.ui.design.components.RowDivider
import com.ledga.app.ui.design.components.RowTrailing
import com.ledga.app.ui.design.components.SectionHeader
import com.ledga.app.ui.design.components.Segment
import com.ledga.app.ui.design.components.SkeletonRow
import com.ledga.app.ui.design.components.WellSize
import com.ledga.app.ui.design.components.cardSegment
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.app.ui.home.HomeText
import com.ledga.app.ui.tx.CategoryPickerHost
import com.ledga.app.ui.tx.OpenSheets
import com.ledga.app.ui.tx.TransactionSheetHost
import com.ledga.app.ui.tx.TxText
import kotlinx.coroutines.launch
import com.ledga.app.ui.design.components.TxRow as TransactionRow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics

data class HistoryCheckActions(val onBack: () -> Unit = {}, val onRescan: () -> Unit = {}, val onOpenTx: (String) -> Unit = {})

/** History check (R79): the verdict, each line's result, then every break, newest first. */
@Composable
fun HistoryCheckContent(ui: HistoryCheckUi, actions: HistoryCheckActions, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    DetailFrame("History check", onBack = actions.onBack, modifier = modifier) {
        when {
            !ui.loaded -> Column(Modifier.padding(horizontal = Spacing.screen)) { repeat(4) { SkeletonRow() } }
            ui.checked == 0 && ui.breaks.isEmpty() -> EmptyState(
                "fluent_magnifying_glass_tilted_left",
                "Nothing to check yet",
                "History check compares the balance in each M-Pesa message with the one before it.",
            )
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl)) {
                item {
                    LedgaCard(Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                            CategoryIcon(if (ui.breaks.isEmpty()) "fluent_check_mark_button" else "fluent_warning", contentDescription = null, size = WellSize.Large)
                            Column(Modifier.weight(1f)) {
                                val n = ui.breaks.size
                                Text(
                                    if (n == 0) "Your history adds up" else "$n ${if (n == 1) "balance doesn't" else "balances don't"} add up",
                                    style = LedgaType.section,
                                    color = c.ink,
                                )
                                Text(
                                    if (n == 0) {
                                        "Ledga checked ${grouped(ui.checked)} balances against what M-Pesa said after each payment."
                                    } else {
                                        "A message may be missing, or Ledga may have misread one. A rescan finds messages Ledga missed."
                                    },
                                    style = LedgaType.body,
                                    color = c.muted,
                                )
                            }
                        }
                        if (ui.breaks.isNotEmpty()) PrimaryPill("Rescan SMS inbox", actions.onRescan, Modifier.fillMaxWidth().padding(top = Spacing.m))
                    }
                }
                if (ui.lines.isNotEmpty()) {
                    item {
                        LedgaCard(Modifier.fillMaxWidth().padding(top = Spacing.l), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                            ui.lines.forEachIndexed { i, l ->
                                if (i > 0) RowDivider(Modifier.padding(horizontal = Spacing.m))
                                ListRow(
                                    l.label,
                                    subtitle = "${grouped(l.checked)} checked · " + when (l.breaks) {
                                        0 -> "all add up"
                                        1 -> "1 doesn't add up"
                                        else -> "${l.breaks} don't add up"
                                    },
                                    trailing = RowTrailing.None,
                                )
                            }
                        }
                    }
                }
                if (ui.breaks.isNotEmpty()) item { SectionHeader("Where it doesn't add up", Modifier.padding(top = Spacing.l, bottom = Spacing.s)) }
                val today = ui.today
                itemsIndexed(ui.breaks, key = { _, b -> b.tx.code }) { i, b ->
                    val segment = when {
                        ui.breaks.size == 1 -> Segment.Single
                        i == 0 -> Segment.Top
                        i == ui.breaks.lastIndex -> Segment.Bottom
                        else -> Segment.Middle
                    }
                    val tx = b.tx
                    // The break is the point of the row, so it sits under it in full, where it wraps instead of being cut
                    // (a row's subtitle ellipsizes: at 1.3× only "Expected K…" was left).
                    Column(Modifier.cardSegment(segment, dividerAbove = i > 0)) {
                        TransactionRow(
                            leading = TxText.leading(tx, ui.categories[tx.categoryKey]?.icon3d ?: "fluent_package"),
                            title = TxText.title(tx),
                            subtitle = today?.let { HomeText.rowTime(tx.occurredAt, it) }.orEmpty(),
                            amountCents = tx.amountCents,
                            inflow = TxText.isInflow(tx.flow),
                            speech = (today?.let { TxText.speech(tx, it) } ?: TxText.title(tx)) + ". " + HistoryText.breakLine(b),
                            onClick = { actions.onOpenTx(tx.code) },
                            onClickLabel = "Open payment",
                        )
                        Text(
                            HistoryText.breakLine(b),
                            // Under the title: the row's 14 dp edge, the icon well and the gap after it.
                            Modifier.padding(start = 14.dp + WellSize.Medium.box + Spacing.m, end = 14.dp, bottom = 10.dp)
                                // TalkBack already hears it in the row's phrase.
                                .semantics { hideFromAccessibility() },
                            style = LedgaType.caption,
                            color = c.ink2,
                        )
                    }
                }
            }
        }
    }
}

/** History check (route): Rescan, and the payment sheet with Undo after Hide. */
@Composable
fun HistoryCheckScreen(onBack: () -> Unit, vm: HistoryCheckViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var sheets by rememberSaveable(stateSaver = OpenSheets.Saver) { mutableStateOf(OpenSheets()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        HistoryCheckContent(
            ui,
            HistoryCheckActions(
                onBack = onBack,
                onRescan = {
                    vm.rescan()
                    scope.launch { snackbar.showSnackbar("Rescanning your inbox. Check again once it's done.", duration = SnackbarDuration.Short) }
                },
                onOpenTx = { sheets = sheets.copy(payment = it) },
            ),
        )
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
