package com.ledga.app.data.legacy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.SmsStatus
import com.ledga.app.data.room.toDerived
import com.ledga.app.testing.UpgradeComparison
import com.ledga.app.testing.V1Export
import com.ledga.core.derive.BalanceChain
import com.ledga.core.model.TxKind
import java.io.File
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * OPT-IN, LOCAL ONLY (R160). v1.6's own Export runs through v2's real upgrade (MIGRATION_5_6, LegacyImporter, a
 * rebuild) and is compared with v1 payment by payment. Run it with `-Pledga.v1Export=<the zip>`; without that it is
 * skipped (as in CI). Prints counts, percentages and month labels only. The Ksh table goes to `<export>.rehearsal.txt`
 * beside the export, outside the repo, and is never printed.
 *
 * Its limits: the export carries no rules, custom category names, notes, car tags or lines, and there is no inbox to
 * rescan, so payments v1 dropped stay Fuliza-only here. The phone's own upgrade covers those.
 */
@RunWith(RobolectricTestRunner::class)
class V1UpgradeRehearsalTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `v1's export upgrades, compared with v1`() = runTest {
        val path = System.getProperty("ledga.v1Export").orEmpty()
        assumeTrue("no -Pledga.v1Export; rehearsal skipped", path.isNotEmpty())
        val file = File(path)
        check(file.isFile) { "-Pledga.v1Export names no file: $path" }

        val rows = V1Export.read(file)
        V1Export.writeSchema5(context, "rehearsal.db", rows)
        val db = LedgaDatabase.builder(context, "rehearsal.db").build()
        val report = LegacyImporter(db).run()
        Deriver(db).rebuildAll()
        val ledger = db.ledgerDao().all()
        val chain = BalanceChain.check(db.transactionsDao().all().map { it.toDerived() })
        val months = UpgradeComparison.months(rows, ledger)
        val categories = UpgradeComparison.categories(rows, ledger)

        println("== v1 upgrade rehearsal (counts, percentages and months only) ==")
        println(
            "v1 rows ${rows.size}; v2 transactions ${ledger.size}; Fuliza-only ${ledger.count { it.kind == TxKind.FULIZA_ONLY }}; " +
                "unreadable ${db.smsDao().countByStatus(SmsStatus.UNREADABLE)}",
        )
        println("importer: $report")
        println("history check: checked ${chain.checked}, gaps ${chain.gaps}, breaks ${chain.breaks.size}")
        println("categories as v1 filed them (payments, agree; elsewhere):")
        categories.forEach { c ->
            val elsewhere = c.elsewhere.entries.sortedByDescending { it.value }.take(4).joinToString("") { "; ${it.key} ${it.value}" }
            println("  ${c.v1Category}: ${c.total}, ${c.agree}$elsewhere")
        }
        println("spending by month, v2 without fees against v1 (both [same amount], v1 only, v2 only, difference):")
        months.forEach { m ->
            println("  ${m.month}: ${m.both} [${m.sameAmount}], v1 only ${m.v1Only}, v2 only ${m.v2Only}, ${UpgradeComparison.percent(m.v1Cents, m.v2SpendCents)}")
        }
        File(file.parentFile, file.nameWithoutExtension + ".rehearsal.txt").writeText(
            buildString {
                appendLine("month\tv1 Ksh\tv2 Ksh without fees\tv2 fees Ksh\tv2 Ksh with fees")
                months.forEach { m ->
                    appendLine("${m.month}\t${ksh(m.v1Cents)}\t${ksh(m.v2SpendCents)}\t${ksh(m.v2FeeCents)}\t${ksh(m.v2SpendCents + m.v2FeeCents)}")
                }
            },
        )

        assertEquals(rows.size, report.smsRehashed + report.smsDuplicatesDropped)
        assertFalse(LegacyImporter(db).isPending())
        db.close()
    }

    private fun ksh(cents: Long): String = "%,.2f".format(Locale.ROOT, cents / 100.0)
}
