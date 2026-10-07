package com.ledga.app.ui.categories

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ledga.app.data.edit.CategoryLooks
import com.ledga.app.ui.design.components.CategoryIcon
import com.ledga.app.ui.design.components.LedgaModalSheet
import com.ledga.app.ui.design.components.SearchField
import com.ledga.app.ui.design.components.SkeletonRow
import com.ledga.app.ui.design.icons.CatalogIcon
import com.ledga.app.ui.design.icons.IconCatalog
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** What the icon sheet says (D5). */
object IconPickerText {
    /** "Smileys & Emotion" → "Smileys & emotion": the set's group names, in the app's sentence case. */
    fun groupLabel(group: String): String = group.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }

    fun noMatch(query: String): String = "No icon matches \"${query.trim()}\""
}

/**
 * The icon sheet (D5): a search over names and keywords (R98); with no query, Suggested ([CategoryLooks.ICONS]) and then
 * the whole set under its groups. The current icon is ringed. A radio group for TalkBack; each cell says its icon's name.
 */
@Composable
fun IconPickerContent(
    selected: String,
    icons: List<CatalogIcon>?,
    query: String,
    onQuery: (String) -> Unit,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LedgaTheme.colors
    Column(modifier.fillMaxWidth()) {
        SearchField(query, onQuery, "Search icons", Modifier.fillMaxWidth().padding(bottom = Spacing.s))
        if (icons == null) {
            repeat(3) { SkeletonRow() }
            return@Column
        }
        val results = IconCatalog.search(icons, query)
        if (query.isNotBlank() && results.isEmpty()) {
            Text(IconPickerText.noMatch(query), Modifier.padding(vertical = Spacing.l), style = LedgaType.body, color = c.muted)
            return@Column
        }
        LazyVerticalGrid(
            GridCells.Adaptive(CELL),
            Modifier.weight(1f, fill = false).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (query.isBlank()) {
                val byKey = icons.associateBy { it.key }
                val suggested = CategoryLooks.ICONS.mapNotNull { byKey[it] }
                if (suggested.isNotEmpty()) {
                    header("Suggested")
                    items(suggested, key = { "s-" + it.key }) { IconCell(it, it.key == selected, onPick) }
                }
                IconCatalog.GROUPS.forEach { group ->
                    val inGroup = results.filter { it.group == group }
                    if (inGroup.isNotEmpty()) {
                        header(IconPickerText.groupLabel(group))
                        items(inGroup, key = { it.key }) { IconCell(it, it.key == selected, onPick) }
                    }
                }
            } else {
                items(results, key = { it.key }) { IconCell(it, it.key == selected, onPick) }
            }
        }
    }
}

private fun LazyGridScope.header(text: String) = item(key = "h-$text", span = { GridItemSpan(maxLineSpan) }) {
    Text(
        text.uppercase(Locale.ENGLISH),
        Modifier.padding(top = Spacing.m, bottom = Spacing.xs).semantics { heading() },
        style = LedgaType.overline,
        color = LedgaTheme.colors.muted,
    )
}

@Composable
private fun IconCell(icon: CatalogIcon, chosen: Boolean, onPick: (String) -> Unit) {
    val c = LedgaTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .size(CELL)
            .clip(shape)
            .border(if (chosen) 2.dp else 0.dp, if (chosen) c.primary else c.surfaceSheet, shape)
            .selectable(chosen, role = Role.RadioButton, onClick = { onPick(icon.key) })
            .semantics { contentDescription = IconCatalog.displayName(icon) },
        contentAlignment = Alignment.Center,
    ) { CategoryIcon(icon.key, contentDescription = null) }
}

/** The icon sheet (route): reads the set's index once, off the main thread. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerSheet(selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val assets = LocalContext.current.assets
    val icons by produceState<List<CatalogIcon>?>(null) { value = withContext(Dispatchers.IO) { IconCatalog.all(assets) } }
    var query by rememberSaveable { mutableStateOf("") }
    LedgaModalSheet(onDismiss = onDismiss, title = "Icon") {
        IconPickerContent(selected, icons, query, onQuery = { query = it }, onPick = onPick)
    }
}

/** A cell is a 48 dp target with room for the ring. */
private val CELL = 56.dp
