package com.ledga.app.ui.onboarding

import androidx.compose.runtime.Composable
import com.ledga.app.testing.snapScreen
import com.ledga.app.work.ImportProgress
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** Spec §15.2: every onboarding step, light/dark × 1.0/1.3. Synthetic data only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class OnboardingScreensTest {
    private val all = listOf(Step.WELCOME, Step.SMS, Step.IMPORT, Step.LINES, Step.NOTIFICATIONS)
    private val preview = InboxPreview(6_385, Instant.parse("2023-03-12T07:00:00Z"), Instant.parse("2026-10-06T06:00:00Z"))

    private fun screen(state: OnboardingState): @Composable () -> Unit = {
        OnboardingScreen(state, {}, {}, {}, {}, {}, { _, _ -> }, {})
    }

    @Test
    fun welcome() = snapScreen("onboarding_welcome", screen(OnboardingState(Step.WELCOME, all, name = "Jane")))

    @Test
    fun sms() = snapScreen("onboarding_sms", screen(OnboardingState(Step.SMS, all)))

    @Test
    fun importPreview() = snapScreen("onboarding_import", screen(OnboardingState(Step.IMPORT, all, preview = preview)))

    @Test
    fun importing() = snapScreen(
        "onboarding_importing",
        screen(OnboardingState(Step.IMPORT, all, preview = preview, import = ImportProgress.Running(1_200, 6_385))),
    )

    @Test
    fun lines() = snapScreen(
        "onboarding_lines",
        screen(OnboardingState(Step.LINES, all, lines = listOf(LineName(1, "0712 000 001", "Safaricom"), LineName(2, "SIM 2", "Line 2")))),
    )

    @Test
    fun notifications() = snapScreen("onboarding_notifications", screen(OnboardingState(Step.NOTIFICATIONS, all)))
}
