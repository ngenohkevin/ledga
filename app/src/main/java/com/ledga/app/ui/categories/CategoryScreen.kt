package com.ledga.app.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledga.app.ui.app.DetailFrame
import com.ledga.app.ui.design.components.AddChip
import com.ledga.app.ui.design.components.Banner
import com.ledga.app.ui.design.components.BannerTone
import com.ledga.app.ui.design.components.EmptyState
import com.ledga.app.ui.design.components.LedgaCard
import com.ledga.app.ui.design.components.LedgaModalSheet
import com.ledga.app.ui.design.components.LedgaSwitch
import com.ledga.app.ui.design.components.ListRow
import com.ledga.app.ui.design.components.PrimaryPill
import com.ledga.app.ui.design.components.RowDivider
import com.ledga.app.ui.design.components.RowTrailing
import com.ledga.app.ui.design.components.SectionHeader
import com.ledga.app.ui.design.components.SkeletonRow
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Sizes
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.app.ui.rules.AddRuleSheet
import com.ledga.app.ui.rules.RenameSheet
import com.ledga.core.model.Categories
import kotlinx.coroutines.launch

/** What a category's screen does. Every default does nothing, for screenshots and tests. */
data class CategoryActions(
    val onBack: () -> Unit = {},
    val onRename: () -> Unit = {},
    val onIcon: () -> Unit = {},
    val onColour: () -> Unit = {},
    val onTracked: (Boolean) -> Unit = {},
    val onArchive: () -> Unit = {},
    val onBringBack: () -> Unit = {},
    val onRuleEnabled: (Long, Boolean) -> Unit = { _, _ -> },
    val onDeleteRule: (RuleUi) -> Unit = {},
    val onAddRule: () -> Unit = {},
    val onSeePayments: () -> Unit = {},
)

/** A category's screen (R67): details, rules, "See payments", and archive for your own. */
@Composable
fun CategoryContent(ui: CategoryUi, actions: CategoryActions, modifier: Modifier = Modifier) {
    val category = ui.category
    DetailFrame(category?.name.orEmpty(), onBack = actions.onBack, modifier = modifier, iconKey = category?.icon3d) {
        when {
            !ui.loaded -> Column(Modifier.padding(horizontal = Spacing.screen)) { repeat(5) { SkeletonRow() } }
            ui.missing || category == null -> EmptyState("fluent_package", "This category isn't here", "It may belong to a backup from a newer Ledga.")
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                if (category.archived) {
                    Banner("Archived. It's hidden from the category picker, the filters and Trackers.", BannerTone.Info, actionLabel = "Bring back", onAction = actions.onBringBack)
                }
                Details(ui, actions)
                Rules(ui, actions)
                LedgaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                    ListRow("See payments", subtitle = "${ui.payments} ${if (ui.payments == 1) "payment" else "payments"}", onClick = actions.onSeePayments)
                }
                if (ui.own && !category.archived) {
                    LedgaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                        ListRow("Archive category", subtitle = "Hide it from the picker. Its payments and rules stay.", trailing = RowTrailing.None, onClick = actions.onArchive)
                    }
                }
            }
        }
    }
}

@Composable
private fun Details(ui: CategoryUi, actions: CategoryActions) {
    val c = ui.category ?: return
    LedgaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
        ListRow("Name", subtitle = c.name, onClick = actions.onRename)
        RowDivider(Modifier.padding(horizontal = Spacing.m))
        ListRow("Group", subtitle = c.groupKey.displayName, trailing = RowTrailing.None)
        if (ui.own) {
            RowDivider(Modifier.padding(horizontal = Spacing.m))
            ListRow("Icon", subtitle = CategoryText.iconName(c.icon3d), iconKey = c.icon3d, onClick = actions.onIcon)
            RowDivider(Modifier.padding(horizontal = Spacing.m))
            ListRow("Colour", subtitle = CategoryText.swatchName(c.color), onClick = actions.onColour)
        }
        if (ui.canTrack) {
            RowDivider(Modifier.padding(horizontal = Spacing.m))
            ListRow("Track month by month", subtitle = "Shows it on Home and in Trackers", trailing = RowTrailing.Toggle(c.tracked, actions.onTracked))
        }
    }
}

@Composable
private fun Rules(ui: CategoryUi, actions: CategoryActions) {
    val c = LedgaTheme.colors
    val own = ui.category?.key == Categories.OWN_ACCOUNTS
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionHeader("Rules")
        LedgaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
            if (ui.rules.isEmpty()) {
                Text(
                    if (own) "None yet. A payment's \"My own account\" switch makes one." else "No rules yet. Payments you file here yourself still count.",
                    Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                    style = LedgaType.caption,
                    color = c.muted,
                )
            }
            ui.rules.forEachIndexed { i, r ->
                if (i > 0) RowDivider(Modifier.padding(horizontal = Spacing.m))
                RuleItem(r, actions)
            }
            if (ui.canAddRule) AddChip("Add rule", actions.onAddRule, Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s))
        }
    }
}

/** R73: the whole row is the rule's switch; your own rule also has Delete. */
@Composable
private fun RuleItem(r: RuleUi, actions: CategoryActions) {
    val c = LedgaTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = r.enabled, role = Role.Switch, onValueChange = { actions.onRuleEnabled(r.id, it) })
            .heightIn(min = 56.dp)
            .padding(start = 14.dp, end = Spacing.s, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Column(Modifier.weight(1f)) {
            Text(r.label, style = LedgaType.bodyStrong, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(CategoryText.ruleOrigin(r), style = LedgaType.caption, color = c.muted)
        }
        if (!r.builtIn) {
            IconButton(onClick = { actions.onDeleteRule(r) }) {
                Icon(Ph.Trash, contentDescription = "Delete rule: ${r.label}", tint = c.ink2, modifier = Modifier.size(Sizes.iconSmall))
            }
        }
        LedgaSwitch(checked = r.enabled, onCheckedChange = null)
    }
}

/** The sheets a category's screen opens. */
private enum class CategorySheet { RENAME, ICON, COLOUR, ADD_RULE, ARCHIVE }

/** A category's screen (route): its sheets, and Undo after deleting a rule (R49, R73). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(onBack: () -> Unit, onSeePayments: () -> Unit, vm: CategoryViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<CategorySheet?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val category = ui.category
    Box(Modifier.fillMaxSize()) {
        CategoryContent(
            ui,
            CategoryActions(
                onBack = onBack,
                onRename = { sheet = CategorySheet.RENAME },
                onIcon = { sheet = CategorySheet.ICON },
                onColour = { sheet = CategorySheet.COLOUR },
                onTracked = vm::setTracked,
                onArchive = { sheet = CategorySheet.ARCHIVE },
                onBringBack = { vm.setArchived(false) },
                onRuleEnabled = vm::setRuleEnabled,
                onDeleteRule = { r ->
                    vm.deleteRule(r.id) { removed ->
                        scope.launch {
                            if (snackbar.showSnackbar("Rule deleted", actionLabel = "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                                vm.restoreRule(removed)
                            }
                        }
                    }
                },
                onAddRule = { sheet = CategorySheet.ADD_RULE },
                onSeePayments = {
                    vm.openPayments()
                    onSeePayments()
                },
            ),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(Spacing.l))
    }
    if (category == null) return
    val close = { sheet = null }
    when (sheet) {
        CategorySheet.RENAME -> RenameSheet(category.name, onSave = { name, done -> vm.rename(name, done) }, onClose = close)
        CategorySheet.ICON -> LedgaModalSheet(onDismiss = close, title = "Icon") {
            IconChoiceContent(category.icon3d) {
                vm.setIcon(it)
                close()
            }
        }
        CategorySheet.COLOUR -> LedgaModalSheet(onDismiss = close, title = "Colour") {
            ColourChoiceContent(category.color) {
                vm.setColor(it)
                close()
            }
        }
        CategorySheet.ADD_RULE -> {
            val closeRule = {
                sheet = null
                vm.clearPreview()
            }
            AddRuleSheet(category.name, preview, onCount = vm::previewRule, onSave = { n, a -> vm.addRule(n, a, closeRule) }, onClose = closeRule)
        }
        CategorySheet.ARCHIVE -> LedgaModalSheet(onDismiss = close, title = "Archive ${category.name}?") {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(CategoryText.archiveText(ui.payments, ui.rules.count { it.enabled }), style = LedgaType.body, color = LedgaTheme.colors.ink2)
                PrimaryPill("Archive", {
                    vm.setArchived(true)
                    close()
                }, Modifier.fillMaxWidth().padding(top = Spacing.l))
            }
        }
        null -> Unit
    }
}
