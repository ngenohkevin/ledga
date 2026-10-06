package com.ledga.app.ui.rules

import com.ledga.app.data.edit.RulePreview
import com.ledga.app.data.edit.TransactionEdits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A rule count (R48) and the text it was worked out for; null [preview] for a name too short to use. */
data class ShownPreview(val name: String, val account: String, val preview: RulePreview?) {
    fun isFor(name: String, account: String) = this.name == name && this.account == account
}

/**
 * "+ Add rule" for one category (R48), shared by Tracker detail and a category's screen. The count is for exactly the
 * text typed (a newer text cancels an older count), and [save] refuses text it hasn't counted: the rule clears
 * hand-filed choices that removing it won't bring back.
 */
class RuleDraft(private val scope: CoroutineScope, private val edits: TransactionEdits, private val categoryKey: String) {
    private val text = MutableStateFlow<Pair<String, String>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val preview: StateFlow<ShownPreview?> = text
        .mapLatest { t -> t?.let { (name, account) -> ShownPreview(name, account, edits.rulePreview(categoryKey, name, account.ifBlank { null })) } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun count(name: String, account: String) {
        text.value = name to account
    }

    fun clear() {
        text.value = null
    }

    fun save(name: String, account: String, onDone: () -> Unit) {
        val shown = preview.value
        if (shown?.isFor(name, account) != true || shown.preview == null) return
        scope.launch { if (edits.addRule(categoryKey, name, account.ifBlank { null })) onDone() }
    }
}
