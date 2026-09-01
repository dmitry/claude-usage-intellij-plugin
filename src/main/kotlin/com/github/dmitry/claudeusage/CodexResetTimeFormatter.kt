package com.github.h2a.claudeusage

import java.time.Duration
import java.time.Instant

object CodexResetTimeFormatter {
    fun format(resetsAtEpochSeconds: Long, now: Instant = Instant.now()): String {
        val duration = Duration.between(now, Instant.ofEpochSecond(resetsAtEpochSeconds))
        if (duration.isNegative || duration.isZero) return "now"

        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60
        return when {
            days > 0 -> "${days}d ${hours}h ${minutes}m"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
}
