package com.ledga.app.ui.design.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
class RowsSpecimenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun day() = compose.snap("rows_day") { DaySpecimen() }

    @Test
    fun list() = compose.snap("rows_list") { ListSpecimen() }
}

@Composable
private fun DaySpecimen() {
    Column {
        DayHeader("TODAY", outCents = 535_000, inCents = 500_000, modifier = Modifier.cardSegment(Segment.Top))
        TxRow(
            Leading.Icon("fluent_fuel_pump"), "Rubis Langata", "Fuel", 300_000, inflow = false,
            speech = "s", balanceText = "Bal 4,231.50", modifier = Modifier.cardSegment(Segment.Middle), subtitleTail = "6:40 PM",
        )
        TxRow(
            Leading.Icon("fluent_high_voltage"), "KPLC Prepaid", "Electricity", 150_000, inflow = false,
            speech = "s", modifier = Modifier.cardSegment(Segment.Middle, dividerAbove = true), subtitleTail = "2:15 PM",
        )
        TxRow(
            Leading.Avatar("Jane Doe", inflow = true), "Jane Doe", "Received", 500_000, inflow = true,
            speech = "s", modifier = Modifier.cardSegment(Segment.Middle, dividerAbove = true), subtitleTail = "11:02 AM",
        )
        TxRow(
            Leading.Icon("fluent_shopping_cart"), "Naivas Supermarket Westlands", "Fuliza Ksh 300",
            85_000, inflow = false, speech = "s", modifier = Modifier.cardSegment(Segment.Bottom, dividerAbove = true),
            subtitleTail = "9:15 AM",
        )
        Spacer(Modifier.height(Spacing.m))
        DayHeader("THU, 2 OCT 2025", outCents = 120_000, inCents = 0, modifier = Modifier.cardSegment(Segment.Top))
        TxRow(
            Leading.Icon("fluent_dollar_banknote"), "Agent 123456", "Cash withdrawal", 120_000, inflow = false,
            speech = "s", modifier = Modifier.cardSegment(Segment.Bottom), subtitleTail = "4:05 PM",
        )
    }
}

@Composable
private fun ListSpecimen() {
    LedgaCard(contentPadding = PaddingValues(0.dp)) {
        ListRow("M-Pesa lines", subtitle = "2 lines", iconKey = "fluent_mobile_phone_with_arrow", onClick = {})
        RowDivider()
        ListRow("Appearance", iconKey = "fluent_artist_palette", trailing = RowTrailing.Value("System"), onClick = {})
        RowDivider()
        ListRow(
            "Updates", subtitle = "v2.0.0-beta.3 · up to date", iconKey = "fluent_rocket", badge = "BETA",
            trailing = RowTrailing.Toggle(true) {},
        )
        RowDivider()
        ListRow("Notifications", iconKey = "fluent_bell", trailing = RowTrailing.Toggle(false) {})
    }
}
