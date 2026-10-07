package com.ledga.app.data.backup

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import com.ledga.app.R
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

/** R114 (owner call A): Android copies only `files/backup/`, to the cloud and to a new phone. */
@RunWith(RobolectricTestRunner::class)
class BackupRulesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** "section tag domain path" for every include/exclude in [res]. */
    private fun rules(res: Int): List<String> {
        val p: XmlResourceParser = context.resources.getXml(res)
        val out = mutableListOf<String>()
        var section = ""
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "cloud-backup", "device-transfer" -> section = p.name + " "
                "include", "exclude" -> out += section + p.name + " " + p.getAttributeValue(null, "domain") + " " + p.getAttributeValue(null, "path")
            }
        }
        return out
    }

    @Test
    fun `Android backs up only the snapshot folder, before Android 12 and after`() {
        assertEquals(listOf("include file backup/"), rules(R.xml.backup_rules))
        assertEquals(listOf("cloud-backup include file backup/", "device-transfer include file backup/"), rules(R.xml.data_extraction_rules))
    }

    @Test
    fun `the manifest points Android at both rule files`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(manifest.contains("android:allowBackup=\"true\""))
    }
}
