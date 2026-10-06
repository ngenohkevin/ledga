package com.ledga.app.ui.categories

import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.room.RuleRow
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.TestViewModels
import com.ledga.core.derive.RuleAction
import com.ledga.core.derive.RuleField
import com.ledga.core.derive.RuleOrigin
import com.ledga.core.model.Categories
import com.ledga.core.model.CategoryGroup
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R67, R72: every category by group, with its rules counted. */
@RunWith(RobolectricTestRunner::class)
class CategoriesViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-10-06T06:00:00Z"))
    private val edits = TransactionEdits(db, Deriver(db, clock), clock)
    private val vms = TestViewModels()

    private fun vm() = vms.track(CategoriesViewModel(db, edits))

    @After fun close() {
        vms.stopAll()
        db.close()
    }

    @Test
    fun `groups in picker order, each category with its rules on and off, and own-account rules under Own accounts`() = runTest {
        val kplc = db.rulesDao().all().first { it.origin == RuleOrigin.SYSTEM && it.pattern.equals("KPLC", ignoreCase = true) }
        edits.setRuleEnabled(kplc.id, false)
        db.rulesDao().insert(RuleRow(field = RuleField.NAME_CONTAINS, pattern = "EXAMPLE BANK", action = RuleAction.MARK_OWN_ACCOUNT, categoryKey = null, origin = RuleOrigin.USER, priority = 0, createdAt = clock.instant()))
        val ui = vm().ui.first { it.loaded }
        assertEquals(CategoryGroup.entries.toList(), ui.groups.map { it.group })
        val electricity = ui.groups.flatMap { it.rows }.single { it.category.key == Categories.ELECTRICITY }
        val electricityRules = db.rulesDao().all().count { it.categoryKey == Categories.ELECTRICITY }
        assertEquals(electricityRules - 1 to 1, electricity.rulesOn to electricity.rulesOff)
        val own = ui.groups.flatMap { it.rows }.single { it.category.key == Categories.OWN_ACCOUNTS }
        assertTrue(own.rulesOn >= 1, "the own-account rule counts under Own accounts")
    }

    @Test
    fun `an archived category leaves its group for Archived (R72)`() = runTest {
        val wedding = edits.createCategory("Wedding", CategoryGroup.EVERYDAY)
        edits.setArchived(wedding, true)
        val ui = vm().ui.first { it.loaded && it.archived.isNotEmpty() }
        assertEquals(listOf(wedding), ui.archived.map { it.category.key })
        assertTrue(ui.groups.flatMap { it.rows }.none { it.category.key == wedding })
    }

    @Test
    fun `a new category opens once made, and a blank name makes nothing`() = runTest {
        val vm = vm()
        vm.create("   ", CategoryGroup.EVERYDAY) { error("nothing to open") }
        val made = CompletableDeferred<String>()
        vm.create("Chama", CategoryGroup.EVERYDAY) { made.complete(it) }
        assertEquals("user_chama", made.await())
    }
}
