package com.ledga.app.ui.app

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ledga.app.startup.StartupState
import com.ledga.app.ui.design.theme.LedgaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class LedgaRootContentTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a failed migration shows recovery and shares the pre-v6 copy`() {
        var shared: File? = null
        val copy = File("pre-v6/ledga.db")
        compose.setContent {
            LedgaTheme {
                LedgaRootContent(StartupState.Failed("no such table: mpesa_accounts", copy), onShare = { shared = it }, onRetry = {}) { Text("the app") }
            }
        }
        compose.onNodeWithText("Ledga couldn't update your data").assertExists()
        compose.onNodeWithText("the app").assertDoesNotExist()
        compose.onNodeWithText("Send me the database").performClick()
        assertEquals(copy, shared)
    }

    @Test
    fun `a ready start shows the app`() {
        compose.setContent { LedgaTheme { LedgaRootContent(StartupState.Ready(onboarded = true), {}, {}) { Text("the app") } } }
        compose.onNodeWithText("the app").assertExists()
    }
}
