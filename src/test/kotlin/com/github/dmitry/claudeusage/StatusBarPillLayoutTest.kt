package com.github.h2a.claudeusage

import kotlin.test.Test
import kotlin.test.assertEquals

class StatusBarPillLayoutTest {

    @Test
    fun `pill width reserves space for favicon gap and usage text`() {
        val layout = StatusBarPillLayout.calculate(
            textWidth = 50,
            fontHeight = 15,
            iconWidth = 16,
            iconHeight = 16,
            minimumWidth = 80
        )

        assertEquals(80, layout.pillWidth)
        assertEquals(86, layout.componentWidth)
        assertEquals(20, layout.pillHeight)
        assertEquals(22, layout.componentHeight)
    }
}
