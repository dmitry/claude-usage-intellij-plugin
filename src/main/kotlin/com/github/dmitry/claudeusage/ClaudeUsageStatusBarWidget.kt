package com.github.dmitry.claudeusage

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar

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

    companion object {
        private const val WIDGET_ID = "ClaudeUsageWidget"
        private const val USAGE_URL = "https://claude.ai/settings/usage"

        private val COLOR_GREEN = Color(144, 238, 144)    // Pastel green
        private val COLOR_YELLOW = Color(255, 245, 157)   // Pastel yellow
        private val COLOR_RED = Color(255, 182, 182)      // Pastel red/pink
        private val COLOR_GRAY = Color(200, 200, 200)     // Light gray
        private val COLOR_BG = Color(240, 240, 240)       // Background gray
    }

    override fun ID(): String = WIDGET_ID

    override fun getComponent(): JComponent = widgetComponent

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        service.addListener {
            widgetComponent.updateUsage()
        }
        widgetComponent.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                showDetailedPopup(widgetComponent)
            }
        })
    }

    override fun dispose() {
        statusBar = null
    }

    private fun getUsageColor(percentage: Int): Color {
        return when {
            percentage > 90 -> COLOR_RED
            percentage > 70 -> COLOR_YELLOW
            else -> COLOR_GREEN
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
            val percentage = usage?.fiveHour?.utilization?.toInt() ?: -1

            val fm = g2d.fontMetrics
            val text = getDisplayText()
            val textWidth = fm.stringWidth(text)
            val textHeight = fm.height

            val rectWidth = textWidth + 10
            val rectHeight = textHeight + 4
            val x = (width - rectWidth) / 2
            val y = (height - rectHeight) / 2

            // Draw background (light gray)
            g2d.color = COLOR_BG
            g2d.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), rectWidth.toFloat(), rectHeight.toFloat(), 6f, 6f))

            // Draw progress fill based on percentage
            if (percentage >= 0) {
                val fillColor = getUsageColor(percentage)
                val fillWidth = (rectWidth * percentage / 100f).coerceAtLeast(6f)

                g2d.color = fillColor
                g2d.clip = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), rectWidth.toFloat(), rectHeight.toFloat(), 6f, 6f)
                g2d.fillRect(x, y, fillWidth.toInt(), rectHeight)
                g2d.clip = null
            }

            // Draw text (dark color for readability on pastel)
            g2d.color = Color(60, 60, 60)
            val textX = x + 5
            val textY = y + fm.ascent + 2
            g2d.drawString(text, textX, textY)

            g2d.dispose()
        }

        private fun getDisplayText(): String {
            val usage = service.getUsage()
            return if (usage?.fiveHour != null) {
                val percentage = usage.fiveHour.utilization.toInt()
                val resetIn = service.formatTimeUntilReset(usage.fiveHour.resetsAt)
                "$percentage% • $resetIn"
            } else {
                "Claude: N/A"
            }
        }
    }

    private fun showDetailedPopup(component: Component) {
        val usage = service.getUsage()
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(12)
        }

        if (usage == null) {
            panel.add(JBLabel("Claude Code usage not available"))
            panel.add(Box.createVerticalStrut(8))
            panel.add(JBLabel("Check ~/.claude/.credentials.json"))
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
        panel.add(createLinkLabel())

        JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, null)
            .setTitle("Claude Code Usage")
            .setFocusable(true)
            .setRequestFocus(true)
            .setMovable(true)
            .setCancelOnClickOutside(true)
            .setCancelButton(com.intellij.openapi.ui.popup.IconButton("Close", com.intellij.icons.AllIcons.Actions.Close))
            .createPopup()
            .showUnderneathOf(component)
    }

    private fun createUsageRow(label: String, percentage: Int, resetIn: String): JPanel {
        val row = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
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
            alignmentX = Component.LEFT_ALIGNMENT
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    BrowserUtil.browse(USAGE_URL)
                }
            })
        }
    }
}
