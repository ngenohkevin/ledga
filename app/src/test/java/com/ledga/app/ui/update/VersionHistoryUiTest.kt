package com.ledga.app.ui.update

import com.ledga.app.data.update.CheckFailure
import com.ledga.app.data.update.GitHubJson
import com.ledga.app.data.update.UpdateState
import com.ledga.app.testing.ghList
import com.ledga.app.testing.ghRelease
import com.ledga.core.update.AppVersion
import com.ledga.core.update.NotesSection
import com.ledga.core.update.UpdateChannel
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** R142: every release on the channel, the installed one marked. Synthetic releases. */
class VersionHistoryUiTest {
    private val releases = GitHubJson.releases(ghList(ghRelease("v2.0.0-beta.2"), ghRelease("v2.0.0-beta.1"), ghRelease("v1.6.0", withManifest = false)))
    private val state = UpdateState(installed = AppVersion.parse("2.0.0-beta.1")!!, channel = UpdateChannel.BETA, history = releases)

    @Test
    fun `each release shows its version, day and kind, and the installed one is marked`() {
        val rows = VersionHistoryUi.of(state, expanded = emptySet()).rows
        assertEquals(listOf("2.0.0-beta.2", "2.0.0-beta.1", "1.6.0"), rows.map { it.title })
        assertEquals(listOf("1 Oct 2026 · Beta", "1 Oct 2026 · Beta", "1 Oct 2026"), rows.map { it.line })
        assertEquals(listOf(false, true, false), rows.map { it.installed })
        assertEquals(listOf(NotesSection("What's new", listOf("Something new in 2.0.0-beta.2"))), rows.first().notes)
    }

    @Test
    fun `a release's notes open and close by its tag`() {
        val ui = VersionHistoryUi.of(state, expanded = setOf("v2.0.0-beta.2"))
        assertEquals(setOf("v2.0.0-beta.2"), ui.expanded)
    }

    @Test
    fun `with nothing cached, why it couldn't fetch the list is kept for the empty state`() {
        val ui = VersionHistoryUi.of(state.copy(history = emptyList(), failure = CheckFailure.OFFLINE), expanded = emptySet())
        assertTrue(ui.rows.isEmpty())
        assertEquals(UpdateText.failureLine(CheckFailure.OFFLINE), ui.failureLine)
    }
}
