package com.github.h2a.claudeusage

import kotlin.test.Test
import kotlin.test.assertEquals

class CodexUsageLoaderTest {

    @Test
    fun `missing Codex executable has its own actionable error state`() {
        val loader = CodexUsageLoader(
            resolveExecutable = { null },
            fetchRateLimits = { error("must not fetch without an executable") }
        )

        val result = loader.load("")

        assertEquals(
            CodexUsageLoadResult.Failure(CodexUsageErrorState.CLI_NOT_FOUND),
            result
        )
    }

    @Test
    fun `Codex login error becomes an authentication error state`() {
        val loader = CodexUsageLoader(
            resolveExecutable = { "/test/codex" },
            fetchRateLimits = { throw CodexAppServerException("ChatGPT login required") }
        )

        val result = loader.load("")

        assertEquals(
            CodexUsageLoadResult.Failure(CodexUsageErrorState.AUTH_FAILED),
            result
        )
    }

    @Test
    fun `Codex timeout keeps a distinct retryable error state`() {
        val loader = CodexUsageLoader(
            resolveExecutable = { "/test/codex" },
            fetchRateLimits = { throw CodexAppServerException("Codex usage request timed out") }
        )

        val result = loader.load("")

        assertEquals(
            CodexUsageLoadResult.Failure(CodexUsageErrorState.TIMEOUT),
            result
        )
    }

    @Test
    fun `missing Codex runtime becomes an actionable runtime error state`() {
        val loader = CodexUsageLoader(
            resolveExecutable = { "/test/codex" },
            fetchRateLimits = {
                throw CodexAppServerException(
                    "Codex CLI could not find Node.js",
                    failure = CodexAppServerFailure.RUNTIME_MISSING
                )
            }
        )

        val result = loader.load("")

        assertEquals(
            CodexUsageLoadResult.Failure(CodexUsageErrorState.RUNTIME_MISSING),
            result
        )
    }

    @Test
    fun `Codex process launch failure has its own error state`() {
        val loader = CodexUsageLoader(
            resolveExecutable = { "/test/codex" },
            fetchRateLimits = {
                throw CodexAppServerException(
                    "Could not start Codex CLI",
                    failure = CodexAppServerFailure.START_FAILED
                )
            }
        )

        val result = loader.load("")

        assertEquals(
            CodexUsageLoadResult.Failure(CodexUsageErrorState.START_FAILED),
            result
        )
    }

    @Test
    fun `unsupported rate limits method asks the user to update Codex`() {
        val loader = CodexUsageLoader(
            resolveExecutable = { "/test/codex" },
            fetchRateLimits = { throw CodexAppServerException("Method not found: account/rateLimits/read") }
        )

        val result = loader.load("")

        assertEquals(
            CodexUsageLoadResult.Failure(CodexUsageErrorState.UPDATE_REQUIRED),
            result
        )
    }
}
