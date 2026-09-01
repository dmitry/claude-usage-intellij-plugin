package com.github.h2a.claudeusage

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.SwingUtilities
import javax.swing.Timer

class CodexUsageStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = WIDGET_ID

    override fun getDisplayName(): String = "Codex Usage"

    override fun isAvailable(project: Project): Boolean =
        ClaudeUsageSettings.getInstance().state.showCodexWidget

    override fun createWidget(project: Project): StatusBarWidget = CodexUsageStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

    companion object {
        const val WIDGET_ID = "CodexUsageWidget"
    }
}

class CodexUsageStatusBarWidget(private val project: Project) : CustomStatusBarWidget {
    private val service = CodexUsageService.getInstance()
    private val widgetComponent = UsageWidgetComponent()
    private var statusBar: StatusBar? = null
    private var listener: (() -> Unit)? = null

    override fun ID(): String = CodexUsageStatusBarWidgetFactory.WIDGET_ID

    override fun getComponent(): JComponent = widgetComponent

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        listener = {
            widgetComponent.updateUsage()
            statusBar.updateWidget(ID())
        }
        service.addListener(listener!!)
        widgetComponent.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) showDetailedPopup(widgetComponent)
            }
        })
        widgetComponent.updateUsage()
        if (service.getUsage() == null && service.canRefresh()) {
            ApplicationManager.getApplication().executeOnPooledThread { service.refreshUsage() }
        }
    }

    override fun dispose() {
        listener?.let(service::removeListener)
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

    private data class StatusBarDisplay(val percentage: Int, val text: String)

    private fun statusBarDisplay(usage: CodexRateLimitsResult): StatusBarDisplay? {
        val row = usage.statusBarRow() ?: return null
        val percentage = row.percent.toInt()
        val resetIn = row.resetsAt?.let(service::formatTimeUntilReset)
        return StatusBarDisplay(
            percentage,
            if (resetIn != null) "$percentage% • $resetIn" else "$percentage%"
        )
    }

    inner class UsageWidgetComponent : JPanel() {
        private val minimumPillWidth = 80
        private val providerIcon = UsageProviderIcons.CODEX

        init {
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "Click to see Codex usage details"
            getAccessibleContext().accessibleName = "Codex usage"
        }

        fun updateUsage() {
            revalidate()
            repaint()
        }

        private fun calculateSize(): Dimension {
            val fm = getFontMetrics(font)
            val layout = StatusBarPillLayout.calculate(
                fm.stringWidth(currentDisplay().text),
                fm.height,
                providerIcon.iconWidth,
                providerIcon.iconHeight,
                minimumPillWidth
            )
            return Dimension(layout.componentWidth, layout.componentHeight)
        }

        override fun getPreferredSize(): Dimension = calculateSize()
        override fun getMinimumSize(): Dimension = calculateSize()
        override fun getMaximumSize(): Dimension = calculateSize()

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2d = g.create() as Graphics2D
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val display = currentDisplay()
            val fm = g2d.fontMetrics
            val layout = StatusBarPillLayout.calculate(
                fm.stringWidth(display.text),
                fm.height,
                providerIcon.iconWidth,
                providerIcon.iconHeight,
                minimumPillWidth
            )
            val x = (width - layout.pillWidth) / 2
            val y = (height - layout.pillHeight) / 2
            val rect = RoundRectangle2D.Float(
                x.toFloat(),
                y.toFloat(),
                layout.pillWidth.toFloat(),
                layout.pillHeight.toFloat(),
                6f,
                6f
            )

            g2d.color = COLOR_BG
            g2d.fill(rect)
            if (display.percentage >= 0) {
                g2d.color = getUsageColor(display.percentage)
                g2d.clip = rect
                g2d.fillRect(
                    x,
                    y,
                    (layout.pillWidth * display.percentage / 100f).coerceAtLeast(6f).toInt(),
                    layout.pillHeight
                )
                g2d.clip = null
            } else if (service.getError() != CodexUsageErrorState.NONE) {
                g2d.color = COLOR_GRAY
                g2d.fill(rect)
            }

            providerIcon.paintIcon(
                this,
                g2d,
                x + 5,
                y + (layout.pillHeight - providerIcon.iconHeight) / 2
            )
            g2d.color = COLOR_TEXT
            g2d.drawString(
                display.text,
                x + 5 + providerIcon.iconWidth + 4,
                y + (layout.pillHeight - fm.height) / 2 + fm.ascent
            )
            g2d.dispose()
        }

        private fun currentDisplay(): StatusBarDisplay =
            service.getUsage()?.let(::statusBarDisplay) ?: StatusBarDisplay(-1, errorText())

        private fun errorText(): String = when (service.getError()) {
            CodexUsageErrorState.CLI_NOT_FOUND -> "CLI not found"
            CodexUsageErrorState.START_FAILED -> "start err"
            CodexUsageErrorState.RUNTIME_MISSING -> "runtime err"
            CodexUsageErrorState.UPDATE_REQUIRED -> "update CLI"
            CodexUsageErrorState.AUTH_FAILED -> "auth err"
            CodexUsageErrorState.TIMEOUT -> "timeout"
            CodexUsageErrorState.REQUEST_FAILED -> "error"
            CodexUsageErrorState.NONE -> "loading..."
        }
    }

    private fun showDetailedPopup(component: Component) {
        val root = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)
            isOpaque = true
            background = UIUtil.getPanelBackground()
        }
        val body = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        val refreshButton = JButton(AllIcons.Actions.Refresh).apply {
            isBorderPainted = false
            isContentAreaFilled = false
            preferredSize = Dimension(20, 20)
        }
        val settingsButton = JButton(AllIcons.General.Settings).apply {
            isBorderPainted = false
            isContentAreaFilled = false
            preferredSize = Dimension(20, 20)
            toolTipText = "Settings"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener { openSettings() }
        }
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false
            add(settingsButton)
            add(refreshButton)
        }
        root.add(body, BorderLayout.CENTER)
        root.add(JPanel(BorderLayout()).apply {
            isOpaque = false
            add(rightPanel, BorderLayout.EAST)
        }, BorderLayout.SOUTH)

        lateinit var popup: JBPopup

        fun updateControls() {
            val available = service.canRefresh()
            refreshButton.isEnabled = available
            refreshButton.toolTipText = if (available) {
                "Refresh Codex usage"
            } else {
                "Refresh available in ${service.secondsUntilCanRefresh()}s"
            }
            refreshButton.cursor = Cursor.getPredefinedCursor(
                if (available) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR
            )
        }

        fun rebuildBody() {
            body.removeAll()
            fillBody(body)
            body.revalidate()
            body.repaint()
        }

        refreshButton.addActionListener {
            if (!service.canRefresh()) return@addActionListener
            updateControls()
            ApplicationManager.getApplication().executeOnPooledThread { service.refreshUsage() }
        }

        val timer = Timer(1000) { updateControls() }.apply { isRepeats = true }
        val serviceListener: () -> Unit = {
            rebuildBody()
            updateControls()
            if (!popup.isDisposed) SwingUtilities.invokeLater { popup.pack(true, true) }
        }

        rebuildBody()
        updateControls()
        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(root, refreshButton)
            .setTitle("Codex Usage")
            .setFocusable(true)
            .setRequestFocus(true)
            .setMovable(true)
            .setCancelOnClickOutside(true)
            .setCancelButton(com.intellij.openapi.ui.popup.IconButton("Close", AllIcons.Actions.Close))
            .createPopup()
        popup.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                timer.stop()
                service.removeListener(serviceListener)
            }
        })
        service.addListener(serviceListener)
        timer.start()
        popup.showUnderneathOf(component)

        SwingUtilities.invokeLater {
            val window = SwingUtilities.getWindowAncestor(root)
            if (window != null) {
                window.validate()
                window.repaint()
            } else {
                root.revalidate()
                root.repaint()
            }
        }
    }

    private fun fillBody(panel: JPanel) {
        val usage = service.getUsage()
        if (usage == null) {
            val error = service.getError()
            panel.add(JBLabel(if (error == CodexUsageErrorState.NONE) "Loading usage data..." else error.message).apply {
                alignmentX = Component.LEFT_ALIGNMENT
            })
            panel.add(Box.createVerticalStrut(8))
            when (error) {
                CodexUsageErrorState.CLI_NOT_FOUND ->
                    panel.add(createHintLabel("Install Codex CLI or set its executable in Settings."))
                CodexUsageErrorState.START_FAILED ->
                    panel.add(createHintLabel("Check the Codex executable shown in Settings."))
                CodexUsageErrorState.RUNTIME_MISSING ->
                    panel.add(createHintLabel("Install Node.js or set the full Codex executable path in Settings."))
                CodexUsageErrorState.UPDATE_REQUIRED ->
                    panel.add(createHintLabel("Update the installed Codex CLI, then refresh."))
                CodexUsageErrorState.AUTH_FAILED ->
                    panel.add(createHintLabel("Run 'codex login' in a terminal, then refresh."))
                CodexUsageErrorState.TIMEOUT ->
                    panel.add(createHintLabel("The local Codex CLI did not answer in time."))
                else -> Unit
            }
        } else {
            val rows = usage.displayRows()
            if (rows.isEmpty()) {
                panel.add(JBLabel("No Codex usage limits were returned.").apply {
                    alignmentX = Component.LEFT_ALIGNMENT
                })
            } else {
                rows.forEach { row ->
                    panel.add(createUsageRow(row))
                    panel.add(Box.createVerticalStrut(12))
                }
            }
        }
        panel.add(Box.createVerticalStrut(4))
    }

    private fun createUsageRow(usage: CodexUsageRow): JPanel {
        val percentage = usage.percent.toInt()
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false

            add(JPanel(BorderLayout()).apply {
                alignmentX = Component.LEFT_ALIGNMENT
                isOpaque = false
                add(JBLabel(usage.label), BorderLayout.WEST)
                add(JBLabel("$percentage%"), BorderLayout.EAST)
            })
            add(Box.createVerticalStrut(4))
            add(JProgressBar(0, 100).apply {
                value = percentage
                isStringPainted = false
                preferredSize = Dimension(200, 8)
                maximumSize = Dimension(200, 8)
                alignmentX = Component.LEFT_ALIGNMENT
                foreground = getUsageColor(percentage)
            })
            usage.resetsAt?.let { reset ->
                add(Box.createVerticalStrut(2))
                add(createHintLabel("resets in ${service.formatTimeUntilReset(reset)}"))
            }
        }
    }

    private fun createHintLabel(text: String): JBLabel = JBLabel(text).apply {
        font = font.deriveFont(font.size - 1f)
        foreground = UIUtil.getContextHelpForeground()
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private fun openSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, ClaudeUsageSettingsConfigurable::class.java)
    }

    companion object {
        private val COLOR_GREEN = JBColor(Color(144, 238, 144), Color(60, 140, 60))
        private val COLOR_YELLOW = JBColor(Color(255, 245, 157), Color(180, 160, 50))
        private val COLOR_RED = JBColor(Color(255, 182, 182), Color(180, 70, 70))
        private val COLOR_GRAY = JBColor(Color(200, 200, 200), Color(80, 80, 80))
        private val COLOR_BG = JBColor(Color(240, 240, 240), Color(60, 63, 65))
        private val COLOR_TEXT = JBColor(Color(60, 60, 60), Color(210, 210, 210))
    }
}
