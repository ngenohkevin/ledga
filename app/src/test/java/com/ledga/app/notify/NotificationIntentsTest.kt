package com.ledga.app.notify

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.ledga.app.MainActivity
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R100: what a tapped notification carries into MainActivity. */
@RunWith(RobolectricTestRunner::class)
class NotificationIntentsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every kind of tap comes back as it went in`() {
        val taps = listOf(
            OpenedNotification("large:TJK4AB12FA", NotificationTap.Payment("TJK4AB12FA")),
            OpenedNotification("fuliza-due:1:2026-11-02:3d", NotificationTap.Fuliza(1)),
            OpenedNotification("fuliza-due:none:2026-11-02:0d", NotificationTap.Fuliza(null)),
            OpenedNotification(
                "weekly:2026-10-05",
                NotificationTap.Spending(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-11")),
            ),
        )
        for (opened in taps) {
            val intent = NotificationIntents.intent(context, opened)
            assertEquals(MainActivity::class.java.name, intent.component?.className)
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0, "an open Ledga gets it in onNewIntent")
            assertEquals(opened, NotificationIntents.read(intent))
        }
    }

    @Test
    fun `any other launch opens nothing`() {
        assertNull(NotificationIntents.read(null))
        assertNull(NotificationIntents.read(Intent(Intent.ACTION_MAIN)))
        val noDays = Intent().putExtra("com.ledga.app.alert", "daily:2026-10-05").putExtra("com.ledga.app.tap", "spending")
        assertNull(NotificationIntents.read(noDays), "a summary without its days")
    }

    @Test
    fun `a handled intent opens nothing again`() {
        val day = LocalDate.parse("2026-10-05")
        val intent = NotificationIntents.intent(context, OpenedNotification("daily:2026-10-05", NotificationTap.Spending(day, day)))
        NotificationIntents.clear(intent)
        assertNull(NotificationIntents.read(intent))
    }
}
