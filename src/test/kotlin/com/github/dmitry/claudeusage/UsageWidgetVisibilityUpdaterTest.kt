package com.github.h2a.claudeusage

import kotlin.test.Test
import kotlin.test.assertEquals

class UsageWidgetVisibilityUpdaterTest {

    @Test
    fun `enabling an absent widget adds it once`() {
        val actions = mutableListOf<String>()

        UsageWidgetVisibilityUpdater.sync(
            enabled = true,
            isInstalled = false,
            add = { actions += "add" },
            remove = { actions += "remove" }
        )

        assertEquals(listOf("add"), actions)
    }

    @Test
    fun `disabling an installed widget removes it once`() {
        val actions = mutableListOf<String>()

        UsageWidgetVisibilityUpdater.sync(
            enabled = false,
            isInstalled = true,
            add = { actions += "add" },
            remove = { actions += "remove" }
        )

        assertEquals(listOf("remove"), actions)
    }

    @Test
    fun `matching visibility performs no status bar mutation`() {
        val actions = mutableListOf<String>()

        UsageWidgetVisibilityUpdater.sync(
            enabled = true,
            isInstalled = true,
            add = { actions += "add" },
            remove = { actions += "remove" }
        )

        assertEquals(emptyList(), actions)
    }
}
