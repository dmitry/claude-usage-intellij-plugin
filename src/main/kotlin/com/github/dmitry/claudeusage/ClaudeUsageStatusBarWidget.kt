package com.github.h2a.claudeusage

import com.intellij.icons.AllIcons
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
import javax.swing.*

class ClaudeUsageStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = WIDGET_ID

    override fun getDisplayName(): String = "Claude Code Usage"

    override fun isAvailable(project: Project): Boolean =
        ClaudeUsageSettings.getInstance().state.showClaudeWidget

    override fun createWidget(project: Project): StatusBarWidget = ClaudeUsageStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

    companion object {
        const val WIDGET_ID = "ClaudeUsageWidget"
    }
}

class ClaudeUsageStatusBarWidget(private val project: Project) : CustomStatusBarWidget {
    private var statusBar: StatusBar? = null
    private val service = ClaudeUsageService.getInstance()
    private val widgetComponent: UsageWidgetComponent = UsageWidgetComponent()
    private var listener: (() -> Unit)? = null

    companion object {
        private val COLOR_GREEN = JBColor(Color(144, 238, 144), Color(60, 140, 60))
        private val COLOR_YELLOW = JBColor(Color(255, 245, 157), Color(180, 160, 50))
        private val COLOR_RED = JBColor(Color(255, 182, 182), Color(180, 70, 70))
        private val COLOR_GRAY = JBColor(Color(200, 200, 200), Color(80, 80, 80))
        private val COLOR_BG = JBColor(Color(240, 240, 240), Color(60, 63, 65))
        private val COLOR_TEXT = JBColor(Color(60, 60, 60), Color(210, 210, 210))

        // The credits scale: distinct from the quota green/yellow/red so spending real money never
        // reads as healthy headroom.
        private val COLOR_AMBER = JBColor(Color(255, 224, 178), Color(190, 130, 40))
        private val COLOR_ORANGE = JBColor(Color(255, 198, 145), Color(205, 100, 35))
    }

    override fun ID(): String = ClaudeUsageStatusBarWidgetFactory.WIDGET_ID

    override fun getComponent(): JComponent = widgetComponent

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        listener = {
            widgetComponent.updateUsage()
            statusBar.updateWidget(ClaudeUsageStatusBarWidgetFactory.WIDGET_ID)
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

    /**
     * Credits never read as the healthy green of an included quota - this is real money - and deepen
     * to red as the monthly cap approaches. Reuses the configured thresholds rather than adding
     * settings of their own.
     */
    private fun getCreditsColor(percentage: Int): Color {
        val settings = ClaudeUsageSettings.getInstance().state
        return when {
            percentage > settings.redThreshold -> COLOR_RED
            percentage > settings.yellowThreshold -> COLOR_ORANGE
            else -> COLOR_AMBER
        }
    }

    /** Which palette the pill's fill uses: an included quota, or real money spent. */
    private enum class BarScale { USAGE, CREDITS }

    /** What the status bar renders: the bar-fill percentage (-1 when unknown) and its label. */
    private data class StatusBarDisplay(
        val percentage: Int,
        val text: String,
        val scale: BarScale = BarScale.USAGE
    )

    private fun barColor(display: StatusBarDisplay): Color = when (display.scale) {
        BarScale.USAGE -> getUsageColor(display.percentage)
        BarScale.CREDITS -> getCreditsColor(display.percentage)
    }

    private fun statusBarDisplay(usage: UsageResponse): StatusBarDisplay? {
        val tier = ClaudeUsageSettings.getInstance().state.statusBarQuotaTier
        if (tier == QuotaTiers.CREDITS) return creditsDisplay(usage)

        val percentage = usage.utilizationForTier(tier)?.toInt() ?: return null
        val resetIn = usage.resetsAtForTier(tier)?.let { service.formatTimeUntilReset(it) }

        if (usage.creditsOverflowFor(tier)) {
            creditsOverflowDisplay(usage, resetIn)?.let { return it }
        }

        return StatusBarDisplay(
            percentage = percentage,
            text = if (resetIn != null) "$percentage% • $resetIn" else "$percentage%"
        )
    }

    /**
     * Spend accruing past a maxed window, keeping that window's countdown so it stays clear when the
     * overflow ends. Null when the payload carries no spend figures - the caller then falls back to
     * the exhausted percentage, since an invented `$0.00` would be worse than an honest 100%.
     */
    private fun creditsOverflowDisplay(usage: UsageResponse, resetIn: String?): StatusBarDisplay? {
        val spend = CreditsFormatter.long(
            usage.creditsUsedMajor(),
            usage.creditsLimitMajor(),
            usage.creditsCurrency()
        ) ?: return null
        return StatusBarDisplay(
            percentage = usage.creditsUtilization()?.toInt() ?: 0,
            text = if (resetIn != null) "$spend • $resetIn" else spend,
            scale = BarScale.CREDITS
        )
    }

    /** Credits show spend rather than a percentage, and have no reset countdown. */
    private fun creditsDisplay(usage: UsageResponse): StatusBarDisplay {
        if (!usage.creditsEnabled()) return StatusBarDisplay(-1, "Credits: off")
        val percentage = usage.creditsUtilization()?.toInt() ?: -1
        val spend = CreditsFormatter.compact(
            usage.creditsUsedMajor(),
            usage.creditsLimitMajor(),
            usage.creditsCurrency()
        )
        return StatusBarDisplay(
            percentage = percentage,
            text = spend ?: if (percentage >= 0) "Credits: $percentage%" else "Credits: n/a",
            scale = BarScale.CREDITS
        )
    }

    inner class UsageWidgetComponent : JPanel() {
        private val minWidth = 80
        private val providerIcon = UsageProviderIcons.CLAUDE

        init {
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "Click to see Claude Code usage details"
            getAccessibleContext().accessibleName = "Claude usage"
        }

        fun updateUsage() {
            revalidate()
            repaint()
        }

        private fun calculateSize(): Dimension {
            val fm = getFontMetrics(font)
            val text = currentDisplay().text
            val layout = StatusBarPillLayout.calculate(
                fm.stringWidth(text),
                fm.height,
                providerIcon.iconWidth,
                providerIcon.iconHeight,
                minWidth
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
            val percentage = display.percentage
            val fm = g2d.fontMetrics
            val text = display.text
            val layout = StatusBarPillLayout.calculate(
                fm.stringWidth(text),
                fm.height,
                providerIcon.iconWidth,
                providerIcon.iconHeight,
                minWidth
            )
            val rectWidth = layout.pillWidth
            val rectHeight = layout.pillHeight
            val x = (width - rectWidth) / 2
            val y = (height - rectHeight) / 2
            val rect = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), rectWidth.toFloat(), rectHeight.toFloat(), 6f, 6f)

            g2d.color = COLOR_BG
            g2d.fill(rect)

            if (percentage >= 0) {
                g2d.color = barColor(display)
                g2d.clip = rect
                g2d.fillRect(x, y, (rectWidth * percentage / 100f).coerceAtLeast(6f).toInt(), rectHeight)
                g2d.clip = null
            } else if (service.getError() != UsageErrorState.NONE) {
                g2d.color = COLOR_GRAY
                g2d.fill(rect)
            }

            g2d.color = COLOR_TEXT
            providerIcon.paintIcon(
                this,
                g2d,
                x + 5,
                y + (rectHeight - providerIcon.iconHeight) / 2
            )
            g2d.drawString(
                text,
                x + 5 + providerIcon.iconWidth + 4,
                y + (rectHeight - fm.height) / 2 + fm.ascent
            )
            g2d.dispose()
        }

        /**
         * The whole render model, resolved once per paint: tier lookup, countdown formatting and
         * currency formatting all happen here, and both width formulas read the same string.
         */
        private fun currentDisplay(): StatusBarDisplay =
            service.getUsage()?.let { statusBarDisplay(it) } ?: StatusBarDisplay(-1, errorText())

        private fun errorText(): String {
            return when (service.getError()) {
                UsageErrorState.NO_CREDENTIALS -> "no creds"
                UsageErrorState.AUTH_FAILED -> "auth err"
                UsageErrorState.NETWORK_ERROR -> "offline"
                UsageErrorState.PROXY_ERROR -> "proxy err"
                UsageErrorState.RATE_LIMITED -> "rate limited"
                UsageErrorState.HTTP_ERROR, UsageErrorState.PARSE_ERROR -> "error"
                UsageErrorState.NO_ACCESS_TOKEN -> "no token"
                UsageErrorState.KEYCHAIN_DENIED -> "keychain denied"
                UsageErrorState.KEYCHAIN_TIMEOUT -> "keychain timeout"
                UsageErrorState.KEYCHAIN_ERROR -> "keychain err"
                UsageErrorState.NONE -> "loading..."
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
        rightPanel.add(settingsButton)
        rightPanel.add(refreshButton)

        val bottomPanel = JPanel(BorderLayout()).apply { isOpaque = false }
        bottomPanel.add(rightPanel, BorderLayout.EAST)

        root.add(body, BorderLayout.CENTER)
        root.add(bottomPanel, BorderLayout.SOUTH)

        lateinit var popup: JBPopup

        fun updateControls() {
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
            usage.displayRows().forEach { row ->
                val resetIn = row.resetsAt?.let { service.formatTimeUntilReset(it) }
                panel.add(createUsageRow(row.label, row.percent.toInt(), resetIn))
                panel.add(Box.createVerticalStrut(12))
            }

            if (usage.creditsEnabled()) {
                val percentage = usage.creditsUtilization()?.toInt()
                val spend = CreditsFormatter.long(
                    usage.creditsUsedMajor(),
                    usage.creditsLimitMajor(),
                    usage.creditsCurrency()
                )
                if (percentage != null || spend != null) {
                    panel.add(
                        createUsageRow(
                            QuotaTiers.CREDITS,
                            percentage ?: 0,
                            valueText = spend,
                            barColor = getCreditsColor(percentage ?: 0)
                        )
                    )
                    panel.add(Box.createVerticalStrut(12))
                }
            }
        }

        panel.add(Box.createVerticalStrut(4))
    }

    private fun openSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, ClaudeUsageSettingsConfigurable::class.java)
    }

    /**
     * [valueText] overrides the right-aligned header value, which defaults to the percentage. Credits
     * use it to show spend (`$0.00 of $1,000.00`) where a percentage would be less useful.
     *
     * [barColor] overrides the quota palette, so the credits row tells the same colour story as the
     * status bar pill does while a maxed window is billing to credits.
     */
    private fun createUsageRow(
        label: String,
        percentage: Int,
        resetIn: String? = null,
        valueText: String? = null,
        barColor: Color? = null
    ): JPanel {
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
        headerPanel.add(JBLabel(valueText ?: "$percentage%"), BorderLayout.EAST)

        row.add(headerPanel)
        row.add(Box.createVerticalStrut(4))
        row.add(JProgressBar(0, 100).apply {
            value = percentage
            isStringPainted = false
            preferredSize = Dimension(200, 8)
            maximumSize = Dimension(200, 8)
            alignmentX = Component.LEFT_ALIGNMENT
            foreground = barColor ?: getUsageColor(percentage)
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
}
