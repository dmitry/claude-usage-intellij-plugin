package com.github.dmitry.claudeusage

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(
    name = "com.github.dmitry.claudeusage.ClaudeUsageSettings",
    storages = [Storage("ClaudeCodeUsage.xml")]
)
class ClaudeUsageSettings : PersistentStateComponent<ClaudeUsageSettings.State> {

    data class State(
        var statusBarQuotaTier: String = DEFAULT_QUOTA_TIER,
        var yellowThreshold: Int = DEFAULT_YELLOW_THRESHOLD,
        var redThreshold: Int = DEFAULT_RED_THRESHOLD,
        var credentialsFilePath: String = DEFAULT_CREDENTIALS_PATH,
        var refreshIntervalMinutes: Int = DEFAULT_REFRESH_INTERVAL_MINUTES
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        const val DEFAULT_QUOTA_TIER = "5-hour"
        const val DEFAULT_YELLOW_THRESHOLD = 70
        const val DEFAULT_RED_THRESHOLD = 90
        const val DEFAULT_CREDENTIALS_PATH = "~/.claude/.credentials.json"
        const val DEFAULT_REFRESH_INTERVAL_MINUTES = 1

        val QUOTA_TIER_OPTIONS = arrayOf("5-hour", "7-day", "7-day Sonnet")

        fun getInstance(): ClaudeUsageSettings =
            ApplicationManager.getApplication().getService(ClaudeUsageSettings::class.java)
    }
}
