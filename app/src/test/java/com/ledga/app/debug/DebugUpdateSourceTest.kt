package com.ledga.app.debug

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ledga.app.data.update.UpdateEndpoint
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Owner call A, R140: Ledga dev's update source. */
@RunWith(RobolectricTestRunner::class)
class DebugUpdateSourceTest {
    private val source = DebugUpdateSource(ApplicationProvider.getApplicationContext<Context>())
    private val endpoints = DebugUpdateEndpoints(source)

    @Test
    fun `unset, Ledga dev reads GitHub for its history and never offers an update`() {
        assertEquals(UpdateEndpoint(UpdateEndpoint.GITHUB, offersUpdates = false), endpoints.current())
    }

    @Test
    fun `a local source is read, and offers updates until it is cleared`() {
        assertTrue(source.set("http://127.0.0.1:8765/"))
        assertEquals(UpdateEndpoint("http://127.0.0.1:8765/releases.json", offersUpdates = true), endpoints.current())
        assertTrue(source.set(null))
        assertFalse(endpoints.current().offersUpdates)
    }

    @Test
    fun `anything but 127_0_0_1 with a port is refused`() {
        for (base in listOf("http://example.test:8765", "https://127.0.0.1:8765", "http://127.0.0.1", "http://10.0.0.2:8765")) {
            assertFalse(source.set(base), base)
        }
        assertNull(source.base)
    }
}
