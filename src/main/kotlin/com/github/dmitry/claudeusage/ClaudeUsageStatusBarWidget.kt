package com.github.dmitry.claudeusage

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*

class ClaudeUsageStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "ClaudeUsageWidget"

    override fun getDisplayName(): String = "Claude Code Usage"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = ClaudeUsageStatusBarWidget()

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class ClaudeUsageStatusBarWidget : CustomStatusBarWidget {
    private var statusBar: StatusBar? = null
    private val service = ClaudeUsageService.getInstance()
    private val widgetComponent: UsageWidgetComponent = UsageWidgetComponent()
    private var listener: (() -> Unit)? = null

    companion object {
        private const val WIDGET_ID = "ClaudeUsageWidget"
        private const val USAGE_URL = "https://claude.ai/settings/usage"

        private val COLOR_GREEN = JBColor(Color(144, 238, 144), Color(60, 140, 60))
        private val COLOR_YELLOW = JBColor(Color(255, 245, 157), Color(180, 160, 50))
        private val COLOR_RED = JBColor(Color(255, 182, 182), Color(180, 70, 70))
        private val COLOR_GRAY = JBColor(Color(200, 200, 200), Color(80, 80, 80))
        private val COLOR_BG = JBColor(Color(240, 240, 240), Color(60, 63, 65))
        private val COLOR_TEXT = JBColor(Color(60, 60, 60), Color(210, 210, 210))
    }

    override fun ID(): String = WIDGET_ID

    override fun getComponent(): JComponent = widgetComponent

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        listener = {
            widgetComponent.updateUsage()
            statusBar.updateWidget(WIDGET_ID)
        }
        service.addListener(listener!!)
        widgetComponent.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                showDetailedPopup(widgetComponent)
            }
        })
        widgetComponent.updateUsage()
    }

    override fun dispose() {
        listener?.let { service.removeListener(it) }
        listener = null
        statusBar = null
    }

    private fun getUsageColor(percentage: Int): Color {
        val settings = ClaudeUsageSettings.getInstance().state
        return when {
            percentage > settings.redThreshold -> COLOR_RED
            percentage > settings.yellowThreshold -> COLOR_YELLOW
            else -> COLOR_GREEN
        }
    }

    private fun getSelectedQuota(usage: UsageResponse): UsageQuota? {
        val tier = ClaudeUsageSettings.getInstance().state.statusBarQuotaTier
        return when (tier) {
            "5-hour" -> usage.fiveHour
            "7-day" -> usage.sevenDay
            "7-day Sonnet" -> usage.sevenDaySonnet
            else -> usage.fiveHour
        }
    }

    inner class UsageWidgetComponent : JPanel() {
        private val minWidth = 100

        init {
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "Click to see Claude Code usage details"
        }

        fun updateUsage() {
            revalidate()
            repaint()
        }

        private fun calculateSize(): Dimension {
            val fm = getFontMetrics(font)
            val text = getDisplayText()
            val textWidth = maxOf(fm.stringWidth(text) + 16, minWidth)
            val textHeight = fm.height + 6
            return Dimension(textWidth, textHeight)
        }

        override fun getPreferredSize(): Dimension = calculateSize()

        override fun getMinimumSize(): Dimension = calculateSize()

        override fun getMaximumSize(): Dimension = calculateSize()

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2d = g.create() as Graphics2D
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val usage = service.getUsage()
            val quota = usage?.let { getSelectedQuota(it) }
            val percentage = quota?.utilization?.toInt() ?: -1

            val fm = g2d.fontMetrics
            val text = getDisplayText()
            val textWidth = fm.stringWidth(text)
            val textHeight = fm.height

            val rectWidth = textWidth + 10
            val rectHeight = textHeight + 4
            val x = (width - rectWidth) / 2
            val y = (height - rectHeight) / 2

            g2d.color = COLOR_BG
            g2d.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), rectWidth.toFloat(), rectHeight.toFloat(), 6f, 6f))

            if (percentage >= 0) {
                val fillColor = getUsageColor(percentage)
                val fillWidth = (rectWidth * percentage / 100f).coerceAtLeast(6f)

                g2d.color = fillColor
                g2d.clip = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), rectWidth.toFloat(), rectHeight.toFloat(), 6f, 6f)
                g2d.fillRect(x, y, fillWidth.toInt(), rectHeight)
                g2d.clip = null
            } else if (service.getError() != UsageErrorState.NONE) {
                g2d.color = COLOR_GRAY
                g2d.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), rectWidth.toFloat(), rectHeight.toFloat(), 6f, 6f))
            }

            g2d.color = COLOR_TEXT
            val textX = x + 5
            val textY = y + fm.ascent + 2
            g2d.drawString(text, textX, textY)

            g2d.dispose()
        }

        private fun getDisplayText(): String {
            val usage = service.getUsage()
            val quota = usage?.let { getSelectedQuota(it) }
            if (quota != null) {
                val percentage = quota.utilization.toInt()
                val resetIn = service.formatTimeUntilReset(quota.resetsAt)
                return "$percentage% \u2022 $resetIn"
            }

            val error = service.getError()
            return when (error) {
                UsageErrorState.NO_CREDENTIALS -> "Claude: no creds"
                UsageErrorState.AUTH_FAILED -> "Claude: auth err"
                UsageErrorState.NETWORK_ERROR -> "Claude: offline"
                UsageErrorState.HTTP_ERROR -> "Claude: error"
                UsageErrorState.PARSE_ERROR -> "Claude: error"
                UsageErrorState.NO_ACCESS_TOKEN -> "Claude: no token"
                UsageErrorState.NONE -> "Claude: loading..."
            }
        }
    }

    private fun showDetailedPopup(component: Component) {
        val usage = service.getUsage()
        val error = service.getError()

        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(12)
        }

        if (usage == null) {
            val errorMessage = if (error != UsageErrorState.NONE) error.message else "Loading usage data..."
            panel.add(JBLabel(errorMessage).apply {
                alignmentX = Component.LEFT_ALIGNMENT
            })
            panel.add(Box.createVerticalStrut(8))

            if (error == UsageErrorState.NO_CREDENTIALS) {
                panel.add(JBLabel("Run 'claude' in terminal to authenticate").apply {
                    font = font.deriveFont(font.size - 1f)
                    foreground = UIUtil.getContextHelpForeground()
                    alignmentX = Component.LEFT_ALIGNMENT
                })
            } else if (error == UsageErrorState.AUTH_FAILED) {
                panel.add(JBLabel("Run 'claude' in terminal to re-authenticate").apply {
                    font = font.deriveFont(font.size - 1f)
                    foreground = UIUtil.getContextHelpForeground()
                    alignmentX = Component.LEFT_ALIGNMENT
                })
            }
        } else {
            usage.fiveHour?.let {
                val resetIn = service.formatTimeUntilReset(it.resetsAt)
                panel.add(createUsageRow("5-hour", it.utilization.toInt(), resetIn))
                panel.add(Box.createVerticalStrut(12))
            }

            usage.sevenDay?.let {
                val resetIn = service.formatTimeUntilReset(it.resetsAt)
                panel.add(createUsageRow("7-day", it.utilization.toInt(), resetIn))
                panel.add(Box.createVerticalStrut(12))
            }

            usage.sevenDaySonnet?.let {
                val resetIn = service.formatTimeUntilReset(it.resetsAt)
                panel.add(createUsageRow("7-day Sonnet", it.utilization.toInt(), resetIn))
                panel.add(Box.createVerticalStrut(12))
            }

            usage.extraUsage?.let { extra ->
                if (extra.isEnabled && extra.utilization != null) {
                    panel.add(createUsageRow("Extra usage", extra.utilization.toInt(), "enabled"))
                    panel.add(Box.createVerticalStrut(12))
                }
            }
        }

        panel.add(Box.createVerticalStrut(4))

        val bottomPanel = JPanel(BorderLayout()).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
        }

        bottomPanel.add(createLinkLabel(), BorderLayout.WEST)

        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false
        }

        val lastFetch = service.getLastFetchTime()
        if (lastFetch != null) {
            val formatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
            rightPanel.add(JBLabel(formatter.format(lastFetch)).apply {
                font = font.deriveFont(font.size - 1f)
                foreground = UIUtil.getContextHelpForeground()
            })
        }

        val refreshButton = JButton(AllIcons.Actions.Refresh).apply {
            toolTipText = "Refresh usage data"
            isBorderPainted = false
            isContentAreaFilled = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            preferredSize = Dimension(20, 20)
        }
        rightPanel.add(refreshButton)

        bottomPanel.add(rightPanel, BorderLayout.EAST)
        panel.add(bottomPanel)

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, null)
            .setTitle("Claude Code Usage")
            .setFocusable(true)
            .setRequestFocus(true)
            .setMovable(true)
            .setCancelOnClickOutside(true)
            .setCancelButton(com.intellij.openapi.ui.popup.IconButton("Close", AllIcons.Actions.Close))
            .createPopup()

        refreshButton.addActionListener {
            Thread {
                service.refreshUsage()
                SwingUtilities.invokeLater {
                    popup.cancel()
                    showDetailedPopup(component)
                }
            }.start()
        }

        popup.showUnderneathOf(component)
    }

    private fun createUsageRow(label: String, percentage: Int, resetIn: String): JPanel {
        val row = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
        }

        val headerPanel = JPanel(BorderLayout()).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
        }
        headerPanel.add(JBLabel(label), BorderLayout.WEST)
        headerPanel.add(JBLabel("$percentage%"), BorderLayout.EAST)

        val progressBar = JProgressBar(0, 100).apply {
            value = percentage
            isStringPainted = false
            preferredSize = Dimension(200, 8)
            maximumSize = Dimension(200, 8)
            alignmentX = Component.LEFT_ALIGNMENT
            foreground = getUsageColor(percentage)
        }

        val resetLabel = JBLabel("resets in $resetIn").apply {
            font = font.deriveFont(font.size - 1f)
            foreground = UIUtil.getContextHelpForeground()
            alignmentX = Component.LEFT_ALIGNMENT
        }

        row.add(headerPanel)
        row.add(Box.createVerticalStrut(4))
        row.add(progressBar)
        row.add(Box.createVerticalStrut(2))
        row.add(resetLabel)

        return row
    }

    private fun createLinkLabel(): JBLabel {
        return JBLabel("<html><a href=''>View on claude.ai</a></html>").apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    BrowserUtil.browse(USAGE_URL)
                }
            })
        }
    }

}
