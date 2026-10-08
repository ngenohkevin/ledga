package com.ledga.app.data.update

import com.ledga.core.update.AppVersion
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** R139: downloaded updates. */
class UpdateFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val older = AppVersion.parse("2.0.1")!!
    private val newer = AppVersion.parse("2.0.2")!!
    private val files by lazy { UpdateFiles(tmp.newFolder("updates")) }

    @Test
    fun `a partial download is never ready`() {
        files.partial(older).writeText("half")
        assertNull(files.ready(older))
        files.apk(older).writeBytes(ByteArray(0))
        assertNull(files.ready(older))
        files.apk(older).writeText("apk")
        assertEquals(files.apk(older), files.ready(older))
    }

    @Test
    fun `keeping one version deletes every other file`() {
        files.apk(older).writeText("old")
        files.partial(older).writeText("old half")
        files.apk(newer).writeText("new")
        files.partial(newer).writeText("new half")
        File(files.dir(), "stray.tmp").writeText("x")
        files.keepOnly(newer)
        assertEquals(setOf("ledga-2.0.2.apk", "ledga-2.0.2.apk.part"), files.dir().list()!!.toSet())
    }

    @Test
    fun `keeping nothing empties the folder`() {
        files.apk(older).writeText("x")
        files.keepOnly(null)
        assertEquals(0, files.dir().list()!!.size)
    }
}
