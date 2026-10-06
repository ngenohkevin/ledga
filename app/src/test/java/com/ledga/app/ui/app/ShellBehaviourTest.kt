package com.ledga.app.ui.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ledga.app.ui.design.theme.LedgaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ShellBehaviourTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `recovery offers the database only when there is a copy, and retries`() {
        var retries = 0
        compose.setContent {
            LedgaTheme { RecoveryScreen("no such table: mpesa_accounts", canShare = false, onShare = {}, onRetry = { retries++ }) }
        }
        compose.onNodeWithText("Send me the database").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `the interim Home warns when v1's notes and categories couldn't be moved`() {
        compose.setContent { LedgaTheme { InterimHome(InterimHomeState(count = 3, legacyImportFailed = true), onAllowSms = {}) } }
        compose.onNodeWithText(LEGACY_IMPORT_FAILED_TEXT).assertExists()
    }

    @Test
    fun `recovery says the file holds the user's M-Pesa messages before they send it`() {
        compose.setContent { LedgaTheme { RecoveryScreen("no such table: mpesa_accounts", canShare = true, onShare = {}, onRetry = {}) } }
        compose.onNodeWithText(RECOVERY_PRIVACY_TEXT).assertExists()
    }
}
