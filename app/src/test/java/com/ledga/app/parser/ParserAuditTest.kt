package com.ledga.app.parser

import com.ledga.app.data.parser.MpesaSmsParser
import com.ledga.app.data.parser.ParseResult
import com.ledga.app.data.parser.TransactionType
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Validates the parser against a real exported dataset.
 *
 * Reads from `~/Documents/ledga-export/data.json` if present; skipped in CI
 * and on any machine without the export so we don't commit real data.
 *
 * Goal: drive the unknown rate as close to zero as possible on the captured
 * 6,565-transaction history. The export was generated before parser fixes
 * landed — re-parsing it tests the current code path.
 */
class ParserAuditTest {

    private val exportPath = File(System.getProperty("user.home"), "Documents/ledga-export/data.json")

    @Test
    fun `current parser handles real exported SMS — unknowns under 50`() {
        assumeTrue("Skipping: export not present at ${exportPath.absolutePath}", exportPath.exists())

        val rawTexts = extractRawSms(exportPath)
        require(rawTexts.isNotEmpty()) { "Export contained no rawSms strings" }

        val outcomes = rawTexts.map { sms ->
            when (val result = MpesaSmsParser.parse(sms)) {
                is ParseResult.Success -> Outcome.Parsed(result.transaction.type, sms)
                is ParseResult.Failure -> Outcome.Filtered(result.reason, sms)
            }
        }

        val typeCounts = outcomes.filterIsInstance<Outcome.Parsed>()
            .groupingBy { it.type }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
        val unknowns = outcomes.count { it is Outcome.Parsed && it.type == TransactionType.UNKNOWN }
        val filtered = outcomes.count { it is Outcome.Filtered }

        // Print a summary that's useful when iterating on the parser.
        println("--- Parser audit ---")
        println("Total rawSms inputs : ${rawTexts.size}")
        println("Filtered (excluded) : $filtered")
        typeCounts.forEach { (type, count) -> println("  %-20s %d".format(type.name, count)) }
        println("UNKNOWN             : $unknowns")

        if (unknowns > 0) {
            println("\n--- Sample UNKNOWN strings (up to 10) ---")
            outcomes.asSequence()
                .filterIsInstance<Outcome.Parsed>()
                .filter { it.type == TransactionType.UNKNOWN }
                .take(10)
                .forEach { println("  ${it.sms.take(180)}") }
        }

        // Be strict: the parser has explicit handling for every observed
        // pattern in this dataset. >50 unknowns means a regression.
        assert(unknowns < 50) {
            "Expected < 50 UNKNOWN parses, got $unknowns. " +
                    "Investigate the printed samples above."
        }
    }

    /**
     * The export is a JSON object: `{ "transactions": [ { "rawSms": "...", ... }, ... ] }`.
     * We parse without bringing in a JSON dependency by extracting the rawSms
     * field with a deliberate regex. Good enough for fixture loading; not for
     * production code.
     */
    private fun extractRawSms(file: File): List<String> {
        val text = file.readText()
        val regex = Regex(""""rawSms"\s*:\s*"((?:\\.|[^"\\])*)"""")
        return regex.findAll(text)
            .map { it.groupValues[1].unescapeJson() }
            .toList()
    }

    private fun String.unescapeJson(): String =
        replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")

    private sealed class Outcome {
        data class Parsed(val type: TransactionType, val sms: String) : Outcome()
        data class Filtered(val reason: String, val sms: String) : Outcome()
    }
}
