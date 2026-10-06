package com.ledga.app.ui.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.ledga.app.testing.SPECIMEN_QUALIFIERS
import com.ledga.app.testing.snap
import com.ledga.app.ui.design.tokens.Spacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SPECIMEN_QUALIFIERS)
class ControlsSpecimenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun chips() = compose.snap("chips") { ChipsSpecimen() }

    @Test
    fun segments() = compose.snap("segments") { SegmentsSpecimen() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsSpecimen() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChoiceChip("All", selected = true, onClick = {})
            ChoiceChip("Money out", selected = false, onClick = {})
            ChoiceChip("Money in", selected = false, onClick = {})
            ChoiceChip("Fuliza", selected = false, onClick = {})
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoChip("Account 37110…")
            InfoChip("Fee 13.00")
            InfoChip("Fuliza covered 300.00", tone = ChipTone.Warning)
            InfoChip("Owed 1,250.00", tone = ChipTone.Danger)
            InfoChip("Saved", tone = ChipTone.Good)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RuleChip("Name has KPLC", onRemove = {})
            RuleChip("Account 37110…")
            AddChip("Add rule", onClick = {})
            LineChip("All lines", onClick = {})
        }
    }
}

@Composable
private fun SegmentsSpecimen() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        SegmentedControl(listOf("Week", "Month", "Year"), selected = 1, onSelect = {})
        SegmentedControl(listOf("Transactions", "Spending", "People"), selected = 0, onSelect = {})
        SearchField("", {}, "Search name, phone, code, amount")
        SearchField("naivas", {}, "Search name, phone, code, amount")
    }
}

