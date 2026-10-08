package com.ledga.app.data.legacy

import com.ledga.app.testing.V1Export
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** R160: reading v1.6's Export. Synthetic rows only. */
class V1ExportTest {
    @get:Rule val tmp = TemporaryFolder()

    private val json =
        """{"version":1,"exportedAt":0,"transactions":[{"transactionCode":"TJK4AB12HA","type":"SEND","amount":2500.0,""" +
            """"transactionCost":0.0,"recipientName":"SAMPLE PERSON","recipientPhone":null,"accountNumber":null,""" +
            """"destinationCountry":null,"balance":0.0,"direction":"OUTFLOW","categoryId":6,"fulizaAmount":null,""" +
            """"fulizaOutstanding":null,"reversedTransactionCode":null,"rawSms":"synthetic","timestamp":1790000000000}]}"""

    private fun zip(name: String, vararg entries: Pair<String, String>) = tmp.newFile(name).also { f ->
        ZipOutputStream(f.outputStream()).use { z ->
            entries.forEach { (entry, text) ->
                z.putNextEntry(ZipEntry(entry))
                z.write(text.toByteArray())
                z.closeEntry()
            }
        }
    }

    @Test
    fun `an export zip's data json is read, as is the file alone`() {
        val fromZip = V1Export.read(zip("export.zip", "transactions.csv" to "code\n", "data.json" to json))
        assertEquals(listOf("TJK4AB12HA"), fromZip.map { it.code })
        assertEquals(6L, fromZip.single().categoryId)
        assertEquals(2500.0, fromZip.single().amount)
        assertEquals(fromZip, V1Export.read(tmp.newFile("data.json").apply { writeText(json) }))
    }

    @Test
    fun `a zip without data json says so, and a broken export never shows what it holds`() {
        val missing = assertFailsWith<IllegalStateException> { V1Export.read(zip("other.zip", "notes.txt" to "SAMPLE PERSON")) }
        assertEquals("the zip has no data.json", missing.message)
        val broken = tmp.newFile("data.json").apply { writeText("""{"transactions":[{"transactionCode":"TJK4AB12HA"}]}""") }
        val e = assertFailsWith<IllegalStateException> { V1Export.read(broken) }
        assertTrue("TJK4AB12HA" !in e.message.orEmpty())
    }
}
