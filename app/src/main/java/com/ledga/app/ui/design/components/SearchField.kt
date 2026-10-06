package com.ledga.app.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Sizes
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType

/** Activity's search pill (spec §10.4): magnifier, placeholder in muted, and a clear button once there is text. */
@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    val pill = RoundedCornerShape(percent = 50)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = LedgaType.body.copy(color = c.ink),
        cursorBrush = SolidColor(c.primary),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = placeholder },
        decorationBox = { field ->
            Row(
                Modifier
                    .clip(pill)
                    .background(c.surface)
                    .border(Sizes.hairline, c.line, pill)
                    .heightIn(min = Sizes.touchTarget)
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Icon(Ph.MagnifyingGlass, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    if (query.isEmpty()) {
                        Text(placeholder, style = LedgaType.body, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    field()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Ph.X, contentDescription = "Clear search", tint = c.muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
    )
}
