package com.github.h2a.claudeusage

import kotlin.math.max

data class StatusBarPillLayout(
    val pillWidth: Int,
    val pillHeight: Int,
    val componentWidth: Int,
    val componentHeight: Int
) {
    companion object {
        private const val HORIZONTAL_PADDING = 5
        private const val ICON_TEXT_GAP = 4
        private const val COMPONENT_HORIZONTAL_MARGIN = 6
        private const val COMPONENT_VERTICAL_MARGIN = 2

        fun calculate(
            textWidth: Int,
            fontHeight: Int,
            iconWidth: Int,
            iconHeight: Int,
            minimumWidth: Int
        ): StatusBarPillLayout {
            val pillWidth = HORIZONTAL_PADDING + iconWidth + ICON_TEXT_GAP + textWidth + HORIZONTAL_PADDING
            val pillHeight = max(fontHeight + 4, iconHeight + 4)
            return StatusBarPillLayout(
                pillWidth = pillWidth,
                pillHeight = pillHeight,
                componentWidth = max(pillWidth + COMPONENT_HORIZONTAL_MARGIN, minimumWidth),
                componentHeight = pillHeight + COMPONENT_VERTICAL_MARGIN
            )
        }
    }
}
