package com.github.h2a.claudeusage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderSettingsComponentTest {

    @Test
    fun `provider controls retain independent widget choices and Codex path`() {
        val component = ClaudeUsageSettingsComponent()

        component.setShowClaudeWidget(false)
        component.setShowCodexWidget(true)
        component.setCodexExecutablePath("/opt/homebrew/bin/codex")

        assertFalse(component.getShowClaudeWidget())
        assertTrue(component.getShowCodexWidget())
        assertEquals("/opt/homebrew/bin/codex", component.getCodexExecutablePath())
    }

    @Test
    fun `Codex setting shows the auto-detected and configured executables`() {
        val component = ClaudeUsageSettingsComponent { configuredPath ->
            if (configuredPath.isBlank()) "/auto/bin/codex" else configuredPath.takeIf { it == "/custom/bin/codex" }
        }

        assertEquals("Auto-detected: /auto/bin/codex", component.getCodexExecutableStatus())

        component.setCodexExecutablePath("/custom/bin/codex")

        assertEquals("Configured: /custom/bin/codex", component.getCodexExecutableStatus())
    }
}
