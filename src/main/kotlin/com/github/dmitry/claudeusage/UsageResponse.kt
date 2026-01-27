package com.github.dmitry.claudeusage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UsageResponse(
    @SerialName("five_hour") val fiveHour: UsageQuota? = null,
    @SerialName("seven_day") val sevenDay: UsageQuota? = null,
    @SerialName("seven_day_sonnet") val sevenDaySonnet: UsageQuota? = null,
    @SerialName("extra_usage") val extraUsage: ExtraUsage? = null
)

@Serializable
data class UsageQuota(
    val utilization: Double,
    @SerialName("resets_at") val resetsAt: String
)

@Serializable
data class ExtraUsage(
    @SerialName("is_enabled") val isEnabled: Boolean,
    @SerialName("monthly_limit") val monthlyLimit: Double? = null,
    @SerialName("used_credits") val usedCredits: Double? = null,
    val utilization: Double? = null
)

@Serializable
data class CredentialsFile(
    val claudeAiOauth: OAuthCredentials? = null
)

@Serializable
data class OAuthCredentials(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAt: Long? = null,
    val subscriptionType: String? = null,
    val rateLimitTier: String? = null
)
