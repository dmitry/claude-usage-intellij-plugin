package com.github.dmitry.claudeusage

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.FlowLayout
import javax.swing.*

class ClaudeUsageSettingsComponent {

    private val mainPanel: JPanel
    private val quotaTierComboBox = JComboBox(ClaudeUsageSettings.QUOTA_TIER_OPTIONS)
    private val yellowThresholdField = JSpinner(SpinnerNumberModel(70, 0, 100, 1))
    private val redThresholdField = JSpinner(SpinnerNumberModel(90, 0, 100, 1))
    private val credentialsPathField = JBTextField()
    private val resetButton = JButton("Reset to Defaults")

    init {
        val resetPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            add(resetButton)
        }

        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Status bar quota display:"), quotaTierComboBox, 1, false)
            .addLabeledComponent(JBLabel("Yellow warning threshold (%):"), yellowThresholdField, 1, false)
            .addLabeledComponent(JBLabel("Red warning threshold (%):"), redThresholdField, 1, false)
            .addLabeledComponent(JBLabel("Credentials file path:"), credentialsPathField, 1, false)
            .addSeparator()
            .addComponent(resetPanel)
            .addComponentFillVertically(JPanel(), 0)
            .getPanel()

        resetButton.addActionListener {
            resetToDefaults()
        }
    }

    fun getPanel(): JPanel = mainPanel

    fun getPreferredFocusedComponent(): JComponent = quotaTierComboBox

    fun getQuotaTier(): String = quotaTierComboBox.selectedItem as String

    fun getYellowThreshold(): Int = yellowThresholdField.value as Int

    fun getRedThreshold(): Int = redThresholdField.value as Int

    fun getCredentialsPath(): String = credentialsPathField.text

    fun setQuotaTier(tier: String) {
        quotaTierComboBox.selectedItem = tier
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

    private fun resetToDefaults() {
        setQuotaTier(ClaudeUsageSettings.DEFAULT_QUOTA_TIER)
        setYellowThreshold(ClaudeUsageSettings.DEFAULT_YELLOW_THRESHOLD)
        setRedThreshold(ClaudeUsageSettings.DEFAULT_RED_THRESHOLD)
        setCredentialsPath(ClaudeUsageSettings.DEFAULT_CREDENTIALS_PATH)
    }
}
