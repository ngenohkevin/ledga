package com.ledga.app.ui.trackers

import com.ledga.core.model.TxKind
import com.ledga.core.derive.RuleOrigin
import com.ledga.core.derive.RuleField
import com.ledga.core.derive.RuleEngine
import com.ledga.core.derive.RuleAction
import com.ledga.core.chart.Bucketing
import com.ledga.app.testing.txRow
import com.ledga.app.data.room.RuleRow
import com.ledga.app.data.edit.RulePreview
import java.time.LocalDate
import com.ledga.app.data.room.dao.CategorySpend
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.trackers.TrackerSummary
import com.ledga.core.chart.Bucket
import com.ledga.core.model.CategoryGroup
import com.ledga.core.money.Money
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.Test

/** What a tracker says (R52). Synthetic amounts. */
class TrackerTextTest {
    private val now = Instant.parse("2026-10-06T06:00:00Z")
    private val water = CategoryRow("water", "Water", CategoryGroup.BILLS_UTILITIES, "fluent_droplet", null, null, true, 1, CategoryOrigin.SYSTEM, false)

    private fun summary(thisMonth: Long, average: Long?, usual: Int?): TrackerSummary {
        val months = Periods.lastN(PeriodType.MONTH, now, 13).mapIndexed { i, p ->
            val cents = if (i == 12) thisMonth else 61_000L
            Bucket(p, Money(cents), if (cents > 0) 1 else 0)
        }
        return TrackerSummary(water, months, average, usual, null)
    }

    @Test
    fun `a tile says when a monthly bill is usually paid until it is, then the average`() {
        assertEquals("usually by the 12th", TrackerText.tileCaption(summary(0, 61_000, 12)))
        assertEquals("avg 610/mo", TrackerText.tileCaption(summary(64_000, 61_000, 12)))
        assertEquals("this month", TrackerText.tileCaption(summary(0, null, null)))
    }

    @Test
    fun `ordinals read as people say them`() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "23rd", "31st"), listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 31).map(TrackerText::ordinal))
    }

    @Test
    fun `a row says who and how many this month, or when it's usually paid, or when it was last paid`() {
        val today = LocalDate.parse("2026-10-06")
        val paid = CategorySpend("TJK4AB12WA", "water", Instant.parse("2026-10-02T07:00:00Z"), 64_000, "SAMPLE WATER CO")
        assertEquals("Sample Water Co · 1 payment this month", TrackerText.rowContext(summary(64_000, 61_000, 12).copy(last = paid), today))
        assertEquals("usually by the 12th", TrackerText.rowContext(summary(0, 61_000, 12), today))
        val old = paid.copy(occurredAt = Instant.parse("2025-07-12T07:00:00Z"), cents = 520_000)
        assertEquals("Last: Jul 2025 · Ksh 5,200", TrackerText.rowContext(summary(0, null, null).copy(last = old), today))
        assertEquals("No payments yet", TrackerText.rowContext(summary(0, null, null), today))
    }

    @Test
    fun `a row's detail is last month while this month is unpaid, else the average`() {
        assertEquals("Sep 610", TrackerText.rowDetail(summary(0, 61_000, 12)))
        assertEquals("avg 610", TrackerText.rowDetail(summary(64_000, 61_000, 12)))
        val empty = summary(0, null, null).let { s -> s.copy(months = s.months.map { it.copy(total = Money.ZERO, count = 0) }) }
        assertEquals("this month", TrackerText.rowDetail(empty))
    }

    @Test
    fun `rule chips say what they match`() {
        fun rule(field: RuleField, pattern: String) =
            RuleRow(field = field, pattern = pattern, action = RuleAction.SET_CATEGORY, categoryKey = "electricity", origin = RuleOrigin.USER, priority = 0, createdAt = now)
        assertEquals("Name has KPLC", TrackerText.ruleLabel(rule(RuleField.NAME_CONTAINS, "KPLC")))
        assertEquals("Name has Sample Water", TrackerText.ruleLabel(rule(RuleField.NAME_CONTAINS, "SAMPLE WATER")))
        assertEquals("KPLC Prepaid · account 37100000001", TrackerText.ruleLabel(rule(RuleField.NAME_AND_ACCOUNT, RuleEngine.nameAndAccount("KPLC PREPAID", "37100000001"))))
        assertEquals("Account 37100000001", TrackerText.ruleLabel(rule(RuleField.ACCOUNT_EQUALS, "37100000001")))
    }

    @Test
    fun `a tooltip names the period, says so far for the running one, and counts the payments`() {
        val months = Periods.lastN(PeriodType.MONTH, now, 2)
        assertEquals("Ksh 1,850 · Sep" to "2 payments", TrackerText.tooltip(Bucket(months[0], Money(185_000), 2), running = false))
        assertEquals("Ksh 900 · Oct so far" to "1 payment", TrackerText.tooltip(Bucket(months[1], Money(90_000), 1), running = true))
        assertEquals("Ksh 1,850 · 2026" to "2 payments", TrackerText.tooltip(Bucketing.byYear(listOf(Bucket(months[0], Money(185_000), 2))).single(), running = false))
    }

    @Test
    fun `a tracker's payment row leads with the day, then the account or the time`() {
        val today = LocalDate.parse("2026-10-06")
        assertEquals("Today · Acc 37100000001" to null, TrackerText.paymentLine(txRow(at = Instant.parse("2026-10-06T05:40:00Z")), today))
        assertEquals("28 Sep" to "8:15 AM", TrackerText.paymentLine(txRow(kind = TxKind.BUY_GOODS, account = null, at = Instant.parse("2026-09-28T05:15:00Z")), today))
    }

    @Test
    fun `the add-rule preview says what will move before Save`() {
        assertEquals("Type part of a name, like KPLC.", TrackerText.rulePreview(null, "", "Water"))
        assertEquals("Type at least two letters or numbers.", TrackerText.rulePreview(null, "x", "Water"))
        assertEquals("No payments match yet. Future ones will go to Water.", TrackerText.rulePreview(RulePreview(0, 0, 0), "sample", "Water"))
        assertEquals("Matches 3 payments, all already in Water.", TrackerText.rulePreview(RulePreview(3, 0, 0), "sample", "Water"))
        assertEquals("Matches 3 payments. 3 move to Water.", TrackerText.rulePreview(RulePreview(3, 3, 0), "sample", "Water"))
        assertEquals("Matches 3 payments. 3 move to Water, 1 of them filed elsewhere by you.", TrackerText.rulePreview(RulePreview(3, 3, 1), "sample", "Water"))
        assertEquals(
            "Matches 3 payments. 3 move to Water. Replaces your rule for Rent.",
            TrackerText.rulePreview(RulePreview(3, 3, 0, replaces = "Rent"), "sample", "Water"),
        )
    }
}
