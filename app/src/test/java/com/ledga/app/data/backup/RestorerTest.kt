package com.ledga.app.data.backup

import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.lines.Sim
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.LineRow
import com.ledga.app.data.room.OverrideRow
import com.ledga.app.data.room.RuleRow
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.testing.BUSINESS
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.FakeSims
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.PERSONAL
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.testSnapshots
import com.ledga.app.testing.twoLines
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.core.derive.RuleAction
import com.ledga.core.derive.RuleField
import com.ledga.core.derive.RuleOrigin
import com.ledga.core.model.Categories
import com.ledga.core.model.CategoryGroup
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec §12.3, R121–R123: what a restore brings, what it keeps, and what it never does. Synthetic data only. */
@RunWith(RobolectricTestRunner::class)
class RestorerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val clock = MutableClock(Instant.parse("2026-10-07T17:00:00Z"))
    private val db = TestDb.inMemory()
    private val source = TestDb.inMemory()
    private val prefs = FakePrefsStore()
    private val settings = SettingsStore(prefs)
    private val sims = FakeSims()
    private val deriver = Deriver(db, clock)
    private val snapshots by lazy { testSnapshots(db, tmp.newFolder("backup"), settings, clock) }
    private val restorer by lazy { Restorer(db, deriver, settings, snapshots, sims, DeviceId { "this-phone" }, clock) }

    @After fun close() {
        db.close()
        source.close()
    }

    private suspend fun ingest(target: LedgaDatabase, body: String, at: String, line: Long?, sub: Int? = null) {
        SmsIngestor(target, Deriver(target, clock)).ingest(RawSms("MPESA", body, Sms.at(at), sub, line, SmsSource.INBOX))
    }

    /** Another phone's history: two lines, a note, a rule, a built-in rule off, a category, Water untracked, settings. */
    private suspend fun backupFromAnotherPhone(device: String = "other-phone"): Incoming {
        twoLines(source)
        ingest(source, Sms.SEND, "2026-03-21T10:30:00Z", PERSONAL.id, sub = 1)
        ingest(source, Sms.KPLC, "2026-03-21T12:00:00Z", BUSINESS.id, sub = 2)
        source.overridesDao().upsert(OverrideRow("TJK4AB12FB", null, "Rent share", null, null, hidden = false, updatedAt = clock.instant()))
        source.rulesDao().insert(RuleRow(field = RuleField.NAME_CONTAINS, pattern = "CORNER SHOP", action = RuleAction.SET_CATEGORY, categoryKey = Categories.GROCERIES, origin = RuleOrigin.USER, priority = 0, createdAt = clock.instant()))
        source.rulesDao().setEnabled(1, false)
        source.categoriesDao().insertIgnore(CategoryRow("user_pets", "Pets", CategoryGroup.EVERYDAY, "fluent_dog_face", null, null, tracked = false, sortOrder = 1000, origin = CategoryOrigin.USER, archived = false))
        source.categoriesDao().setTracked(Categories.WATER, false)
        val sourceSettings = SettingsStore(FakePrefsStore()).apply {
            setAppearance(Appearance.DARK)
            setDisplayName("Amani")
        }
        val data = BackupReader(source, sourceSettings, DeviceId { device }, "2.0.0-test", clock).read()
        return Incoming(BackupOrigin.LEDGA, data)
    }

    @Test
    fun `a restore onto a new phone brings everything back, its lines on this phone's SIMs`() = runTest {
        val incoming = backupFromAnotherPhone()
        sims.add(Sim(5, "SIM 1", "+254712345111")) // Personal's number
        sims.add(Sim(6, "eSIM 1", null))
        val plan = restorer.plan(incoming, RestoreMode.MERGE)
        assertEquals(listOf(BUSINESS.id), plan.questions.map { it.line.id }, "Business's number can't be read here: asked once")
        val report = restorer.restore(incoming, RestoreMode.MERGE, answers = mapOf(BUSINESS.id to 6), applySettings = true)
        assertEquals(RestoreReport(messagesAdded = 2, paymentsBefore = 0, paymentsAfter = 2), report)
        val lines = db.linesDao().all().associateBy { it.subscriptionId }
        assertEquals("Personal", lines[5]?.displayName)
        assertEquals("Business", lines[6]?.displayName)
        assertEquals(lines[5]?.id, db.transactionsDao().get("TJK4AB12FB")?.lineId)
        assertEquals(lines[6]?.id, db.transactionsDao().get("TJK4AB12FA")?.lineId)
        assertEquals("Rent share", db.transactionsDao().get("TJK4AB12FB")?.note)
        assertEquals(listOf("CORNER SHOP"), db.rulesDao().all().filter { it.origin == RuleOrigin.USER }.map { it.pattern })
        assertFalse(db.rulesDao().get(1)!!.enabled)
        assertEquals("Pets", db.categoriesDao().get("user_pets")?.name)
        assertFalse(db.categoriesDao().get(Categories.WATER)!!.tracked)
        val s = settings.current()
        assertEquals(Appearance.DARK, s.appearance)
        assertEquals("Amani", s.displayName)
        assertFalse(s.onboarded, "per-phone state never comes from a backup (R114)")
        assertEquals(listOf<Int?>(null, null), db.smsDao().pageAfter(0, 10).map { it.subscriptionId }, "another phone's SIM ids mean nothing here")
        assertFalse(deriver.needsRebuild())
    }

    @Test
    fun `on the same phone each line keeps its SIM and nothing is asked`() = runTest {
        val incoming = backupFromAnotherPhone(device = "this-phone")
        assertTrue(restorer.sameDevice(incoming))
        assertEquals(emptyList(), restorer.plan(incoming, RestoreMode.MERGE).questions)
        restorer.restore(incoming, RestoreMode.MERGE, emptyMap(), applySettings = false)
        assertEquals(setOf(1, 2), db.linesDao().all().mapNotNull { it.subscriptionId }.toSet())
        assertEquals(setOf<Int?>(1, 2), db.smsDao().pageAfter(0, 10).map { it.subscriptionId }.toSet())
        assertEquals(Appearance.SYSTEM, settings.current().appearance, "settings only when asked")
    }

    @Test
    fun `a merge keeps this phone's choices and fills only what it left empty`() = runTest {
        val incoming = backupFromAnotherPhone()
        ingest(db, Sms.SEND, "2026-03-21T10:30:00Z", null)
        db.overridesDao().upsert(OverrideRow("TJK4AB12FB", Categories.GROCERIES, null, null, null, hidden = false, updatedAt = clock.instant()))
        db.rulesDao().insert(RuleRow(field = RuleField.NAME_CONTAINS, pattern = "Corner Shop", action = RuleAction.SET_CATEGORY, categoryKey = Categories.FOOD, origin = RuleOrigin.USER, priority = 0, createdAt = clock.instant()))
        db.categoriesDao().setTracked(Categories.WATER, true) // the built-in default: the backup's "untracked" fills it
        restorer.restore(incoming, RestoreMode.MERGE, emptyMap(), applySettings = false)
        val tx = db.transactionsDao().get("TJK4AB12FB")!!
        assertEquals(Categories.GROCERIES, db.overridesDao().get("TJK4AB12FB")?.categoryKey, "this phone's category stays")
        assertEquals("Rent share", tx.note, "the backup's note fills the empty one")
        assertEquals(listOf(Categories.FOOD), db.rulesDao().all().filter { it.origin == RuleOrigin.USER }.map { it.categoryKey }, "this phone's rule for the same words wins")
        assertFalse(db.categoriesDao().get(Categories.WATER)!!.tracked)
    }

    @Test
    fun `the same message twice is stored once and takes the backup's line only where it had none`() = runTest {
        val incoming = backupFromAnotherPhone(device = "this-phone")
        val here = db.linesDao().insert(LineRow(subscriptionId = 9, phoneNumber = null, displayName = "Line 1", color = "#0E9F6E", isPrimary = true, createdAt = Instant.EPOCH))
        ingest(db, Sms.SEND, "2026-03-21T10:30:00Z", here)
        ingest(db, Sms.KPLC, "2026-03-21T12:00:00Z", null)
        val report = restorer.restore(incoming, RestoreMode.MERGE, emptyMap(), applySettings = false)
        assertEquals(0, report.messagesAdded)
        assertEquals(2, db.smsDao().count())
        assertEquals(here, db.transactionsDao().get("TJK4AB12FB")?.lineId, "a message already on a line stays there")
        assertEquals(db.linesDao().bySubscription(2)?.id, db.transactionsDao().get("TJK4AB12FA")?.lineId)
    }

    @Test
    fun `replace starts again from the backup alone, after keeping this phone's copy`() = runTest {
        val incoming = backupFromAnotherPhone()
        settings.setOnboarded()
        ingest(db, Sms.BANK_APP, "2026-04-02T06:15:00Z", null)
        db.rulesDao().insert(RuleRow(field = RuleField.NAME_CONTAINS, pattern = "GREEN GROCER", action = RuleAction.SET_CATEGORY, categoryKey = Categories.GROCERIES, origin = RuleOrigin.USER, priority = 0, createdAt = clock.instant()))
        db.categoriesDao().rename(Categories.FUEL, "Petrol")
        restorer.restore(incoming, RestoreMode.REPLACE, emptyMap(), applySettings = true)
        assertNull(db.transactionsDao().get("TJK4AB12FC"), "this phone's own payment is gone")
        assertEquals(setOf("TJK4AB12FB", "TJK4AB12FA"), db.transactionsDao().all().map { it.code }.toSet())
        assertEquals(listOf("CORNER SHOP"), db.rulesDao().all().filter { it.origin == RuleOrigin.USER }.map { it.pattern })
        assertEquals("Fuel", db.categoriesDao().get(Categories.FUEL)?.name, "built-ins are reset before the backup's choices")
        val before = SnapshotStore(tmp.root.resolve("backup")).let { it.read(it.beforeRestore) }
        assertEquals(listOf(Sms.BANK_APP), before?.sms?.map { it.body }, "Replace is undone by restoring this copy")
        assertEquals(Appearance.DARK, settings.current().appearance)
    }

    @Test
    fun `a v1 export brings its messages and v1's own category choices`() = runTest {
        val v1 = Incoming(
            BackupOrigin.V1,
            BackupData(
                writtenAt = 1,
                appVersion = "1.x",
                counts = BackupCounts(2, 2),
                sms = listOf(
                    SmsEntry("MPESA", Sms.SEND, Sms.at("2026-03-21T10:30:00Z").toEpochMilli(), source = "IMPORT"),
                    SmsEntry("MPESA", Sms.KPLC, Sms.at("2026-03-21T12:00:00Z").toEpochMilli(), source = "IMPORT"),
                ),
            ),
            listOf(V1Choice("TJK4AB12FB", 1, "SEND", "JANE TESTER", null), V1Choice("TJK4AB12FA", null, "PAYBILL", "KPLC PREPAID", "37100000001")),
        )
        restorer.restore(v1, RestoreMode.MERGE, emptyMap(), applySettings = true)
        assertEquals(Categories.GROCERIES, db.transactionsDao().get("TJK4AB12FB")?.categoryKey)
        assertNull(db.overridesDao().get("TJK4AB12FA"), "v1 didn't choose: no override")
        assertEquals(emptyList(), db.linesDao().all())
        assertEquals(Appearance.SYSTEM, settings.current().appearance, "a v1 export carries no settings")
    }

    @Test
    fun `a restore stopped after its write still rebuilds at the next start`() = runTest {
        restorer.write(backupFromAnotherPhone(), RestoreMode.MERGE, emptyMap())
        assertTrue(deriver.needsRebuild(), "Startup queues the rebuild (R122)")
    }

    @Test
    fun `a restore never alerts`() = runTest {
        restorer.restore(backupFromAnotherPhone(), RestoreMode.MERGE, emptyMap(), applySettings = false)
        assertEquals(emptyList(), db.alertsDao().observeWithTx().first())
    }
}
