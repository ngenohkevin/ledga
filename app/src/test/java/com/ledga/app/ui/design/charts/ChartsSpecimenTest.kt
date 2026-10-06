package com.ledga.app.ui.design.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ledga.app.testing.SPECIMEN_QUALIFIERS
import com.ledga.app.testing.snap
import com.ledga.app.ui.design.components.AmountText
import com.ledga.app.ui.design.components.CategoryIcon
import com.ledga.app.ui.design.components.LedgaCard
import com.ledga.app.ui.design.components.LedgaTile
import com.ledga.app.ui.design.components.RowDivider
import com.ledga.app.ui.design.components.WellSize
import com.ledga.app.ui.design.format.AmountFormat
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.CategoryPalette
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.core.model.Categories
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SPECIMEN_QUALIFIERS)
class ChartsSpecimenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun small() = compose.snap("charts_small") { SmallCharts() }

    @Test
    fun column() = compose.snap("charts_column") { ColumnCharts() }
}

@Composable
private fun categoryColor(key: String) = CategoryPalette.seeded(key)!!.pick(LedgaTheme.colors.isDark)

@Composable
private fun SmallCharts() {
    val c = LedgaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        LedgaCard {
            Text("Spent this month", style = LedgaType.caption, color = c.muted)
            AmountText(1_111_100L, style = LedgaType.amountL)
            MiniBars(
                listOf(3_820_000L, 4_110_000L, 3_560_000L, 4_480_000L, 4_250_000L, 2_910_000L),
                Modifier.padding(top = Spacing.m),
                inProgressIndex = 5,
                contentDescription = "Spending, last 6 months",
            )
            Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("APR", "MAY", "JUN", "JUL", "AUG", "SEP").forEach {
                    Text(it, Modifier.weight(1f), style = LedgaType.caption, color = c.muted, textAlign = TextAlign.Center)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            TrackerTile("fluent_high_voltage", "Electricity", "Ksh 1,111", categoryColor(Categories.ELECTRICITY), Modifier.weight(1f))
            TrackerTile("fluent_droplet", "Water", "Ksh 1,200", categoryColor(Categories.WATER), Modifier.weight(1f))
        }
        LedgaCard(contentPadding = PaddingValues(0.dp)) {
            ShareBar("fluent_shopping_cart", "Groceries", "Ksh 12,400", 0.42f, categoryColor(Categories.GROCERIES), "42% · 9 payments")
            RowDivider()
            ShareBar("fluent_fuel_pump", "Fuel", "Ksh 8,000", 0.27f, categoryColor(Categories.FUEL), "27% · 3 payments")
            RowDivider()
            ShareBar("fluent_high_voltage", "Electricity", "Ksh 1,111", 0.08f, categoryColor(Categories.ELECTRICITY), "8% · 2 payments")
        }
    }
}

@Composable
private fun TrackerTile(icon: String, name: String, amount: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val c = LedgaTheme.colors
    LedgaTile(modifier) {
        CategoryIcon(icon, contentDescription = null, size = WellSize.Small)
        Text(name, Modifier.padding(top = 8.dp), style = LedgaType.caption, color = c.muted)
        Text(amount, style = LedgaType.amountM, color = c.ink)
        SparkBars(listOf(210_000L, 245_000L, 198_000L, 230_000L, 260_000L, 120_000L), color, Modifier.padding(top = 7.dp))
    }
}

@Composable
private fun ColumnCharts() {
    val c = LedgaTheme.colors
    val electricity = categoryColor(Categories.ELECTRICITY)
    val months = listOf("NOV", "DEC", "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT")
    val values = listOf(210_000L, 245_000L, 198_000L, 230_000L, 260_000L, 215_000L, 240_000L, 225_000L, 250_000L, 245_000L, 232_000L, 120_000L)
    val single = months.mapIndexed { i, m ->
        Bar(m, listOf(values[i]), speech = "$m, Ksh ${AmountFormat.plain(values[i])}", inProgress = i == months.lastIndex)
    }
    val keys = listOf(Categories.ELECTRICITY, Categories.WATER, Categories.FUEL, Categories.CAR_SERVICE)
    val stackColors = keys.map { categoryColor(it) }
    val stacked = listOf("MAY", "JUN", "JUL", "AUG", "SEP", "OCT").mapIndexed { i, m ->
        Bar(
            m,
            listOf(240_000L + i * 5_000L, 120_000L, 800_000L - i * 40_000L, if (i == 2) 650_000L else 0L),
            speech = m,
            inProgress = i == 5,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        LedgaCard {
            Text("Electricity", style = LedgaType.cardTitle, color = c.ink)
            ColumnChart(
                single, listOf(electricity), summary = "Electricity, 12 months",
                modifier = Modifier.padding(top = Spacing.m),
                selectedIndex = 10, onSelect = {},
                softColors = listOf(CategoryPalette.soft(electricity, c.surface)),
                average = 230_000L, showGrid = true,
                tooltip = { ChartTooltip("Ksh 2,320 · Sep", "3 payments") },
            )
        }
        LedgaCard {
            Text("Bills and running costs", style = LedgaType.cardTitle, color = c.ink)
            ColumnChart(stacked, stackColors, summary = "Trackers, 6 months", modifier = Modifier.padding(top = Spacing.m), height = 120.dp)
            ChartLegend(
                listOf("Electricity" to stackColors[0], "Water" to stackColors[1], "Fuel" to stackColors[2], "Car service" to stackColors[3]),
                Modifier.padding(top = Spacing.s),
            )
        }
    }
}
