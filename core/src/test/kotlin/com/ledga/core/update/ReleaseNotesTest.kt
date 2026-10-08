package com.ledga.core.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Spec §13.2, R143: release notes as Ledga shows them. Synthetic notes. */
class ReleaseNotesTest {
    @Test
    fun `headings start sections and bullets are their items, as plain text`() {
        val md = """
            ## What's new
            - A new **Home** with *your* balance
            - Tap a [category](https://example.test/c) to see its `payments`

            ## Fixes
            * Totals move on as messages arrive
            1. Numbered items count too
        """.trimIndent()
        assertEquals(
            listOf(
                NotesSection("What's new", listOf("A new Home with your balance", "Tap a category to see its payments")),
                NotesSection("Fixes", listOf("Totals move on as messages arrive", "Numbered items count too")),
            ),
            ReleaseNotes.parse(md),
        )
    }

    @Test
    fun `an indented line continues the item above it`() {
        assertEquals(
            listOf(NotesSection("Fixes", listOf("A long item that goes on"))),
            ReleaseNotes.parse("### Fixes\n- A long item\n  that goes on\n"),
        )
    }

    @Test
    fun `text before any heading has a section with no title, and other lines are items`() {
        assertEquals(
            listOf(NotesSection("", listOf("A first line", "A second line")), NotesSection("Fixes", listOf("One"))),
            ReleaseNotes.parse("A first line\nA second line\n\n## Fixes\n- One\n## Empty\n"),
        )
    }

    @Test
    fun `GitHub's generated changelog line is not a note`() {
        assertTrue(ReleaseNotes.parse("**Full Changelog**: https://github.com/example/app/compare/v1.0.0...v1.1.0").isEmpty())
    }

    @Test
    fun `Windows line endings and rules are read like the rest`() {
        assertEquals(
            listOf(NotesSection("What's new", listOf("One", "Two"))),
            ReleaseNotes.parse("## What's new\r\n- One\r\n---\r\n- Two\r\n"),
        )
    }
}
