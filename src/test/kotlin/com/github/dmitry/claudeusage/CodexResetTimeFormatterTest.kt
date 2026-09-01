package com.github.h2a.claudeusage

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class CodexResetTimeFormatterTest {

    @Test
    fun `epoch reset time uses the same day hour minute countdown as Claude`() {
        val now = Instant.ofEpochSecond(1_000)
        val reset = now.plusSeconds(2 * 86_400L + 3 * 3_600L + 4 * 60L).epochSecond

        assertEquals("2d 3h 4m", CodexResetTimeFormatter.format(reset, now))
    }
}
