package com.github.dmitry.claudeusage

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
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
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*

class ClaudeUsageStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "ClaudeUsageWidget"

    override fun getDisplayName(): String = "Claude Code Usage"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = ClaudeUsageStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class ClaudeUsageStatusBarWidget(private val project: Project) : CustomStatusBarWidget {
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

        private val QUOTA_TIERS = listOf(
            "5-hour" to { usage: UsageResponse -> usage.fiveHour },
            "7-day" to { usage: UsageResponse -> usage.sevenDay },
            "7-day Sonnet" to { usage: UsageResponse -> usage.sevenDaySonnet }
        )
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
                if (SwingUtilities.isLeftMouseButton(e)) {
                    showDetailedPopup(widgetComponent)
                }
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
        return usage.quotaForTier(tier)
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
            return Dimension(maxOf(fm.stringWidth(text) + 16, minWidth), fm.height + 6)
        }

        override fun getPreferredSize(): Dimension = calculateSize()

        override fun getMinimumSize(): Dimension = calculateSize()

        override fun getMaximumSize(): Dimension = calculateSize()

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2d = g.create() as Graphics2D
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val percentage = service.getUsage()?.let { getSelectedQuota(it) }?.utilization?.toInt() ?: -1
            val fm = g2d.fontMetrics
            val text = getDisplayText()
            val rectWidth = fm.stringWidth(text) + 10
            val rectHeight = fm.height + 4
            val x = (width - rectWidth) / 2
            val y = (height - rectHeight) / 2
            val rect = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), rectWidth.toFloat(), rectHeight.toFloat(), 6f, 6f)

            g2d.color = COLOR_BG
            g2d.fill(rect)

            if (percentage >= 0) {
                g2d.color = getUsageColor(percentage)
                g2d.clip = rect
                g2d.fillRect(x, y, (rectWidth * percentage / 100f).coerceAtLeast(6f).toInt(), rectHeight)
                g2d.clip = null
            } else if (service.getError() != UsageErrorState.NONE) {
                g2d.color = COLOR_GRAY
                g2d.fill(rect)
            }

            g2d.color = COLOR_TEXT
            g2d.drawString(text, x + 5, y + fm.ascent + 2)
            g2d.dispose()
        }

        private fun getDisplayText(): String {
            val quota = service.getUsage()?.let { getSelectedQuota(it) }
            if (quota != null) {
                val percent = "${quota.utilization.toInt()}%"
                return if (quota.resetsAt != null) "$percent \u2022 ${service.formatTimeUntilReset(quota.resetsAt)}" else percent
            }

            return when (service.getError()) {
                UsageErrorState.NO_CREDENTIALS -> "Claude: no creds"
                UsageErrorState.AUTH_FAILED -> "Claude: auth err"
                UsageErrorState.NETWORK_ERROR -> "Claude: offline"
                UsageErrorState.PROXY_ERROR -> "Claude: proxy err"
                UsageErrorState.RATE_LIMITED -> "Claude: rate limited"
                UsageErrorState.HTTP_ERROR, UsageErrorState.PARSE_ERROR -> "Claude: error"
                UsageErrorState.NO_ACCESS_TOKEN -> "Claude: no token"
                UsageErrorState.KEYCHAIN_DENIED -> "Claude: keychain denied"
                UsageErrorState.KEYCHAIN_TIMEOUT -> "Claude: keychain timeout"
                UsageErrorState.KEYCHAIN_ERROR -> "Claude: keychain err"
                UsageErrorState.NONE -> "Claude: loading..."
            }
        }
    }

    private fun showDetailedPopup(component: Component) {
        // Root paints an explicit opaque background: on the macOS Metal rendering pipeline a
        // popup whose content does not paint a solid background can show the uncleared GPU
        // surface as a fully black window (reported on WebStorm 2026.1 / macOS 26).
        val root = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)
            isOpaque = true
            background = UIUtil.getPanelBackground()
        }

        val body = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        val lastFetchLabel = JBLabel().apply {
            font = font.deriveFont(font.size - 1f)
            foreground = UIUtil.getContextHelpForeground()
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

        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }
        rightPanel.add(lastFetchLabel)
        rightPanel.add(settingsButton)
        rightPanel.add(refreshButton)

        val bottomPanel = JPanel(BorderLayout()).apply { isOpaque = false }
        bottomPanel.add(createLinkLabel(), BorderLayout.WEST)
        bottomPanel.add(rightPanel, BorderLayout.EAST)

        root.add(body, BorderLayout.CENTER)
        root.add(bottomPanel, BorderLayout.SOUTH)

        lateinit var popup: JBPopup

        fun updateControls() {
            lastFetchLabel.text = service.getLastFetchTime()?.let {
                DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(it)
            } ?: ""
            if (service.canRefresh()) {
                refreshButton.isEnabled = true
                refreshButton.toolTipText = "Refresh usage data"
                refreshButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            } else {
                refreshButton.isEnabled = false
                refreshButton.toolTipText = "Refresh available in ${service.secondsUntilCanRefresh()}s"
                refreshButton.cursor = Cursor.getDefaultCursor()
            }
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
            Thread { service.refreshUsage() }.start()
        }

        // Keep the popup live: the timer ticks the refresh cooldown countdown every second,
        // and the service listener rebuilds usage rows whenever fresh data arrives, so the
        // user can wait and refresh again without closing the popup.
        val timer = Timer(1000) { updateControls() }
        timer.isRepeats = true

        val serviceListener: () -> Unit = {
            rebuildBody()
            updateControls()
            if (!popup.isDisposed) {
                SwingUtilities.invokeLater { popup.pack(true, true) }
            }
        }

        rebuildBody()
        updateControls()

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(root, refreshButton)
            .setTitle("Claude Code Usage")
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

        // Metal workaround: force a repaint of the popup window once it is shown so the
        // GPU surface is actually painted instead of staying black on macOS Metal.
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
        val error = service.getError()

        if (usage == null) {
            val errorMessage = if (error != UsageErrorState.NONE) error.message else "Loading usage data..."
            panel.add(JBLabel(errorMessage).apply { alignmentX = Component.LEFT_ALIGNMENT })
            panel.add(Box.createVerticalStrut(8))

            when (error) {
                UsageErrorState.NO_CREDENTIALS ->
                    panel.add(createHintLabel("Run 'claude' in terminal to authenticate"))
                UsageErrorState.AUTH_FAILED ->
                    panel.add(createHintLabel("Run 'claude' in terminal to re-authenticate"))
                UsageErrorState.KEYCHAIN_DENIED -> {
                    panel.add(createHintLabel("Open Keychain Access, search for 'Claude Code-credentials',"))
                    panel.add(createHintLabel("then in Access Control grant your IDE access."))
                    panel.add(createHintLabel("'Allow' asks again on every refresh. 'Always Allow' gives this IDE"))
                    panel.add(createHintLabel("- and every plugin inside it - silent access to the token."))
                }
                UsageErrorState.KEYCHAIN_TIMEOUT ->
                    panel.add(createHintLabel("Approve the macOS Keychain dialog, then click Refresh."))
                UsageErrorState.PROXY_ERROR -> {
                    panel.add(createHintLabel("The proxy refused or could not reach api.anthropic.com."))
                    panel.add(createHintLabel("Check the Proxy URL setting or HTTPS_PROXY in ~/.claude/settings.json."))
                }
                else -> { /* no extra hint */ }
            }
        } else {
            QUOTA_TIERS.forEach { (label, extractor) ->
                extractor(usage)?.let { quota ->
                    val resetIn = quota.resetsAt?.let { service.formatTimeUntilReset(it) }
                    panel.add(createUsageRow(label, quota.utilization.toInt(), resetIn))
                    panel.add(Box.createVerticalStrut(12))
                }
            }

            usage.extraUsage?.let { extra ->
                if (extra.isEnabled && extra.utilization != null) {
                    panel.add(createUsageRow("Extra usage", extra.utilization.toInt(), "enabled"))
                    panel.add(Box.createVerticalStrut(12))
                }
            }
        }

        panel.add(Box.createVerticalStrut(4))
    }

    private fun openSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, ClaudeUsageSettingsConfigurable::class.java)
    }

    private fun createUsageRow(label: String, percentage: Int, resetIn: String?): JPanel {
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

        row.add(headerPanel)
        row.add(Box.createVerticalStrut(4))
        row.add(JProgressBar(0, 100).apply {
            value = percentage
            isStringPainted = false
            preferredSize = Dimension(200, 8)
            maximumSize = Dimension(200, 8)
            alignmentX = Component.LEFT_ALIGNMENT
            foreground = getUsageColor(percentage)
        })
        if (resetIn != null) {
            row.add(Box.createVerticalStrut(2))
            row.add(createHintLabel("resets in $resetIn"))
        }

        return row
    }

    private fun createHintLabel(text: String): JBLabel {
        return JBLabel(text).apply {
            font = font.deriveFont(font.size - 1f)
            foreground = UIUtil.getContextHelpForeground()
            alignmentX = Component.LEFT_ALIGNMENT
        }
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
