package com.github.dmitry.claudeusage

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class ClaudeUsageSettingsConfigurable : Configurable {

    private var settingsComponent: ClaudeUsageSettingsComponent? = null

    override fun getDisplayName(): String = "Claude Code Usage"

    override fun getPreferredFocusedComponent(): JComponent? =
        settingsComponent?.getPreferredFocusedComponent()

    override fun createComponent(): JComponent? {
        settingsComponent = ClaudeUsageSettingsComponent()
        return settingsComponent?.getPanel()
    }

    override fun isModified(): Boolean {
        val state = ClaudeUsageSettings.getInstance().state
        val component = settingsComponent ?: return false
        return component.getQuotaTier() != state.statusBarQuotaTier ||
            component.getYellowThreshold() != state.yellowThreshold ||
            component.getRedThreshold() != state.redThreshold ||
            component.getCredentialsPath() != state.credentialsFilePath ||
            component.getRefreshIntervalMinutes() != state.refreshIntervalMinutes ||
            component.getUseMacKeychain() != state.useMacKeychain
    }

    override fun apply() {
        val state = ClaudeUsageSettings.getInstance().state
        val component = settingsComponent ?: return
        state.statusBarQuotaTier = component.getQuotaTier()
        state.yellowThreshold = component.getYellowThreshold()
        state.redThreshold = component.getRedThreshold()
        state.credentialsFilePath = component.getCredentialsPath()
        state.refreshIntervalMinutes = component.getRefreshIntervalMinutes()
        state.useMacKeychain = component.getUseMacKeychain()
    }

    override fun reset() {
        val state = ClaudeUsageSettings.getInstance().state
        val component = settingsComponent ?: return
        component.setQuotaTier(state.statusBarQuotaTier)
        component.setYellowThreshold(state.yellowThreshold)
        component.setRedThreshold(state.redThreshold)
        component.setCredentialsPath(state.credentialsFilePath)
        component.setRefreshIntervalMinutes(state.refreshIntervalMinutes)
        component.setUseMacKeychain(state.useMacKeychain)
    }

    override fun disposeUIResources() {
        settingsComponent = null
    }
}
