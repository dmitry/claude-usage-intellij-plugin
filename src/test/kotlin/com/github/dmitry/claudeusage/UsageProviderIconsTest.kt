package com.github.h2a.claudeusage

import kotlin.test.Test
import kotlin.test.assertEquals

class UsageProviderIconsTest {

    @Test
    fun `bundled provider favicons render at status-bar size`() {
        assertEquals(16, UsageProviderIcons.CLAUDE.iconWidth)
        assertEquals(16, UsageProviderIcons.CLAUDE.iconHeight)
        assertEquals(16, UsageProviderIcons.CODEX.iconWidth)
        assertEquals(16, UsageProviderIcons.CODEX.iconHeight)
    }
}
