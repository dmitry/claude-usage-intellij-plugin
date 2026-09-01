package com.github.h2a.claudeusage

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.ProjectManager
import javax.swing.JComponent

class ClaudeUsageSettingsConfigurable : Configurable {

    private var settingsComponent: ClaudeUsageSettingsComponent? = null

    override fun getDisplayName(): String = "Claude & Codex Usage"

    override fun getPreferredFocusedComponent(): JComponent? =
        settingsComponent?.getPreferredFocusedComponent()

    override fun createComponent(): JComponent? {
        settingsComponent = ClaudeUsageSettingsComponent()
        return settingsComponent?.getPanel()
    }

    override fun isModified(): Boolean {
        val state = ClaudeUsageSettings.getInstance().state
        val component = settingsComponent ?: return false
        return component.getShowClaudeWidget() != state.showClaudeWidget ||
            component.getShowCodexWidget() != state.showCodexWidget ||
            component.getCodexExecutablePath() != state.codexExecutablePath ||
            component.getQuotaTier() != state.statusBarQuotaTier ||
            component.getYellowThreshold() != state.yellowThreshold ||
            component.getRedThreshold() != state.redThreshold ||
            component.getCredentialsPath() != state.credentialsFilePath ||
            component.getRefreshIntervalMinutes() != state.refreshIntervalMinutes ||
            component.getUseMacKeychain() != state.useMacKeychain ||
            component.getNotifyOnThreshold() != state.notifyOnThreshold ||
            component.getSoundOnCreditsSwitch() != state.soundOnCreditsSwitch ||
            component.getProxyUrl() != state.proxyUrl
    }

    override fun apply() {
        val state = ClaudeUsageSettings.getInstance().state
        val component = settingsComponent ?: return
        val widgetVisibilityChanged = component.getShowClaudeWidget() != state.showClaudeWidget ||
            component.getShowCodexWidget() != state.showCodexWidget

        val proxyUrl = component.getProxyUrl().trim()
        if (!ClaudeProxyResolver.isValidProxyUrl(proxyUrl)) {
            throw ConfigurationException(
                "Invalid proxy URL. Expected host:port, http://host:port, socks5://host:port, or 'none' to disable."
            )
        }

        // The proxy password goes to the IDE password safe; ClaudeCodeUsage.xml keeps only
        // scheme/host/port plus the non-secret username. Upstream wrote the whole
        // "user:password@host" string there in plain text.
        val previous = ProxyUrlParser.parse(state.proxyUrl)
        val parsed = ProxyUrlParser.parse(proxyUrl)
        val username = parsed?.username
        val password = parsed?.password
        if (parsed?.type == ProxyUrlParser.Type.HTTP && username != null && password != null) {
            ProxyCredentialStore.savePassword(parsed.host, parsed.port, username, password)
        }
        // Drop a stored password as soon as its endpoint or username stops applying, so it is never
        // carried over to a proxy it was not entered for.
        val endpointChanged = parsed == null || previous?.host != parsed.host || previous.port != parsed.port
        if (previous != null && (endpointChanged || username == null)) {
            ProxyCredentialStore.clear(previous.host, previous.port)
        }
        val sanitizedProxyUrl = ProxyUrlParser.stripPassword(proxyUrl)
        // Mirror the sanitized value back into the field so isModified() settles.
        component.setProxyUrl(sanitizedProxyUrl)

        state.showClaudeWidget = component.getShowClaudeWidget()
        state.showCodexWidget = component.getShowCodexWidget()
        state.codexExecutablePath = component.getCodexExecutablePath().trim()
        state.statusBarQuotaTier = component.getQuotaTier()
        state.yellowThreshold = component.getYellowThreshold()
        state.redThreshold = component.getRedThreshold()
        state.credentialsFilePath = component.getCredentialsPath()
        state.refreshIntervalMinutes = component.getRefreshIntervalMinutes()
        state.useMacKeychain = component.getUseMacKeychain()
        state.notifyOnThreshold = component.getNotifyOnThreshold()
        state.soundOnCreditsSwitch = component.getSoundOnCreditsSwitch()
        state.proxyUrl = sanitizedProxyUrl
        component.refreshProxyDetectedLabel()

        if (widgetVisibilityChanged) {
            ApplicationManager.getApplication().invokeLater {
                ProjectManager.getInstance().openProjects.forEach { project ->
                    UsageWidgetVisibilityUpdater.update(project)
                }
            }
        }
        if (state.showCodexWidget) {
            ApplicationManager.getApplication().executeOnPooledThread {
                CodexUsageService.getInstance().refreshUsage()
            }
        }
    }

    override fun reset() {
        val state = ClaudeUsageSettings.getInstance().state
        val component = settingsComponent ?: return
        component.setShowClaudeWidget(state.showClaudeWidget)
        component.setShowCodexWidget(state.showCodexWidget)
        component.setCodexExecutablePath(state.codexExecutablePath)
        component.setQuotaTier(state.statusBarQuotaTier)
        component.setYellowThreshold(state.yellowThreshold)
        component.setRedThreshold(state.redThreshold)
        component.setCredentialsPath(state.credentialsFilePath)
        component.setRefreshIntervalMinutes(state.refreshIntervalMinutes)
        component.setUseMacKeychain(state.useMacKeychain)
        component.setNotifyOnThreshold(state.notifyOnThreshold)
        component.setSoundOnCreditsSwitch(state.soundOnCreditsSwitch)
        component.setProxyUrl(state.proxyUrl)
        component.refreshProxyDetectedLabel()
    }

    override fun disposeUIResources() {
        settingsComponent = null
    }
}
