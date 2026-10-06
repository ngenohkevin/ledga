package com.ledga.app.ui.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.ui.app.DetailFrame
import com.ledga.app.ui.app.GroupLabel
import com.ledga.app.ui.design.components.AddChip
import com.ledga.app.ui.design.components.LedgaCard
import com.ledga.app.ui.design.components.LedgaModalSheet
import com.ledga.app.ui.design.components.ListRow
import com.ledga.app.ui.design.components.PrimaryPill
import com.ledga.app.ui.design.components.RowDivider
import com.ledga.app.ui.design.components.SkeletonRow
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.app.ui.tx.TxText
import com.ledga.core.model.CategoryGroup

/** What Categories & rules' taps do. Every default does nothing, for screenshots and tests. */
data class CategoriesActions(val onBack: () -> Unit = {}, val onOpen: (String) -> Unit = {}, val onNew: (CategoryGroup) -> Unit = {})

/** Categories & rules (R67): one card per group, then Archived (R72). */
@Composable
fun CategoriesContent(ui: CategoriesUi, actions: CategoriesActions, modifier: Modifier = Modifier) {
    DetailFrame("Categories & rules", onBack = actions.onBack, modifier = modifier) {
        if (!ui.loaded) {
            Column(Modifier.padding(horizontal = Spacing.screen)) { repeat(6) { SkeletonRow() } }
            return@DetailFrame
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl)) {
            item {
                Text(
                    "Rules file payments into categories by their name. Any rule can be switched off, built-in ones too.",
                    style = LedgaType.body,
                    color = LedgaTheme.colors.muted,
                )
            }
            ui.groups.forEach { g -> item(key = g.group.name) { GroupCard(g.group.displayName, g.rows, actions, newIn = g.group) } }
            if (ui.archived.isNotEmpty()) item(key = "archived") { GroupCard("Archived", ui.archived, actions, newIn = null) }
        }
    }
}

@Composable
private fun GroupCard(title: String, rows: List<CategoryListRow>, actions: CategoriesActions, newIn: CategoryGroup?) {
    Column {
        GroupLabel(title)
        LedgaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
            rows.forEachIndexed { i, r ->
                if (i > 0) RowDivider(Modifier.padding(horizontal = Spacing.m))
                ListRow(
                    TxText.categoryLabel(r.category.name, r.category.tracked),
                    subtitle = CategoryText.rulesLine(r.rulesOn, r.rulesOff),
                    iconKey = r.category.icon3d,
                    onClick = { actions.onOpen(r.category.key) },
                )
            }
            if (newIn != null) {
                // Six cards each end in "New category": TalkBack names the group.
                AddChip(
                    "New category",
                    { actions.onNew(newIn) },
                    Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s).semantics { contentDescription = "New category in ${newIn.displayName}" },
                )
            }
        }
    }
}

/** "+ New category": a name (1–30 characters); the group is the card it was asked from. */
@Composable
fun NewCategoryContent(name: String, onName: (String) -> Unit, onSave: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        OutlinedTextField(name, { onName(it.take(TransactionEdits.CATEGORY_NAME_MAX)) }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
        PrimaryPill("Create", onSave, Modifier.fillMaxWidth().padding(top = Spacing.m), enabled = name.isNotBlank())
    }
}

/** Categories & rules (route): "+ New category" makes it and opens it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit, onOpen: (String) -> Unit, vm: CategoriesViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var newIn by rememberSaveable { mutableStateOf<CategoryGroup?>(null) }
    CategoriesContent(ui, CategoriesActions(onBack = onBack, onOpen = onOpen, onNew = { newIn = it }))
    newIn?.let { group ->
        var name by rememberSaveable { mutableStateOf("") }
        LedgaModalSheet(onDismiss = { newIn = null }, title = "New category in ${group.displayName}") {
            NewCategoryContent(name, onName = { name = it }, onSave = {
                vm.create(name, group) { key ->
                    newIn = null
                    onOpen(key)
                }
            })
        }
    }
}
