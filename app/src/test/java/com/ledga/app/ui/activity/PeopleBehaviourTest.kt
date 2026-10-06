package com.ledga.app.ui.activity

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.ledga.app.data.derive.PeopleDirection
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class PeopleBehaviourTest {
    @get:Rule val compose = createComposeRule()
    private val jane = PersonRowUi("JANE TESTER|0712111", "Jane Tester", "0712345111", 23, 1_265_000, Instant.parse("2026-10-02T15:00:00Z"))
    private val ui = PeopleUi(loaded = true, direction = PeopleDirection.SENT, maxCents = 1_265_000, rows = listOf(jane), today = LocalDate.parse("2026-10-06"))

    @Test
    fun `a person row opens their sheet`() {
        val opened = mutableListOf<PersonRowUi>()
        compose.setContent { LedgaTheme(Appearance.LIGHT, reducedMotion = true) { PeoplePane(ui, PeopleActions(onOpen = { opened += it })) } }
        compose.onNodeWithContentDescription("Jane Tester, 23 payments, Ksh 12,650 sent").performClick()
        assertEquals(listOf(jane), opened)
    }

    @Test
    fun `the minimum total is a labelled slider`() {
        compose.setContent { LedgaTheme(Appearance.LIGHT, reducedMotion = true) { PeoplePane(ui, PeopleActions()) } }
        compose.onNodeWithContentDescription("Minimum total").assertExists()
    }

    @Test
    fun `at 1_3x a long name ellipsizes while the total and the last-paid date stay whole`() {
        val long = PersonRowUi("A VERY LONG", "A Very Long Person Name That Keeps Going Onwards", null, 1, 123_456_789, Instant.parse("2025-12-24T09:00:00Z"))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                    PeoplePane(ui.copy(rows = listOf(long), maxCents = long.totalCents), PeopleActions())
                }
            }
        }
        for (text in listOf("Ksh 1,234,568", "last 24 Dec 2025")) {
            val node = compose.onNodeWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNode()
            val layout = mutableListOf<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.single()
            val needs = layout.multiParagraph.intrinsics.maxIntrinsicWidth
            assertTrue(needs <= layout.size.width + 0.5f, "'$text' is cut: it needs $needs px and has ${layout.size.width}")
        }
    }
}
