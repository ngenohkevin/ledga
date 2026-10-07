package com.ledga.app.ui.design.icons

import android.content.res.AssetManager
import com.ledga.app.ui.design.format.TextFold
import java.util.Locale

/** One icon of the Fluent Emoji 3D set: its key (`fluent_<name>`, as `categories.icon3d` stores it), the set's name, group and keywords. */
data class CatalogIcon(val key: String, val name: String, val group: String, val keywords: List<String>)

/**
 * The whole Fluent Emoji 3D set (Phase 4e, D5), listed in `assets/icons3d/index.tsv` by `tools/design/fetch_fluent.py
 * --catalog`. The 60 icons the app draws elsewhere are drawables (`FluentIcons`); the rest are `assets/icons3d/<name>.webp`.
 */
object IconCatalog {
    const val DIR = "icons3d"

    /** The set's own group order. */
    val GROUPS = listOf(
        "Smileys & Emotion", "People & Body", "Animals & Nature", "Food & Drink", "Travel & Places",
        "Activities", "Objects", "Symbols", "Flags",
    )

    @Volatile private var loaded: List<CatalogIcon>? = null

    /** Every icon, in group order then name, read once. Reads an asset: call it off the main thread. */
    fun all(assets: AssetManager): List<CatalogIcon> =
        loaded ?: synchronized(this) { loaded ?: parse(assets.open("$DIR/index.tsv").bufferedReader().use { it.readText() }).also { loaded = it } }

    /** `index.tsv`: a header line, then key, name, group and "|"-separated keywords, tab-separated. */
    fun parse(text: String): List<CatalogIcon> = text.lineSequence().drop(1).filter { it.isNotBlank() }.map { line ->
        val cells = line.split('\t')
        CatalogIcon(cells[0], cells[1], cells[2], cells.getOrNull(3).orEmpty().split('|').filter { it.isNotBlank() })
    }.toList()

    /** R98: icons whose name or a keyword contains [query], ignoring case, accents and extra spaces; every icon for a blank one. */
    fun search(icons: List<CatalogIcon>, query: String): List<CatalogIcon> {
        val q = TextFold.of(query)
        if (q.isEmpty()) return icons
        return icons.filter { icon -> TextFold.of(icon.name).contains(q) || icon.keywords.any { TextFold.of(it).contains(q) } }
    }

    /** Where a catalog key's WebP is. */
    fun assetPath(key: String): String = "$DIR/${key.removePrefix("fluent_")}.webp"

    /** "dog face" → "Dog face": TalkBack's and the picker's name for an icon. */
    fun displayName(icon: CatalogIcon): String = icon.name.replaceFirstChar { it.titlecase(Locale.ROOT) }
}
