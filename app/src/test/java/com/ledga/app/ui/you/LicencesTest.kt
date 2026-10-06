package com.ledga.app.ui.you

import com.ledga.app.testing.snapScreen
import com.ledga.app.ui.app.ShellFrame
import java.io.File
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** R80: every licence listed ships with the app, Apache 2.0 included. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class LicencesTest {
    @Test
    fun `every listed licence is in the app's assets`() {
        Licences.ALL.forEach { l -> assertTrue(File("src/main/assets/${l.asset}").readText().isNotBlank(), l.asset) }
        val apache = File("src/main/assets/licenses/apache-2.0.txt").readText()
        assertTrue(apache.contains("Apache License") && apache.contains("Version 2.0, January 2004"))
    }

    @Test
    fun licences() = snapScreen("licences") { ShellFrame(null, onSelect = {}) { LicencesContent(onBack = {}, onOpen = {}) } }
}
