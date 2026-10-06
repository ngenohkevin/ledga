package com.ledga.app.ui.tx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.ui.design.components.CategoryIcon
import com.ledga.app.ui.design.components.LedgaModalSheet
import com.ledga.app.ui.design.components.LedgaSwitch
import com.ledga.app.ui.design.components.LinkButton
import com.ledga.app.ui.design.components.PrimaryPill
import com.ledga.app.ui.design.components.SearchField
import com.ledga.app.ui.design.components.SkeletonRow
import com.ledga.app.ui.design.components.WellSize
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Radii
import com.ledga.app.ui.design.tokens.Sizes
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.core.model.CategoryGroup
import java.util.Locale

private const val COLUMNS = 4

/**
 * The narrowest a cell may be, in widths of the caption's font size: the longest seeded word ("International", about
 * 6 ems) and its padding. It follows the caption's own scaling (Android scales large sp sizes less than small ones), so
 * a phone shows four columns at 1.0× (mockup `picker`) and three at 1.3×: names wrap between words, never inside one.
 */
private const val CELL_MIN_EMS = 6.4f
private val STAR = Char(0x2605).toString()

/** Everything the picker can do; the host wires it to the ViewModel. */
data class PickerActions(
    val onSelect: (String) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onToggleSearch: () -> Unit = {},
    val onApplyAll: (Boolean) -> Unit = {},
    val onAccountOnly: (Boolean) -> Unit = {},
    val onNewCategory: (CategoryGroup) -> Unit = {},
    val onCreateCategory: (String) -> Unit = {},
    val onCancelNew: () -> Unit = {},
    val onSave: () -> Unit = {},
)

/**
 * The category picker's body (spec §10.4, mockup `picker`):
 * - a grouped grid of 3D icons, with ★ on tracked categories;
 * - search;
 * - "+ New category" at the end of each group;
 * - "Apply to all N from <name>", with "only this account number" for paybills (R35, R36);
 * - Save.
 */
@Composable
fun CategoryPickerContent(state: PickerState, actions: PickerActions, modifier: Modifier = Modifier, searchOpen: Boolean = false) {
    val c = LedgaTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Category", Modifier.weight(1f).semantics { heading() }, style = LedgaType.section, color = c.ink)
            IconButton(onClick = actions.onToggleSearch) {
                Icon(Ph.MagnifyingGlass, contentDescription = "Search categories", tint = c.ink2, modifier = Modifier.size(Sizes.icon))
            }
        }
        if (searchOpen) SearchField(state.query, actions.onQuery, "Search categories", Modifier.padding(bottom = Spacing.s))
        if (!state.loaded) {
            repeat(3) { SkeletonRow() }
            return@Column
        }
        state.visibleGroups.forEach { g ->
            Text(
                g.group.displayName.uppercase(Locale.ENGLISH),
                Modifier.padding(top = Spacing.m, bottom = Spacing.s).semantics { heading() },
                style = LedgaType.overline,
                color = c.muted,
            )
            CategoryGrid(g, state, actions)
            if (state.newCategoryIn == g.group) NewCategoryEditor(g.group, actions)
        }
        if (state.showApplyAll) ApplyAllCard(state, actions, Modifier.padding(top = Spacing.l))
        if (state.selected != null) PrimaryPill("Save", actions.onSave, Modifier.fillMaxWidth().padding(top = Spacing.l))
    }
}

@Composable
private fun CategoryGrid(group: PickerGroup, state: PickerState, actions: PickerActions) {
    // null is the "+ New category" cell at the end of the group (not while searching).
    val cells: List<PickerItem?> = group.items + if (state.query.isBlank()) listOf(null) else emptyList()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellMin = with(LocalDensity.current) { LedgaType.caption.fontSize.toDp() } * CELL_MIN_EMS
        val columns = ((maxWidth + Spacing.xs) / (cellMin + Spacing.xs)).toInt().coerceIn(COLUMNS - 1, COLUMNS)
        GridRows(cells, columns, group, state, actions)
    }
}

@Composable
private fun GridRows(cells: List<PickerItem?>, columns: Int, group: PickerGroup, state: PickerState, actions: PickerActions) {
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        cells.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                row.forEach { item ->
                    if (item == null) {
                        NewCategoryCell({ actions.onNewCategory(group.group) }, Modifier.weight(1f))
                    } else {
                        CategoryCell(item, item.key == state.selected, { actions.onSelect(item.key) }, Modifier.weight(1f))
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A radio-button cell: the 3D icon (★ when tracked) over the name; TalkBack hears "Electricity, tracked". */
@Composable
private fun CategoryCell(item: PickerItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = LedgaTheme.colors
    val shape = RoundedCornerShape(Radii.stat)
    val speech = if (item.tracked) "${item.name}, tracked" else item.name
    Column(
        modifier
            .clip(shape)
            .then(if (selected) Modifier.background(c.primarySoft).border(1.5.dp, c.primary, shape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = speech }
            .heightIn(min = Sizes.touchTarget)
            .padding(vertical = Spacing.s, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.clearAndSetSemantics {}) {
            CategoryIcon(item.icon3d, contentDescription = null, size = WellSize.Small)
            if (item.tracked) {
                Text(STAR, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp), style = LedgaType.caption, color = c.primary)
            }
        }
        Text(
            item.name,
            Modifier.padding(top = 4.dp).clearAndSetSemantics {},
            style = LedgaType.caption,
            color = c.ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NewCategoryCell(onClick: () -> Unit, modifier: Modifier) {
    val c = LedgaTheme.colors
    val well = RoundedCornerShape(WellSize.Small.radius)
    Column(
        modifier
            .clip(RoundedCornerShape(Radii.stat))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = Sizes.touchTarget)
            .padding(vertical = Spacing.s, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(WellSize.Small.box).clip(well).border(Sizes.hairline, c.line, well), contentAlignment = Alignment.Center) {
            Icon(Ph.PlusBold, contentDescription = null, tint = c.primary, modifier = Modifier.size(Sizes.iconSmall))
        }
        Text("New category", Modifier.padding(top = 4.dp), style = LedgaType.caption, color = c.primary, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** R43: a name is all a person types mid-task; the group is the one being added to. */
@Composable
private fun NewCategoryEditor(group: CategoryGroup, actions: PickerActions) {
    val c = LedgaTheme.colors
    var name by rememberSaveable(group) { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(top = Spacing.s)) {
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= TransactionEdits.CATEGORY_NAME_MAX) name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name") },
            singleLine = true,
            textStyle = LedgaType.body,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) actions.onCreateCategory(name) }),
        )
        Text("A new category in ${group.displayName}", Modifier.padding(top = 4.dp), style = LedgaType.caption, color = c.muted)
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LinkButton("Cancel", actions.onCancelNew)
            PrimaryPill("Add", { if (name.isNotBlank()) actions.onCreateCategory(name) })
        }
    }
}

/** "Apply to all 14 from KPLC Prepaid" (mockup `picker`) and, for a paybill, "Only account …" (R35). */
@Composable
private fun ApplyAllCard(state: PickerState, actions: PickerActions, modifier: Modifier) {
    val c = LedgaTheme.colors
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.stat)).background(c.primarySoft)) {
        ToggleLine(
            title = "Apply to all ${state.applyCount} from ${state.txName}",
            caption = "Past and future payments · creates a rule you can edit",
            checked = state.applyAll,
            onChange = actions.onApplyAll,
        )
        val forAccount = state.counts.forAccount
        if (state.showAccountOnly && forAccount != null) {
            ToggleLine(
                title = "Only account ${state.account}",
                caption = "$forAccount ${if (forAccount == 1) "payment" else "payments"} · this business only",
                checked = state.accountOnly,
                onChange = actions.onAccountOnly,
            )
        }
    }
}

/** A whole-line switch on primarySoft: one TalkBack switch, its title and caption read together. */
@Composable
private fun ToggleLine(title: String, caption: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LedgaTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        LedgaSwitch(checked = checked, onCheckedChange = null)
        Column(Modifier.weight(1f)) {
            Text(title, style = LedgaType.bodyStrong, color = c.onPrimarySoft)
            Text(caption, style = LedgaType.caption, color = c.muted)
        }
    }
}

/**
 * Opens the category picker for [code] (null = closed), from a row's long press or the transaction sheet's Category
 * row. Activity uses it now; Home and Tracker detail (4c) reuse it. Save closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerHost(code: String?, onDismiss: () -> Unit, vm: CategoryPickerViewModel = hiltViewModel(key = "category-picker")) {
    if (code == null) return
    LaunchedEffect(code) { vm.open(code) }
    val state by vm.state.collectAsStateWithLifecycle()
    var searchOpen by rememberSaveable(code) { mutableStateOf(false) }
    LedgaModalSheet(onDismiss = onDismiss, title = null) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            CategoryPickerContent(
                state = state,
                actions = PickerActions(
                    onSelect = vm::select,
                    onQuery = vm::setQuery,
                    onToggleSearch = {
                        searchOpen = !searchOpen
                        if (!searchOpen) vm.setQuery("")
                    },
                    onApplyAll = vm::setApplyAll,
                    onAccountOnly = vm::setAccountOnly,
                    onNewCategory = vm::startNewCategory,
                    onCreateCategory = vm::createCategory,
                    onCancelNew = vm::cancelNewCategory,
                    onSave = { vm.save(onDismiss) },
                ),
                searchOpen = searchOpen,
            )
        }
    }
}
