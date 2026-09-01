package com.github.h2a.claudeusage

import kotlin.test.Test
import kotlin.test.assertEquals

class CodexUsageStateTest {

    @Test
    fun `successful refresh publishes Codex usage and clears an earlier error`() {
        val usage = CodexRateLimitsResult(
            rateLimits = CodexRateLimitBucket(
                limitId = "codex",
                primary = CodexRateLimitWindow(21.0, 10_080, 1788796558)
            )
        )
        val state = CodexUsageState { CodexUsageLoadResult.Success(usage) }

        val snapshot = state.refresh("")

        assertEquals(usage, snapshot.usage)
        assertEquals(CodexUsageErrorState.NONE, snapshot.error)
    }
}
