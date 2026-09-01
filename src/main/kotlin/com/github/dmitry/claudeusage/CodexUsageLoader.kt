package com.github.h2a.claudeusage

enum class CodexUsageErrorState(val message: String) {
    NONE(""),
    CLI_NOT_FOUND("Codex CLI not found - set its path in Settings"),
    START_FAILED("Codex CLI could not start"),
    RUNTIME_MISSING("Codex CLI could not find Node.js"),
    UPDATE_REQUIRED("Codex CLI is too old - update Codex"),
    AUTH_FAILED("Codex must be signed in with ChatGPT - run 'codex login'"),
    TIMEOUT("Codex usage request timed out"),
    REQUEST_FAILED("Could not fetch Codex usage")
}

sealed interface CodexUsageLoadResult {
    data class Success(val usage: CodexRateLimitsResult) : CodexUsageLoadResult
    data class Failure(val error: CodexUsageErrorState) : CodexUsageLoadResult
}

class CodexUsageLoader(
    private val resolveExecutable: (String) -> String? = { configuredPath ->
        CodexCliResolver.resolve(configuredPath)
    },
    private val fetchRateLimits: (String) -> CodexRateLimitsResult = CodexAppServerClient()::fetch
) {
    fun load(configuredPath: String): CodexUsageLoadResult {
        val executable = resolveExecutable(configuredPath)
            ?: return CodexUsageLoadResult.Failure(CodexUsageErrorState.CLI_NOT_FOUND)
        return try {
            CodexUsageLoadResult.Success(fetchRateLimits(executable))
        } catch (e: CodexAppServerException) {
            val error = when {
                e.failure == CodexAppServerFailure.START_FAILED -> CodexUsageErrorState.START_FAILED
                e.failure == CodexAppServerFailure.RUNTIME_MISSING -> CodexUsageErrorState.RUNTIME_MISSING
                e.failure == CodexAppServerFailure.UPDATE_REQUIRED -> CodexUsageErrorState.UPDATE_REQUIRED
                e.failure == CodexAppServerFailure.TIMEOUT -> CodexUsageErrorState.TIMEOUT
                e.message?.contains("method not found", ignoreCase = true) == true -> CodexUsageErrorState.UPDATE_REQUIRED
                e.message?.contains("login", ignoreCase = true) == true -> CodexUsageErrorState.AUTH_FAILED
                e.message?.contains("timed out", ignoreCase = true) == true -> CodexUsageErrorState.TIMEOUT
                else -> CodexUsageErrorState.REQUEST_FAILED
            }
            CodexUsageLoadResult.Failure(error)
        }
    }
}
