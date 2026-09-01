package com.github.h2a.claudeusage

data class CodexUsageSnapshot(
    val usage: CodexRateLimitsResult? = null,
    val error: CodexUsageErrorState = CodexUsageErrorState.NONE
)

class CodexUsageState(
    private val load: (String) -> CodexUsageLoadResult = CodexUsageLoader()::load
) {
    @Volatile
    private var current = CodexUsageSnapshot()

    @Synchronized
    fun refresh(configuredPath: String): CodexUsageSnapshot {
        current = when (val result = load(configuredPath)) {
            is CodexUsageLoadResult.Success -> CodexUsageSnapshot(usage = result.usage)
            is CodexUsageLoadResult.Failure -> CodexUsageSnapshot(error = result.error)
        }
        return current
    }

    fun snapshot(): CodexUsageSnapshot = current
}
