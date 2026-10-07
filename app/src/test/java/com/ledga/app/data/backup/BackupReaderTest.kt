package com.ledga.app.data.backup

import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.OverrideRow
import com.ledga.app.data.room.RuleRow
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.PERSONAL
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
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
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R118: a backup holds every message and every choice the person made, and only the settings that travel (R114). */
@RunWith(RobolectricTestRunner::class)
class BackupReaderTest {
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-10-07T17:00:00Z"))
    private val prefs = FakePrefsStore()
    private val settings = SettingsStore(prefs)
    private val reader = BackupReader(db, settings, DeviceId { "fingerprint-a" }, "2.0.0-test", clock)

    @After fun close() = db.close()

    private suspend fun seed() {
        twoLines(db)
        SmsIngestor(db, Deriver(db, clock)).ingestAll(
            listOf(
                RawSms("MPESA", Sms.SEND, Sms.at("2026-03-21T10:30:00Z"), 1, PERSONAL.id, SmsSource.INBOX),
                RawSms("MPESA", Sms.KPLC, Sms.at("2026-03-21T12:00:00Z"), null, null, SmsSource.RECEIVER),
            ),
        )
        db.overridesDao().upsert(OverrideRow("TJK4AB12FB", null, "Rent share", null, null, hidden = false, updatedAt = clock.instant()))
        db.rulesDao().insert(RuleRow(field = RuleField.NAME_CONTAINS, pattern = "CORNER SHOP", action = RuleAction.SET_CATEGORY, categoryKey = Categories.GROCERIES, origin = RuleOrigin.USER, priority = 0, createdAt = clock.instant()))
        db.rulesDao().setEnabled(1, false) // the first built-in rule, switched off
        db.categoriesDao().insertIgnore(CategoryRow("user_pets", "Pets", CategoryGroup.EVERYDAY, "fluent_dog_face", null, null, tracked = true, sortOrder = 1000, origin = CategoryOrigin.USER, archived = false))
        db.categoriesDao().setTracked(Categories.WATER, false)
        settings.setOnboarded()
        settings.advanceWatermark(1_700_000_000_000)
        settings.setAppearance(Appearance.DARK)
        settings.setDisplayName("Amani")
    }

    @Test
    fun `a backup holds every message, choice and line`() = runTest {
        seed()
        val data = reader.read()
        assertEquals(BackupData.FORMAT, data.format)
        assertEquals(clock.instant().toEpochMilli(), data.writtenAt)
        assertEquals("2.0.0-test", data.appVersion)
        assertEquals("fingerprint-a", data.device)
        assertEquals(BackupCounts(sms = 2, payments = 2), data.counts)
        assertEquals(listOf(Sms.SEND, Sms.KPLC), data.sms.map { it.body })
        assertEquals(SmsEntry("MPESA", Sms.SEND, Sms.at("2026-03-21T10:30:00Z").toEpochMilli(), 1, PERSONAL.id, "INBOX"), data.sms.first())
        assertEquals(listOf("Rent share"), data.overrides.map { it.note })
        assertEquals(listOf("CORNER SHOP"), data.rules.map { it.pattern }, "the person's rules only")
        val firstBuiltIn = db.rulesDao().get(1)!!
        assertEquals(listOf(RuleKey(firstBuiltIn.field.name, firstBuiltIn.pattern, firstBuiltIn.action.name, firstBuiltIn.categoryKey)), data.systemRulesOff)
        assertEquals(setOf("user_pets", Categories.WATER), data.categories.map { it.key }.toSet(), "the person's categories and the built-in one they changed")
        assertEquals(listOf(1L, 2L), data.lines.map { it.id })
    }

    @Test
    fun `only the settings that belong to the person travel`() = runTest {
        seed()
        val data = reader.read()
        assertEquals("DARK", data.settings?.appearance)
        assertEquals("Amani", data.settings?.displayName)
        val json = BackupJson.json.encodeToString(BackupData.serializer(), data)
        for (perPhone in listOf("onboarded", "watermark", "rescan", "selected", "nudge")) {
            assertFalse(json.contains(perPhone, ignoreCase = true), "$perPhone stays on the phone (R114)")
        }
    }

    @Test
    fun `an unchanged built-in category is not in the backup`() = runTest {
        val electricity = db.categoriesDao().get(Categories.ELECTRICITY)!!
        assertFalse(BackupReader.changedFromSeed(electricity))
        assertTrue(BackupReader.changedFromSeed(electricity.copy(color = "#123456", colorDark = "#654321")))
        assertTrue(BackupReader.changedFromSeed(electricity.copy(tracked = false)))
        assertTrue(BackupReader.changedFromSeed(electricity.copy(name = "Power")))
    }
}
