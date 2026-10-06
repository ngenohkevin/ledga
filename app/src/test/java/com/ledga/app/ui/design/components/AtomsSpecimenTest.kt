package com.ledga.app.ui.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.ledga.app.testing.SPECIMEN_QUALIFIERS
import com.ledga.app.testing.snap
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SPECIMEN_QUALIFIERS)
class AtomsSpecimenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun atoms() = compose.snap("atoms") { AtomsSpecimen() }
}

@Composable
private fun AtomsSpecimen() {
    val c = LedgaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        SectionHeader("Recent", actionLabel = "See all", onAction = {})
        LedgaCard {
            Text("M-Pesa balance", style = LedgaType.caption, color = c.muted)
            AmountText(123_456_789)
            Text("Updated 7:12 PM · from Safaricom", style = LedgaType.caption, color = c.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            StatTile("This month", "Ksh 1,111", Modifier.weight(1f))
            StatTile("Avg / month", "Ksh 1,980", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon("fluent_high_voltage", "Electricity", size = WellSize.Small)
            CategoryIcon("fluent_droplet", "Water", size = WellSize.Medium)
            CategoryIcon("fluent_fuel_pump", "Fuel", size = WellSize.Large)
            InitialAvatar("Jane Doe", inflow = true)
            InitialAvatar("Peter Otieno", inflow = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            DeltaBadge("▲ 9% vs Aug", DeltaTone.Bad)
            DeltaBadge("▼ 4%", DeltaTone.Good)
            DeltaBadge("Same", DeltaTone.Neutral)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            PrimaryPill("Add rule", onClick = {}, icon = Ph.PlusBold)
            SoftPill("History", onClick = {})
        }
        LedgaTile { Text("A tile", style = LedgaType.cardTitle, color = c.ink) }
    }
}
