package com.github.dmitry.claudeusage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant

@Serializable
data class UsageResponse(
    @SerialName("five_hour") val fiveHour: UsageQuota? = null,
    @SerialName("seven_day") val sevenDay: UsageQuota? = null,
    @SerialName("seven_day_sonnet") val sevenDaySonnet: UsageQuota? = null,
    @SerialName("extra_usage") val extraUsage: ExtraUsage? = null,
    // Kept as raw JSON: the entries vary by kind, and an unexpected shape must not
    // fail parsing of the whole response.
    val limits: JsonArray? = null
) {
    // Per-model weekly windows (e.g. Fable) have no dedicated top-level field; the API
    // only reports them as "weekly_scoped" entries of the limits[] array.
    val sevenDayFable: UsageQuota?
        get() = weeklyScopedQuota("Fable")

    fun quotaForTier(tier: String): UsageQuota? = when (tier) {
        "7-day" -> sevenDay
        "7-day Sonnet" -> sevenDaySonnet
        "7-day Fable" -> sevenDayFable
        else -> fiveHour
    }

    private fun weeklyScopedQuota(modelName: String): UsageQuota? {
        val entry = limits.orEmpty()
            .mapNotNull { it as? JsonObject }
            .firstOrNull { limit ->
                val model = (limit["scope"] as? JsonObject)?.get("model") as? JsonObject
                limit.stringOrNull("kind") == "weekly_scoped" &&
                    model?.stringOrNull("display_name").equals(modelName, ignoreCase = true)
            } ?: return null
        val percent = (entry["percent"] as? JsonPrimitive)?.doubleOrNull ?: return null
        return UsageQuota(percent, resetsAtToIso(entry["resets_at"]))
    }
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

// resets_at in limits[] is normally an ISO timestamp; epoch seconds are accepted too,
// matching how the Claude Code CLI reads this field.
private fun resetsAtToIso(element: JsonElement?): String? {
    val primitive = element as? JsonPrimitive ?: return null
    if (primitive.isString) return primitive.content
    return primitive.doubleOrNull?.let { Instant.ofEpochSecond(it.toLong()).toString() }
}

@Serializable
data class UsageQuota(
    val utilization: Double,
    @SerialName("resets_at") val resetsAt: String? = null
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

@Serializable
data class ClaudeSettingsFile(
    val env: Map<String, String>? = null
)
