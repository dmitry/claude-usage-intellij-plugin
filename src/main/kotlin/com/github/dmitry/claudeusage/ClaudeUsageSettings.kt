package com.github.h2a.claudeusage

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(
    name = "com.github.h2a.claudeusage.ClaudeUsageSettings",
    storages = [Storage("ClaudeCodeUsage.xml")]
)
class ClaudeUsageSettings : PersistentStateComponent<ClaudeUsageSettings.State> {

    data class State(
        var statusBarQuotaTier: String = DEFAULT_QUOTA_TIER,
        var yellowThreshold: Int = DEFAULT_YELLOW_THRESHOLD,
        var redThreshold: Int = DEFAULT_RED_THRESHOLD,
        var credentialsFilePath: String = DEFAULT_CREDENTIALS_PATH,
        var refreshIntervalMinutes: Int = DEFAULT_REFRESH_INTERVAL_MINUTES,
        var useMacKeychain: Boolean = DEFAULT_USE_MAC_KEYCHAIN,
        var notifyOnThreshold: Boolean = DEFAULT_NOTIFY_ON_THRESHOLD,
        var soundOnCreditsSwitch: Boolean = DEFAULT_SOUND_ON_CREDITS_SWITCH,
        var proxyUrl: String = DEFAULT_PROXY_URL,
        var showClaudeWidget: Boolean = DEFAULT_SHOW_CLAUDE_WIDGET,
        var showCodexWidget: Boolean = DEFAULT_SHOW_CODEX_WIDGET,
        var codexExecutablePath: String = DEFAULT_CODEX_EXECUTABLE_PATH
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        const val DEFAULT_QUOTA_TIER = QuotaTiers.FIVE_HOUR
        const val DEFAULT_YELLOW_THRESHOLD = 70
        const val DEFAULT_RED_THRESHOLD = 90
        const val DEFAULT_CREDENTIALS_PATH = "~/.claude/.credentials.json"
        const val DEFAULT_REFRESH_INTERVAL_MINUTES = 1
        const val DEFAULT_USE_MAC_KEYCHAIN = true
        const val DEFAULT_NOTIFY_ON_THRESHOLD = true
        const val DEFAULT_SOUND_ON_CREDITS_SWITCH = true
        const val DEFAULT_PROXY_URL = ""
        const val DEFAULT_SHOW_CLAUDE_WIDGET = true
        const val DEFAULT_SHOW_CODEX_WIDGET = false
        const val DEFAULT_CODEX_EXECUTABLE_PATH = ""

        val QUOTA_TIER_OPTIONS: Array<String> = QuotaTiers.ALL.toTypedArray()

        fun getInstance(): ClaudeUsageSettings =
            ApplicationManager.getApplication().getService(ClaudeUsageSettings::class.java)
    }
}
