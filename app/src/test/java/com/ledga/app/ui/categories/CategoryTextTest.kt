package com.ledga.app.ui.categories

import kotlin.test.assertEquals
import org.junit.Test

class CategoryTextTest {
    @Test
    fun `a category row counts its rules and says how many are off (R67)`() {
        assertEquals("No rules", CategoryText.rulesLine(0, 0))
        assertEquals("1 rule", CategoryText.rulesLine(1, 0))
        assertEquals("1 rule · 1 off", CategoryText.rulesLine(0, 1))
        assertEquals("3 rules · 1 off", CategoryText.rulesLine(2, 1))
    }

    @Test
    fun `the archive question says what stays and how to come back (R72)`() {
        assertEquals(
            "It leaves the category picker, the filters and Trackers. Its 12 payments keep it, and 2 rules still file new payments here. You can bring it back from Categories & rules.",
            CategoryText.archiveText(payments = 12, rules = 2),
        )
        assertEquals(
            "It leaves the category picker, the filters and Trackers. No payments use it. You can bring it back from Categories & rules.",
            CategoryText.archiveText(payments = 0, rules = 0),
        )
    }

    @Test
    fun `icons and swatches have names TalkBack can read`() {
        assertEquals("Hammer and wrench", CategoryText.iconName("fluent_hammer_and_wrench"))
        assertEquals("Label", CategoryText.iconName("fluent_label"))
        assertEquals("Sky", CategoryText.swatchName("#0277BD"))
        assertEquals("Default", CategoryText.swatchName(null))
    }
}
