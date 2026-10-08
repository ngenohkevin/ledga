package com.ledga.core.update

/** One section of release notes: its heading ("" for text before the first heading) and its items, as plain text. */
data class NotesSection(val title: String, val items: List<String>)

/**
 * Release notes as Ledga shows them (spec §13.2, R143):
 * - `#` to `######` lines start a section, and `-`, `*`, `+` or `1.` lines are its items;
 * - an indented line continues the item above it, and any other line is an item of its own;
 * - bold, italics, code and links become plain text, and `---` rules are ignored.
 * GitHub's generated "Full Changelog" line, the only text v1.x releases have, is dropped, and so is a section left with
 * no items.
 */
object ReleaseNotes {
    private val HEADING = Regex("""#{1,6}\s+(.*)""")
    private val ITEM = Regex("""(?:[-*+]|\d+[.)])\s+(.*)""")
    private val RULE = Regex("""[-*_]{3,}""")
    private val LINK = Regex("""\[([^\]]*)]\([^)]*\)""")
    private val BOLD = Regex("""\*\*(.+?)\*\*|__(.+?)__""")
    private val ITALIC = Regex("""(?<![*\w])\*(?!\s)([^*]+?)\*(?![*\w])""")
    private val CODE = Regex("""`([^`]+)`""")
    private const val CHANGELOG = "Full Changelog"

    fun parse(markdown: String): List<NotesSection> {
        val titles = mutableListOf<String>()
        val items = mutableListOf<MutableList<String>>()
        // The last line was an item, so an indented line under it continues it.
        var open = false

        fun add(text: String) {
            if (items.isEmpty()) {
                titles += ""
                items += mutableListOf<String>()
            }
            items.last() += text
        }

        for (line in markdown.lines()) {
            val text = line.trim()
            val heading = HEADING.matchEntire(text)
            val item = ITEM.matchEntire(text)
            when {
                text.isEmpty() || RULE.matches(text) -> open = false
                heading != null -> {
                    titles += plain(heading.groupValues[1])
                    items += mutableListOf<String>()
                    open = false
                }
                item != null -> {
                    add(plain(item.groupValues[1]))
                    open = true
                }
                open && line.first().isWhitespace() -> {
                    val last = items.last()
                    last[last.lastIndex] = last.last() + " " + plain(text)
                }
                else -> {
                    add(plain(text))
                    open = true
                }
            }
        }
        return titles.indices
            .map { i -> NotesSection(titles[i], items[i].filter { it.isNotBlank() && !it.startsWith(CHANGELOG, ignoreCase = true) }) }
            .filter { it.items.isNotEmpty() }
    }

    /** Inline markdown to plain text: a link keeps its words; bold, italics and code lose their marks. */
    fun plain(text: String): String = text
        .replace(LINK) { it.groupValues[1] }
        .replace(BOLD) { it.groupValues[1].ifEmpty { it.groupValues[2] } }
        .replace(CODE) { it.groupValues[1] }
        .replace(ITALIC) { it.groupValues[1] }
        .trim()
}
