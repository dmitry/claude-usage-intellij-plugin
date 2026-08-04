package com.github.dmitry.claudeusage

/**
 * The single source of truth for status-bar tier ids.
 *
 * These strings were previously repeated across [UsageResponse.quotaForTier],
 * `ClaudeUsageSettings.QUOTA_TIER_OPTIONS` and the widget's row list - three places to keep in step
 * every time the API grows a tier, which is how a tier ends up silently missing from the UI.
 *
 * They are persisted in `ClaudeCodeUsage.xml` as `statusBarQuotaTier`, so each one is an id as well
 * as a label: renaming a value makes anyone who selected it fall back to [FIVE_HOUR]. Only ever add.
 */
object QuotaTiers {
    const val FIVE_HOUR = "5-hour"
    const val SEVEN_DAY = "7-day"

    /**
     * The model-scoped weekly limit (rendered "Weekly · Fable" and friends). Its id is static because
     * it is persisted, while the scoped model name it reports is dynamic - see
     * [UsageLimit.displayLabel].
     */
    const val WEEKLY_SCOPED = "Weekly (model-scoped)"

    /** Legacy: newer accounts return null for `seven_day_sonnet`. Kept so saved settings still load. */
    const val SEVEN_DAY_SONNET = "7-day Sonnet"

    const val CREDITS = "Usage credits"

    /** Order shown in the settings dropdown. */
    val ALL: List<String> = listOf(FIVE_HOUR, SEVEN_DAY, WEEKLY_SCOPED, SEVEN_DAY_SONNET, CREDITS)
}
