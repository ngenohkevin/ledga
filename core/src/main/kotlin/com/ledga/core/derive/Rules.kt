package com.ledga.core.derive

import com.ledga.core.model.CategoryGroup
import com.ledga.core.model.FlowKind
import com.ledga.core.model.SystemRules
import com.ledga.core.parse.Counterparty
import com.ledga.core.parse.Phones
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Paybill/till numbers never appear in SMS, so they are deliberately not a match field. */
enum class RuleField { NAME_CONTAINS, ACCOUNT_EQUALS, PHONE_EQUALS }
enum class RuleAction { SET_CATEGORY, MARK_OWN_ACCOUNT }
enum class RuleOrigin { SYSTEM, USER }

data class Rule(
    val id: Long,
    val field: RuleField,
    val pattern: String,
    val action: RuleAction,
    val categoryKey: String?,
    val origin: RuleOrigin,
    val priority: Int = 0,
    val createdAt: Instant,
    val enabled: Boolean = true,
)

class RuleEngine(rules: List<Rule>, private val groupOf: (String) -> CategoryGroup?) {

    /** USER before SYSTEM; higher priority first; USER ties newest-first, SYSTEM ties by seed id. */
    val ordered: List<Rule> = rules.filter { it.enabled }.sortedWith(
        compareBy<Rule> { if (it.origin == RuleOrigin.USER) 0 else 1 }
            .thenByDescending { it.priority }
            .thenComparator { a, b ->
                if (a.origin == RuleOrigin.USER) {
                    compareValuesBy(b, a, { it.createdAt }, { it.id })
                } else {
                    a.id.compareTo(b.id)
                }
            },
    )

    private val namePatterns = ConcurrentHashMap<String, Regex>()

    fun matches(rule: Rule, cp: Counterparty?): Boolean {
        if (cp == null) return false
        if (rule.pattern.none { it.isLetterOrDigit() }) return false
        return when (rule.field) {
            RuleField.NAME_CONTAINS -> {
                val name = cp.name ?: return false
                namePatterns.getOrPut(rule.pattern) { wholeWords(rule.pattern) }.containsMatchIn(name)
            }
            RuleField.ACCOUNT_EQUALS -> {
                val account = cp.accountRef ?: return false
                collapse(account).equals(collapse(rule.pattern), ignoreCase = true)
            }
            RuleField.PHONE_EQUALS -> {
                val phone = cp.phone ?: return false
                if (rule.pattern.count { it.isDigit() } < 7) return false
                if ('*' !in rule.pattern && '*' !in phone) {
                    // Both sides are full numbers: compare them exactly, not by first-4/last-3.
                    Phones.local(phone) == Phones.local(rule.pattern)
                } else {
                    Phones.key(phone) == Phones.key(rule.pattern)
                }
            }
        }
    }

    fun isOwnAccount(cp: Counterparty?): Boolean =
        ordered.any { it.action == RuleAction.MARK_OWN_ACCOUNT && matches(it, cp) }

    /** Whether [categoryKey]'s group may label a transaction with [flow]. Unknown categories fit nothing. */
    fun fits(categoryKey: String, flow: FlowKind): Boolean = groupOf(categoryKey)?.accepts(flow) == true

    fun categoryFor(cp: Counterparty?, flow: FlowKind): String? = ordered.firstOrNull { rule ->
        rule.action == RuleAction.SET_CATEGORY &&
            rule.categoryKey != null &&
            fits(rule.categoryKey, flow) &&
            matches(rule, cp)
    }?.categoryKey

    companion object {
        private val WS = Regex("\\s+")
        private fun collapse(s: String) = s.replace(WS, " ").trim()

        /** Whole-word, case-insensitive containment of the pattern's word sequence. */
        private fun wholeWords(pattern: String): Regex {
            val words = collapse(pattern).split(" ").joinToString("\\s+") { Regex.escape(it) }
            return Regex("""(?<![A-Za-z0-9])$words(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE)
        }

        /** The seeded SYSTEM rules, ids 1..n in seed order (Phase 2 seeds the table with these). */
        fun systemRules(createdAt: Instant): List<Rule> = SystemRules.SEED.mapIndexed { i, seed ->
            Rule(
                id = i + 1L,
                field = RuleField.NAME_CONTAINS,
                pattern = seed.pattern,
                action = RuleAction.SET_CATEGORY,
                categoryKey = seed.categoryKey,
                origin = RuleOrigin.SYSTEM,
                createdAt = createdAt,
            )
        }
    }
}
