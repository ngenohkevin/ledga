package com.ledga.app.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.ledga.app.ui.app.ScreenTitle
import com.ledga.app.ui.design.components.SegmentedControl
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Sizes
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.app.ui.tx.CategoryPickerHost
import com.ledga.app.ui.tx.TransactionSheetHost
import kotlinx.coroutines.launch

/** Activity's frame (spec §10.4): the title with the filter button, the three segments, then the segment's pane. */
@Composable
fun ActivityContent(
    segment: ActivitySegment,
    onSegment: (ActivitySegment) -> Unit,
    filterCount: Int,
    onFilters: () -> Unit,
    modifier: Modifier = Modifier,
    pane: @Composable () -> Unit,
) {
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("Activity", Modifier.weight(1f))
            if (segment == ActivitySegment.TRANSACTIONS) FilterButton(filterCount, onFilters, Modifier.padding(end = Spacing.s))
        }
        SegmentedControl(
            ActivitySegment.entries.map { it.label },
            segment.ordinal,
            { onSegment(ActivitySegment.entries[it]) },
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
        )
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = Spacing.s)) { pane() }
    }
}

/** The sliders button (mockup `activity`) with a count of the sheet's filters that are on. */
@Composable
private fun FilterButton(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    Box(
        modifier
            .size(Sizes.touchTarget)
            .clip(CircleShape)
            .background(c.surface)
            .border(Sizes.hairline, c.line, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (count == 0) "Filters" else "Filters, $count on" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Ph.SlidersHorizontal, contentDescription = null, tint = c.ink2, modifier = Modifier.size(Sizes.icon))
        if (count > 0) {
            Text(
                "$count",
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .heightIn(min = 16.dp)
                    .widthIn(min = 16.dp)
                    .clip(CircleShape)
                    .background(c.primary)
                    .padding(horizontal = 3.dp)
                    .clearAndSetSemantics {},
                style = LedgaType.overline,
                color = c.onPrimary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The Activity tab (route). It hosts the transaction sheet, the category picker and the filter sheet. After Hide it
 * offers Undo in a snackbar (spec §10.4).
 */
@Composable
fun ActivityTab(vm: ActivityViewModel = hiltViewModel()) {
    val segment by vm.segment.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val items = vm.items.collectAsLazyPagingItems()
    var openCode by rememberSaveable { mutableStateOf<String?>(null) }
    var pickCode by rememberSaveable { mutableStateOf<String?>(null) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var personKey by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        ActivityContent(segment, vm::select, ui.filter.sheetCount, onFilters = { filtersOpen = true }) {
            when (segment) {
                ActivitySegment.TRANSACTIONS -> TransactionsPane(
                    ui,
                    items,
                    TransactionsActions(
                        onQuery = vm::setQuery,
                        onFlow = vm::setFlow,
                        onLine = vm::toggleLine,
                        onOpen = { openCode = it },
                        onPickCategory = { pickCode = it },
                        onClearFilters = vm::clearFilters,
                    ),
                )
                ActivitySegment.SPENDING -> {
                    val spending: SpendingViewModel = hiltViewModel()
                    val s by spending.ui.collectAsStateWithLifecycle()
                    SpendingPane(
                        s,
                        SpendingActions(
                            onPrevious = spending::previous,
                            onNext = spending::next,
                            onSelect = spending::select,
                            onByGroup = spending::setByGroup,
                            onShare = { vm.showTransactions(spending.transactionsFor(it)) },
                        ),
                    )
                }
                ActivitySegment.PEOPLE -> {
                    val people: PeopleViewModel = hiltViewModel()
                    val p by people.ui.collectAsStateWithLifecycle()
                    PeoplePane(
                        p,
                        PeopleActions(
                            onDirection = people::setDirection,
                            onQuery = people::setQuery,
                            onMinimum = people::setMinimum,
                            onOpen = { personKey = it.key },
                        ),
                    )
                    PersonSheetHost(p.rows.firstOrNull { it.key == personKey }, onDismiss = { personKey = null }, onOpenTx = { openCode = it })
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(Spacing.l))
    }
    TransactionSheetHost(
        code = openCode,
        onDismiss = { openCode = null },
        onHidden = { code ->
            scope.launch {
                val result = snackbar.showSnackbar("Payment hidden", actionLabel = "Undo", duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) vm.undoHide(code)
            }
        },
        onChangeCategory = { pickCode = it },
    )
    CategoryPickerHost(pickCode, onDismiss = { pickCode = null })
    if (filtersOpen) {
        FilterSheet(
            current = ui.filter,
            categories = ui.categories.values.sortedBy { it.sortOrder },
            now = vm.now(),
            onApply = {
                vm.applySheet(it)
                filtersOpen = false
            },
            onDismiss = { filtersOpen = false },
        )
    }
}
