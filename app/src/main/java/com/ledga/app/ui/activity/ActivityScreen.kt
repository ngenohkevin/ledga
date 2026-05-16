package com.ledga.app.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.ledga.app.ui.components.v2.LedgaTopBar
import com.ledga.app.ui.theme.LedgaText
import com.ledga.app.ui.theme.Space
import com.ledga.app.ui.transactions.TransactionsScreen
import com.ledga.app.ui.trends.TrendsScreen

private enum class Segment { Transactions, Trends }

/**
 * Activity tab — pairs the Transactions list and Trends analytics behind
 * a single tab with a segmented switch on top. Users always toggle between
 * "what happened" and "what's the pattern"; they shouldn't be two taps apart.
 */
@Composable
fun ActivityScreen() {
    var segment by remember { mutableStateOf(Segment.Transactions) }

    Column(modifier = Modifier.fillMaxSize()) {
        LedgaTopBar(title = "Activity")

        Segmented(
            options = Segment.entries.toList(),
            selected = segment,
            onSelect = { segment = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.Screen, vertical = Space.s4),
        )

        when (segment) {
            Segment.Transactions -> TransactionsScreen()
            Segment.Trends -> TrendsScreen()
        }
    }
}

@Composable
private fun Segmented(
    options: List<Segment>,
    selected: Segment,
    onSelect: (Segment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            options.forEach { opt ->
                val isSel = opt == selected
                val bg = if (isSel) MaterialTheme.colorScheme.surface
                else androidx.compose.ui.graphics.Color.Transparent
                val fg = if (isSel) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onSelect(opt) },
                    shape = RoundedCornerShape(999.dp),
                    color = bg,
                ) {
                    Text(
                        text = opt.name,
                        style = LedgaText.BodyL,
                        color = fg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}
