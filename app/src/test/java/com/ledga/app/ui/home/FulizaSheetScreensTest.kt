package com.ledga.app.ui.home

import androidx.compose.runtime.remember
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.ledga.app.data.derive.FulizaStatus
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.testing.fulizaTxRow
import com.ledga.app.testing.snapScreen
import com.ledga.app.testing.txRow
import com.ledga.app.ui.design.components.SheetScaffold
import com.ledga.core.model.Categories
import com.ledga.core.model.TxKind
import com.ledga.core.money.Money
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Fuliza sheet (R58, not mocked): owed, with a draw, a repayment and last year's draw. Synthetic values. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class FulizaSheetScreensTest {
    private val complete = LoadStates(LoadState.NotLoading(false), LoadState.NotLoading(true), LoadState.NotLoading(true))
    private val categories = Categories.SEED.associate {
        it.key to CategoryRow(it.key, it.name, it.group, it.icon3d, null, null, it.tracked, it.sortOrder, CategoryOrigin.SYSTEM, false)
    }
    private val rows = listOf(
        fulizaTxRow(),
        txRow(
            code = "TJK4AB12RC", kind = TxKind.FULIZA_REPAY_AUTO, name = null, account = null, categoryKey = Categories.FULIZA,
            amountCents = 137_500, balanceCents = null, at = Instant.parse("2026-09-28T05:00:00Z"),
        ),
        txRow(
            code = "TJK4AB12RD", kind = TxKind.BUY_GOODS, name = "SAMPLE SUPERMARKET", account = null, categoryKey = Categories.GROCERIES,
            amountCents = 286_000, fulizaDrawnCents = 46_300, at = Instant.parse("2025-12-20T15:00:00Z"),
        ),
    )
    private val ui = FulizaSheetUi(
        loaded = true,
        status = FulizaStatus(Money(641_836), Money(991_836), Money(350_000), LocalDate.parse("2026-11-02")),
        categories = categories,
        today = LocalDate.parse("2026-10-06"),
    )

    @Test
    fun owed() = snapScreen("fuliza_sheet") {
        val items = remember { flowOf(PagingData.from(rows, complete)) }.collectAsLazyPagingItems()
        SheetScaffold(null) { FulizaSheetContent(ui, items, onOpenTx = {}) }
    }
}
