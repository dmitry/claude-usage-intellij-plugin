package com.github.h2a.claudeusage

import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ClaudeUsageSettingsComponent(
    private val resolveCodexExecutable: (String) -> String? = { configuredPath ->
        CodexCliResolver.resolve(configuredPath)
    }
) {

    private val mainPanel: JPanel
    private val showClaudeWidgetCheckBox = JBCheckBox("Show Claude usage widget")
    private val showCodexWidgetCheckBox = JBCheckBox("Show Codex usage widget")
    private val codexExecutablePathField = JBTextField().apply {
        toolTipText = "Leave empty to auto-detect Codex and repair the CLI launch PATH for the IDE"
    }
    private val codexExecutableStatusLabel = JBLabel().apply {
        foreground = JBColor.GRAY
    }
    private val quotaTierComboBox = JComboBox(ClaudeUsageSettings.QUOTA_TIER_OPTIONS)
    private val yellowThresholdField = JSpinner(SpinnerNumberModel(70, 0, 100, 1))
    private val redThresholdField = JSpinner(SpinnerNumberModel(90, 0, 100, 1))
    private val credentialsPathField = JBTextField()
    private val refreshIntervalField = JSpinner(SpinnerNumberModel(ClaudeUsageSettings.DEFAULT_REFRESH_INTERVAL_MINUTES, 1, 60, 1))
    private val useMacKeychainCheckBox = JBCheckBox("Use macOS Keychain (recommended on macOS)").apply {
        isEnabled = SystemInfo.isMac
        toolTipText = if (SystemInfo.isMac) {
            "Read credentials from macOS Keychain (Claude Code stores them there). Falls back to the file below if Keychain is unavailable."
        } else {
            "Available on macOS only"
        }
    }
    private val notifyOnThresholdCheckBox = JBCheckBox("Show IDE notification when usage crosses a warning threshold").apply {
        toolTipText = "Pops up a balloon when the status bar quota crosses the yellow or red threshold"
    }
    private val soundOnCreditsSwitchCheckBox = JBCheckBox("Play a sound when usage switches to or from credit spend").apply {
        toolTipText = "A low chime when the status bar quota hits its limit and starts billing to credits, " +
            "and a brighter one when it resets and included usage is available again"
    }
    private val proxyUrlField = JBTextField().apply {
        toolTipText = "e.g. http://user:pass@proxyhost:3128 or socks5://proxyhost:1080. " +
            "Leave empty to auto-detect, or type 'none' to always connect directly."
    }
    private val proxyDetectedLabel = JBLabel().apply {
        foreground = JBColor.GRAY
    }
    private val proxyCredentialsLabel = JBLabel(
        "On Apply, any password you enter moves to the IDE password safe - it is never stored in plain text."
    ).apply {
        foreground = JBColor.GRAY
    }
    private val resetButton = JButton("Reset to Defaults")

    init {
        val resetPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            add(resetButton)
        }

        mainPanel = FormBuilder.createFormBuilder()
            .addComponent(JBLabel("Usage widgets"))
            .addComponent(showClaudeWidgetCheckBox)
            .addComponent(showCodexWidgetCheckBox)
            .addLabeledComponent(JBLabel("Codex executable (empty = auto-detect):"), codexExecutablePathField, 1, false)
            .addComponent(codexExecutableStatusLabel)
            .addSeparator()
            .addComponent(JBLabel("Claude"))
            .addLabeledComponent(JBLabel("Status bar quota display:"), quotaTierComboBox, 1, false)
            .addLabeledComponent(JBLabel("Yellow warning threshold (%):"), yellowThresholdField, 1, false)
            .addLabeledComponent(JBLabel("Red warning threshold (%):"), redThresholdField, 1, false)
            .addLabeledComponent(JBLabel("Credentials file path:"), credentialsPathField, 1, false)
            .addLabeledComponent(JBLabel("Refresh interval (minutes):"), refreshIntervalField, 1, false)
            .addComponent(useMacKeychainCheckBox)
            .addComponent(notifyOnThresholdCheckBox)
            .addComponent(soundOnCreditsSwitchCheckBox)
            .addLabeledComponent(JBLabel("Proxy URL (empty = auto-detect, 'none' = direct):"), proxyUrlField, 1, false)
            .addComponent(proxyDetectedLabel)
            .addComponent(proxyCredentialsLabel)
            .addSeparator()
            .addComponent(resetPanel)
            .addComponentFillVertically(JPanel(), 0)
            .getPanel()

        resetButton.addActionListener {
            resetToDefaults()
        }

        codexExecutablePathField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent) = refreshCodexExecutableStatus()
            override fun removeUpdate(event: DocumentEvent) = refreshCodexExecutableStatus()
            override fun changedUpdate(event: DocumentEvent) = refreshCodexExecutableStatus()
        })

        refreshCodexExecutableStatus()
        refreshProxyDetectedLabel()
    }

    fun getPanel(): JPanel = mainPanel

    fun getPreferredFocusedComponent(): JComponent = quotaTierComboBox

    fun getQuotaTier(): String = quotaTierComboBox.selectedItem as String

    fun getShowClaudeWidget(): Boolean = showClaudeWidgetCheckBox.isSelected

    fun getShowCodexWidget(): Boolean = showCodexWidgetCheckBox.isSelected

    fun getCodexExecutablePath(): String = codexExecutablePathField.text

    fun getCodexExecutableStatus(): String = codexExecutableStatusLabel.text

    fun getYellowThreshold(): Int = yellowThresholdField.value as Int

    fun getRedThreshold(): Int = redThresholdField.value as Int

    fun getCredentialsPath(): String = credentialsPathField.text

    fun getRefreshIntervalMinutes(): Int = refreshIntervalField.value as Int

    fun getUseMacKeychain(): Boolean = useMacKeychainCheckBox.isSelected

    fun getNotifyOnThreshold(): Boolean = notifyOnThresholdCheckBox.isSelected

    fun getSoundOnCreditsSwitch(): Boolean = soundOnCreditsSwitchCheckBox.isSelected

    fun getProxyUrl(): String = proxyUrlField.text

    fun setQuotaTier(tier: String) {
        quotaTierComboBox.selectedItem = tier
    }

    fun setShowClaudeWidget(value: Boolean) {
        showClaudeWidgetCheckBox.isSelected = value
    }

    fun setShowCodexWidget(value: Boolean) {
        showCodexWidgetCheckBox.isSelected = value
    }

    fun setCodexExecutablePath(value: String) {
        codexExecutablePathField.text = value
    }

    fun setYellowThreshold(value: Int) {
        yellowThresholdField.value = value
    }

    fun setRedThreshold(value: Int) {
        redThresholdField.value = value
    }

    fun setCredentialsPath(path: String) {
        credentialsPathField.text = path
    }

    fun setRefreshIntervalMinutes(value: Int) {
        refreshIntervalField.value = value
    }

    fun setUseMacKeychain(value: Boolean) {
        useMacKeychainCheckBox.isSelected = value
    }

    fun setNotifyOnThreshold(value: Boolean) {
        notifyOnThresholdCheckBox.isSelected = value
    }

    fun setSoundOnCreditsSwitch(value: Boolean) {
        soundOnCreditsSwitchCheckBox.isSelected = value
    }

    fun setProxyUrl(value: String) {
        proxyUrlField.text = value
    }

    fun refreshProxyDetectedLabel() {
        val detected = ClaudeProxyResolver.autoDetectedProxyUrl()
        proxyDetectedLabel.text = if (detected != null) {
            // An auto-detected URL can carry credentials; never render them on screen.
            "Auto-detected: ${Secrets.redactUrlCredentials(detected)}"
        } else {
            "No proxy auto-detected (checks ~/.claude/settings.json, then HTTPS_PROXY/HTTP_PROXY)"
        }
    }

    private fun refreshCodexExecutableStatus() {
        val configuredPath = getCodexExecutablePath().trim()
        val resolved = runCatching { resolveCodexExecutable(configuredPath) }.getOrNull()
        codexExecutableStatusLabel.text = when {
            resolved != null && configuredPath.isEmpty() -> "Auto-detected: $resolved"
            resolved != null -> "Configured: $resolved"
            configuredPath.isEmpty() -> "Codex CLI was not found in PATH or a common install directory"
            else -> "Configured Codex executable was not found or is not executable"
        }
    }

    private fun resetToDefaults() {
        setShowClaudeWidget(ClaudeUsageSettings.DEFAULT_SHOW_CLAUDE_WIDGET)
        setShowCodexWidget(ClaudeUsageSettings.DEFAULT_SHOW_CODEX_WIDGET)
        setCodexExecutablePath(ClaudeUsageSettings.DEFAULT_CODEX_EXECUTABLE_PATH)
        setQuotaTier(ClaudeUsageSettings.DEFAULT_QUOTA_TIER)
        setYellowThreshold(ClaudeUsageSettings.DEFAULT_YELLOW_THRESHOLD)
        setRedThreshold(ClaudeUsageSettings.DEFAULT_RED_THRESHOLD)
        setCredentialsPath(ClaudeUsageSettings.DEFAULT_CREDENTIALS_PATH)
        setRefreshIntervalMinutes(ClaudeUsageSettings.DEFAULT_REFRESH_INTERVAL_MINUTES)
        setUseMacKeychain(ClaudeUsageSettings.DEFAULT_USE_MAC_KEYCHAIN)
        setNotifyOnThreshold(ClaudeUsageSettings.DEFAULT_NOTIFY_ON_THRESHOLD)
        setSoundOnCreditsSwitch(ClaudeUsageSettings.DEFAULT_SOUND_ON_CREDITS_SWITCH)
        setProxyUrl(ClaudeUsageSettings.DEFAULT_PROXY_URL)
    }
}
