package com.github.h2a.claudeusage

import kotlinx.serialization.Serializable

@Serializable
data class CodexRateLimitsRpcResponse(
    val id: Int? = null,
    val result: CodexRateLimitsResult? = null,
    val error: CodexRpcError? = null
)

@Serializable
data class CodexRpcError(
    val code: Int? = null,
    val message: String? = null
)

@Serializable
data class CodexRateLimitsResult(
    val rateLimits: CodexRateLimitBucket? = null,
    val rateLimitsByLimitId: Map<String, CodexRateLimitBucket>? = null
) {
    fun displayRows(): List<CodexUsageRow> {
        val buckets = rateLimitsByLimitId?.values?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(rateLimits)
        return buckets.flatMap { bucket -> bucket.displayRows() }
    }

    fun statusBarRow(): CodexUsageRow? = displayRows().maxByOrNull { it.percent }
}

@Serializable
data class CodexRateLimitBucket(
    val limitId: String? = null,
    val limitName: String? = null,
    val primary: CodexRateLimitWindow? = null,
    val secondary: CodexRateLimitWindow? = null
) {
    fun displayRows(): List<CodexUsageRow> = listOfNotNull(primary, secondary).map { window ->
        val windowLabel = windowDurationLabel(window.windowDurationMins)
        val bucketLabel = if (limitId == null || limitId == DEFAULT_LIMIT_ID) {
            windowLabel
        } else {
            "${humanize(limitName ?: limitId)} · $windowLabel"
        }
        CodexUsageRow(bucketLabel, window.usedPercent, window.resetsAt)
    }

    private fun windowDurationLabel(minutes: Long?): String = when (minutes) {
        300L -> "5-hour"
        10_080L -> "Weekly"
        null -> "Usage"
        else -> when {
            minutes % MINUTES_PER_DAY == 0L -> "${minutes / MINUTES_PER_DAY}-day"
            minutes % MINUTES_PER_HOUR == 0L -> "${minutes / MINUTES_PER_HOUR}-hour"
            else -> "$minutes-minute"
        }
    }

    private fun humanize(value: String): String =
        value.replace('_', ' ').replaceFirstChar { it.uppercase() }

    private companion object {
        const val DEFAULT_LIMIT_ID = "codex"
        const val MINUTES_PER_HOUR = 60L
        const val MINUTES_PER_DAY = 24L * MINUTES_PER_HOUR
    }
}

@Serializable
data class CodexRateLimitWindow(
    val usedPercent: Double,
    val windowDurationMins: Long? = null,
    val resetsAt: Long? = null
)

data class CodexUsageRow(
    val label: String,
    val percent: Double,
    val resetsAt: Long?
)
