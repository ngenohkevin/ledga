package com.ledga.app.data.lines

import com.ledga.app.testing.PERSONAL
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.selectedLine
import com.ledga.app.testing.twoLines
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R47: one line choice for every summary, and never a hidden filter (Review Focus #1). */
@RunWith(RobolectricTestRunner::class)
class SelectedLineTest {
    private val db = TestDb.inMemory()
    private val line = selectedLine(db)

    @After fun close() = db.close()

    @Test
    fun `on a phone with one line a stored choice is ignored, so nothing is hidden`() = runTest {
        db.linesDao().insert(PERSONAL)
        line.select(PERSONAL.id) // e.g. v1's "selected line", carried over
        val choice = line.choice.first { it.lines.size == 1 && it.selectedId == PERSONAL.id }
        assertNull(choice.lineId, "a one-line phone shows every payment, attributed or not")
        assertFalse(choice.showChip)
    }

    @Test
    fun `with two lines the choice narrows, and All lines clears it`() = runTest {
        twoLines(db)
        line.select(2)
        val business = line.choice.first { it.lineId == 2L }
        assertEquals("Business", business.selected?.displayName)
        assertTrue(business.showChip)
        line.select(null)
        assertNull(line.choice.first { it.selectedId == null }.lineId)
    }

    @Test
    fun `a choice of a line that no longer exists reads as all lines`() = runTest {
        twoLines(db)
        line.select(9)
        assertNull(line.choice.first { it.selectedId == 9L }.lineId)
    }
}
