package com.ledga.app.ui.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.ledga.app.data.derive.FulizaStatus
import com.ledga.app.testing.txRow
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.core.model.Categories
import com.ledga.core.model.TxKind
import com.ledga.core.money.Money
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class FulizaSheetBehaviourTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `at 1_3x a draw's amount stays whole beside a long name and last year's date`() {
        val old = txRow(
            code = "TJK4AB12RE", kind = TxKind.BUY_GOODS, name = "SAMPLE SUPERMARKET WITH A LONG NAME", account = null,
            categoryKey = Categories.GROCERIES, amountCents = 286_000, fulizaDrawnCents = 46_300, at = Instant.parse("2025-12-20T15:00:00Z"),
        )
        val complete = LoadStates(LoadState.NotLoading(false), LoadState.NotLoading(true), LoadState.NotLoading(true))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                    val items = remember { flowOf(PagingData.from(listOf(old), complete)) }.collectAsLazyPagingItems()
                    FulizaSheetContent(
                        FulizaSheetUi(loaded = true, status = FulizaStatus(Money(46_300), null, null, null), today = LocalDate.parse("2026-10-06")),
                        items,
                        onOpenTx = {},
                    )
                }
            }
        }
        for (text in listOf("Fuliza Ksh 463", "20 Dec 2025")) {
            val node = compose.onNodeWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNode()
            val layout = mutableListOf<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.single()
            val needs = layout.multiParagraph.intrinsics.maxIntrinsicWidth
            assertTrue(needs <= layout.size.width + 0.5f, "'$text' is cut: it needs $needs px and has ${layout.size.width}")
        }
    }
}
