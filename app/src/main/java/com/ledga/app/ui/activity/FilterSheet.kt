package com.ledga.app.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.ledga.app.data.derive.DateFilter
import com.ledga.app.data.derive.DatePreset
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.ui.design.components.ChoiceChip
import com.ledga.app.ui.design.components.LedgaModalSheet
import com.ledga.app.ui.design.components.LinkButton
import com.ledga.app.ui.design.components.ListRow
import com.ledga.app.ui.design.components.PrimaryPill
import com.ledga.app.ui.design.components.RowTrailing
import com.ledga.app.ui.design.format.AmountFormat
import com.ledga.app.ui.design.format.DateLabels
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.core.money.Money
import java.time.LocalDate
import java.util.Locale

private const val MINIMUM_CHARS = 12

/** The filter sheet over Activity (spec §10.4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(current: TransactionFilter, categories: List<CategoryRow>, today: LocalDate, onApply: (TransactionFilter) -> Unit, onDismiss: () -> Unit) {
    LedgaModalSheet(onDismiss = onDismiss, title = "Filters") {
        Column(Modifier.verticalScroll(rememberScrollState())) { FilterSheetContent(current, categories, today, onApply) }
    }
}

/**
 * The filter sheet's body (spec §10.4, R39):
 * - categories (any number);
 * - a date preset, a custom range (R70), or the month Spending sent;
 * - a minimum amount;
 * - "Show hidden payments".
 *
 * Nothing applies until "Show results".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSheetContent(current: TransactionFilter, categories: List<CategoryRow>, today: LocalDate, onApply: (TransactionFilter) -> Unit) {
    var keys by remember(current) { mutableStateOf(current.categoryKeys) }
    var dates by remember(current) { mutableStateOf(current.dates) }
    var minimum by remember(current) { mutableStateOf(current.minAmountCents?.let { AmountFormat.plain(it) }.orEmpty()) }
    var hidden by remember(current) { mutableStateOf(current.includeHidden) }
    var picking by remember { mutableStateOf<Edge?>(null) }
    Column(Modifier.fillMaxWidth()) {
        SectionLabel("Category")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            // An archived category stays while it is one of this filter's (R72): "Filters, 1 on" must show what is on.
            categories.filter { !it.archived || it.key in current.categoryKeys }.forEach { cat ->
                ChoiceChip(cat.name, cat.key in keys, { keys = if (cat.key in keys) keys - cat.key else keys + cat.key })
            }
        }
        SectionLabel("Date")
        FlowRow(
            Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            ChoiceChip("Any time", dates == null, { dates = null }, role = Role.RadioButton)
            DatePreset.entries.forEach { preset ->
                val chosen = DateFilter.Preset(preset)
                ChoiceChip(preset.label, dates == chosen, { dates = chosen }, role = Role.RadioButton)
            }
            ChoiceChip(
                "Custom range",
                dates is DateFilter.Custom,
                { if (dates !is DateFilter.Custom) dates = DateFilter.Custom(today.withDayOfMonth(1), today) },
                role = Role.RadioButton,
            )
            // A month Spending sent (R69) keeps its own chip.
            (dates as? DateFilter.Month)?.let { ChoiceChip(FilterText.dateLabel(it), selected = true, onClick = {}, role = Role.RadioButton) }
        }
        (dates as? DateFilter.Custom)?.let { range ->
            ListRow("From", Modifier.padding(top = Spacing.s), trailing = RowTrailing.Value(DateLabels.date(range.from)), onClick = { picking = Edge.FROM })
            ListRow("To", trailing = RowTrailing.Value(DateLabels.date(range.to)), onClick = { picking = Edge.TO })
        }
        SectionLabel("Minimum amount")
        OutlinedTextField(
            value = minimum,
            onValueChange = { text -> minimum = text.filter { it.isDigit() || it == '.' || it == ',' }.take(MINIMUM_CHARS) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ksh") },
            singleLine = true,
            textStyle = LedgaType.body,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        )
        ListRow(
            "Show hidden payments",
            Modifier.padding(top = Spacing.s),
            subtitle = "Hidden payments never count in totals",
            trailing = RowTrailing.Toggle(hidden) { hidden = it },
        )
        Row(Modifier.fillMaxWidth().padding(top = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            LinkButton("Reset", {
                keys = emptySet()
                dates = null
                minimum = ""
                hidden = false
            })
            Spacer(Modifier.weight(1f))
            PrimaryPill("Show results", {
                onApply(
                    TransactionFilter(
                        categoryKeys = keys,
                        dates = dates,
                        minAmountCents = Money.parse(minimum)?.cents?.takeIf { it > 0 },
                        includeHidden = hidden,
                    ),
                )
            })
        }
        val range = dates as? DateFilter.Custom
        val edge = picking
        if (range != null && edge != null) {
            RangeDatePicker(
                initial = if (edge == Edge.FROM) range.from else range.to,
                today = today,
                onPicked = { day ->
                    dates = if (edge == Edge.FROM) DateFilter.Custom.of(day, range.to) else DateFilter.Custom.of(range.from, day)
                    picking = null
                },
                onDismiss = { picking = null },
            )
        }
    }
}

/** Which end of a custom range the date picker is choosing (R70). */
private enum class Edge { FROM, TO }

/** M3's date picker in its dialog (R70): Nairobi days through UTC midnights, nothing after today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeDatePicker(initial: LocalDate, today: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val c = LedgaTheme.colors
    val state = rememberDatePickerState(initialSelectedDateMillis = PickerDates.toMillis(initial), selectableDates = PickerDates.selectable(today))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPicked(PickerDates.fromMillis(it)) } ?: onDismiss() }) {
                Text("OK", style = LedgaType.label, color = c.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = LedgaType.label, color = c.primary) } },
        colors = DatePickerDefaults.colors(containerColor = c.surfaceSheet),
    ) {
        DatePicker(state = state, colors = DatePickerDefaults.colors(containerColor = c.surfaceSheet))
    }
}

@Composable
private fun SectionLabel(text: String) = Text(
    text.uppercase(Locale.ENGLISH),
    Modifier.padding(top = Spacing.l, bottom = Spacing.s).semantics { heading() },
    style = LedgaType.overline,
    color = LedgaTheme.colors.muted,
)
