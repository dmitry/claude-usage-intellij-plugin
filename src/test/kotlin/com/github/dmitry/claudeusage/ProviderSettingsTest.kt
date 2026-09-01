package com.github.h2a.claudeusage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderSettingsTest {

    @Test
    fun `existing installs keep Claude enabled while Codex is opt-in`() {
        val state = ClaudeUsageSettings.State()

        assertTrue(state.showClaudeWidget)
        assertFalse(state.showCodexWidget)
        assertEquals("", state.codexExecutablePath)
    }
}
