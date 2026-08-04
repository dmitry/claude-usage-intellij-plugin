package com.github.dmitry.claudeusage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.pow

@Serializable
data class UsageResponse(
    @SerialName("five_hour") val fiveHour: UsageQuota? = null,
    @SerialName("seven_day") val sevenDay: UsageQuota? = null,
    @SerialName("seven_day_sonnet") val sevenDaySonnet: UsageQuota? = null,
    @SerialName("extra_usage") val extraUsage: ExtraUsage? = null,
    /**
     * The API's self-describing limit list. It supersedes the named `five_hour` / `seven_day_*`
     * fields (which come back null on newer accounts) and is the *only* place a model-scoped weekly
     * limit appears, so rows render from here whenever it is present.
     */
    val limits: List<UsageLimit>? = null,
    /** The modern credit representation: explicit minor units, currency and exponent. */
    val spend: Spend? = null
) {
    /** Legacy named accessor, kept for the fallback path when [limits] is absent. */
    fun quotaForTier(tier: String): UsageQuota? = when (tier) {
        QuotaTiers.SEVEN_DAY -> sevenDay
        QuotaTiers.SEVEN_DAY_SONNET -> sevenDaySonnet
        QuotaTiers.CREDITS, QuotaTiers.WEEKLY_SCOPED -> null
        else -> fiveHour
    }

    fun limitOfKind(kind: String): UsageLimit? = limits?.firstOrNull { it.kind == kind && it.percent != null }

    /** Rows for the detail popup: the self-describing list when present, else the legacy fields. */
    fun displayRows(): List<UsageRow> {
        val fromLimits = limits.orEmpty().mapNotNull { limit ->
            limit.percent?.let { UsageRow(limit.displayLabel(), it, limit.resetsAt) }
        }
        if (fromLimits.isNotEmpty()) return fromLimits

        return listOfNotNull(
            fiveHour?.let { UsageRow(QuotaTiers.FIVE_HOUR, it.utilization, it.resetsAt) },
            sevenDay?.let { UsageRow(QuotaTiers.SEVEN_DAY, it.utilization, it.resetsAt) },
            sevenDaySonnet?.let { UsageRow(QuotaTiers.SEVEN_DAY_SONNET, it.utilization, it.resetsAt) }
        )
    }

    /**
     * Bar-fill percentage for [tier], covering credits too, or null when unknown. Shared by the
     * status bar and the threshold notifications so each tier is resolved in exactly one place.
     */
    fun utilizationForTier(tier: String): Double? = when (tier) {
        QuotaTiers.CREDITS -> creditsUtilization()
        QuotaTiers.WEEKLY_SCOPED -> limitOfKind(LimitKinds.WEEKLY_SCOPED)?.percent
        QuotaTiers.SEVEN_DAY -> limitOfKind(LimitKinds.WEEKLY_ALL)?.percent ?: sevenDay?.utilization
        QuotaTiers.SEVEN_DAY_SONNET -> sevenDaySonnet?.utilization
        else -> limitOfKind(LimitKinds.SESSION)?.percent ?: fiveHour?.utilization
    }

    /** Reset timestamp for [tier]. Credits accrue monthly with no reset window in the payload. */
    fun resetsAtForTier(tier: String): String? = when (tier) {
        QuotaTiers.CREDITS -> null
        QuotaTiers.WEEKLY_SCOPED -> limitOfKind(LimitKinds.WEEKLY_SCOPED)?.resetsAt
        QuotaTiers.SEVEN_DAY -> limitOfKind(LimitKinds.WEEKLY_ALL)?.resetsAt ?: sevenDay?.resetsAt
        QuotaTiers.SEVEN_DAY_SONNET -> sevenDaySonnet?.resetsAt
        else -> limitOfKind(LimitKinds.SESSION)?.resetsAt ?: fiveHour?.resetsAt
    }

    // Credits resolve from `spend` when present - it states its units explicitly - and fall back to
    // the older `extra_usage` block otherwise.

    fun creditsEnabled(): Boolean = spend?.enabled ?: extraUsage?.isEnabled ?: false

    fun creditsUsedMajor(): Double? = spend?.used?.toMajor() ?: extraUsage?.usedMajor()

    fun creditsLimitMajor(): Double? = spend?.limit?.toMajor() ?: extraUsage?.limitMajor()

    fun creditsCurrency(): String? =
        spend?.limit?.currency ?: spend?.used?.currency ?: extraUsage?.currency

    fun creditsUtilization(): Double? =
        if (!creditsEnabled()) null else spend?.percent ?: extraUsage?.utilizationOrDerived()
}

/** One rendered row: a label, a percentage, and an optional reset timestamp. */
data class UsageRow(val label: String, val percent: Double, val resetsAt: String?)

@Serializable
data class UsageQuota(
    val utilization: Double,
    @SerialName("resets_at") val resetsAt: String? = null
)

object LimitKinds {
    const val SESSION = "session"
    const val WEEKLY_ALL = "weekly_all"
    const val WEEKLY_SCOPED = "weekly_scoped"
}

@Serializable
data class UsageLimit(
    val kind: String? = null,
    val group: String? = null,
    val percent: Double? = null,
    @SerialName("resets_at") val resetsAt: String? = null,
    val scope: LimitScope? = null
) {
    /**
     * The row label. An unrecognised kind is humanised rather than dropped - silently discarding
     * anything the model did not name is exactly how the model-scoped weekly limit went missing.
     */
    fun displayLabel(): String = when (kind) {
        LimitKinds.SESSION -> QuotaTiers.FIVE_HOUR
        LimitKinds.WEEKLY_ALL -> "Weekly · all models"
        LimitKinds.WEEKLY_SCOPED -> scope?.model?.displayName?.let { "Weekly · $it" } ?: "Weekly · scoped"
        else -> kind?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Usage"
    }
}

@Serializable
data class LimitScope(
    val model: ScopedModel? = null,
    val surface: String? = null
)

@Serializable
data class ScopedModel(
    val id: String? = null,
    @SerialName("display_name") val displayName: String? = null
)

@Serializable
data class Spend(
    val used: Money? = null,
    val limit: Money? = null,
    val percent: Double? = null,
    val enabled: Boolean? = null
)

@Serializable
data class Money(
    @SerialName("amount_minor") val amountMinor: Long? = null,
    val currency: String? = null,
    val exponent: Int? = null
) {
    /** Whole currency units: 100000 minor units at exponent 2 is 1000.00. */
    fun toMajor(): Double? = amountMinor?.div(10.0.pow(exponent ?: DEFAULT_EXPONENT))

    private companion object {
        const val DEFAULT_EXPONENT = 2
    }
}

/**
 * The older credit block. `monthly_limit` is in **minor** units - a $1,000 cap arrives as `100000`
 * alongside `decimal_places: 2`. `used_credits` is assumed to match; it was 0 in every payload
 * observed, so its scale is unconfirmed. Prefer [Spend], which states its units explicitly.
 */
@Serializable
data class ExtraUsage(
    @SerialName("is_enabled") val isEnabled: Boolean,
    @SerialName("monthly_limit") val monthlyLimit: Double? = null,
    @SerialName("used_credits") val usedCredits: Double? = null,
    val utilization: Double? = null,
    val currency: String? = null,
    @SerialName("decimal_places") val decimalPlaces: Int? = null
) {
    fun limitMajor(): Double? = toMajor(monthlyLimit)

    fun usedMajor(): Double? = toMajor(usedCredits)

    private fun toMajor(minor: Double?): Double? = minor?.div(10.0.pow(decimalPlaces ?: 2))

    /**
     * [utilization] when supplied, otherwise derived from the credit figures - their shared units
     * cancel, so the ratio holds either way. Guards a zero or absent limit against dividing by zero.
     */
    fun utilizationOrDerived(): Double? = utilization
        ?: if (monthlyLimit != null && monthlyLimit > 0 && usedCredits != null) {
            usedCredits / monthlyLimit * 100
        } else {
            null
        }
}

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
) {
    // The generated data-class toString() prints both tokens verbatim; interpolating an instance
    // into any log statement would leak them. Non-secret fields stay visible for diagnostics.
    override fun toString(): String =
        "OAuthCredentials(accessToken=***, refreshToken=***, expiresAt=$expiresAt, " +
            "subscriptionType=$subscriptionType, rateLimitTier=$rateLimitTier)"
}

@Serializable
data class ClaudeSettingsFile(
    val env: Map<String, String>? = null
)
