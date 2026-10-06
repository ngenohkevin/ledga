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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.ui.design.components.ChoiceChip
import com.ledga.app.ui.design.components.LedgaModalSheet
import com.ledga.app.ui.design.components.LinkButton
import com.ledga.app.ui.design.components.ListRow
import com.ledga.app.ui.design.components.PrimaryPill
import com.ledga.app.ui.design.components.RowTrailing
import com.ledga.app.ui.design.format.AmountFormat
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.core.money.Money
import java.time.Instant
import java.util.Locale

private const val MINIMUM_CHARS = 12

/** The filter sheet over Activity (spec §10.4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(current: TransactionFilter, categories: List<CategoryRow>, now: Instant, onApply: (TransactionFilter) -> Unit, onDismiss: () -> Unit) {
    LedgaModalSheet(onDismiss = onDismiss, title = "Filters") {
        Column(Modifier.verticalScroll(rememberScrollState())) { FilterSheetContent(current, categories, now, onApply) }
    }
}

/**
 * The filter sheet's body (spec §10.4, R39):
 * - categories (any number);
 * - a date preset, or the month Spending sent;
 * - a minimum amount;
 * - "Show hidden payments".
 *
 * Nothing applies until "Show results".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSheetContent(current: TransactionFilter, categories: List<CategoryRow>, now: Instant, onApply: (TransactionFilter) -> Unit) {
    var keys by remember(current) { mutableStateOf(current.categoryKeys) }
    var dates by remember(current) { mutableStateOf(current.dates) }
    var minimum by remember(current) { mutableStateOf(current.minAmountCents?.let { AmountFormat.plain(it) }.orEmpty()) }
    var hidden by remember(current) { mutableStateOf(current.includeHidden) }
    Column(Modifier.fillMaxWidth()) {
        SectionLabel("Category")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            categories.filterNot { it.archived }.forEach { cat ->
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
                ChoiceChip(preset.label, dates?.label == preset.label, { dates = preset.filter(now) }, role = Role.RadioButton)
            }
            val sent = dates?.takeIf { d -> DatePreset.entries.none { it.label == d.label } }
            if (sent != null) ChoiceChip(sent.label, selected = true, onClick = {}, role = Role.RadioButton)
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
    }
}

@Composable
private fun SectionLabel(text: String) = Text(
    text.uppercase(Locale.ENGLISH),
    Modifier.padding(top = Spacing.l, bottom = Spacing.s).semantics { heading() },
    style = LedgaType.overline,
    color = LedgaTheme.colors.muted,
)
