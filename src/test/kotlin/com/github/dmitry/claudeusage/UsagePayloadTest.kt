package com.github.dmitry.claudeusage

import kotlinx.serialization.json.Json
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Golden test against a real `GET /api/oauth/usage` response (captured 2026-08-04, Team plan, CAD).
 *
 * The client decodes with `ignoreUnknownKeys = true`, so a field the model does not name is dropped
 * in silence - which is how the model-scoped weekly limit and the credit figures went missing. This
 * pins the decode against an actual payload rather than against the model's own assumptions.
 */
class UsagePayloadTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val payload = """
        {"five_hour":{"utilization":29.0,"resets_at":"2026-08-05T00:49:59.690395+00:00","limit_dollars":null,"used_dollars":null,"remaining_dollars":null},
        "seven_day":{"utilization":16.0,"resets_at":"2026-08-07T22:59:59.690427+00:00","limit_dollars":null,"used_dollars":null,"remaining_dollars":null},
        "seven_day_oauth_apps":null,"seven_day_opus":null,"seven_day_sonnet":null,"seven_day_cowork":null,"seven_day_omelette":null,
        "tangelo":null,"iguana_necktie":null,"omelette_promotional":null,"nimbus_quill":null,"cinder_cove":null,"amber_ladder":null,
        "extra_usage":{"is_enabled":true,"monthly_limit":100000,"used_credits":0.0,"utilization":null,"currency":"CAD","decimal_places":2,
        "disabled_reason":null,"user_disabled":false,"spend_limit_reached":false,"credits_ever_enabled":true,"daily":null,"weekly":null},
        "limits":[{"kind":"session","group":"session","percent":29,"severity":"normal","resets_at":"2026-08-05T00:49:59.690395+00:00","scope":null,"is_active":true},
        {"kind":"weekly_all","group":"weekly","percent":16,"severity":"normal","resets_at":"2026-08-07T22:59:59.690427+00:00","scope":null,"is_active":false},
        {"kind":"weekly_scoped","group":"weekly","percent":8,"severity":"normal","resets_at":"2026-08-07T22:59:59.690792+00:00",
        "scope":{"model":{"id":null,"display_name":"Fable"},"surface":null},"is_active":false}],
        "spend":{"used":{"amount_minor":0,"currency":"CAD","exponent":2},"limit":{"amount_minor":100000,"currency":"CAD","exponent":2},
        "percent":0,"severity":"normal","enabled":true,"disabled_reason":null,
        "cap":{"money":{"amount_minor":100000,"currency":"CAD","exponent":2},"credits":null},"balance":null,"auto_reload":null,
        "can_purchase_credits":false,"can_toggle":false},"member_dashboard_available":false}
    """.trimIndent()

    private fun decode(): UsageResponse = json.decodeFromString(payload)

    @Test
    fun `the real payload decodes without throwing`() {
        val usage = decode()

        assertEquals(3, usage.limits?.size)
        assertTrue(usage.creditsEnabled())
    }

    @Test
    fun `the popup renders the same three rows as the official usage view`() {
        val rows = decode().displayRows()

        assertEquals(listOf("5-hour", "Weekly · all models", "Weekly · Fable"), rows.map { it.label })
        assertEquals(listOf(29, 16, 8), rows.map { it.percent.toInt() })
    }

    @Test
    fun `the Fable weekly limit is no longer dropped`() {
        // It exists only inside `limits`; every named seven_day_* field in this payload is null.
        val usage = decode()

        assertNull(usage.sevenDaySonnet)
        assertEquals(8.0, usage.utilizationForTier(QuotaTiers.WEEKLY_SCOPED))
        assertEquals("Weekly · Fable", usage.limitOfKind(LimitKinds.WEEKLY_SCOPED)?.displayLabel())
    }

    @Test
    fun `credits render exactly as the official view shows them`() {
        val usage = decode()

        assertEquals(
            "$0.00 of $1,000.00",
            CreditsFormatter.long(
                usage.creditsUsedMajor(),
                usage.creditsLimitMajor(),
                usage.creditsCurrency(),
                Locale.CANADA
            )
        )
    }

    @Test
    fun `the credit limit is read as minor units, not dollars`() {
        // monthly_limit is 100000 for a $1,000.00 cap. Treating it as dollars would show $100,000.00.
        val usage = decode()

        assertEquals(1000.0, usage.creditsLimitMajor())
        assertEquals(0.0, usage.creditsUsedMajor())
        assertEquals("CAD", usage.creditsCurrency())
    }

    @Test
    fun `the legacy extra_usage block agrees with spend`() {
        // Both are present here, so the fallback path can be checked against the authoritative one.
        val usage = decode()
        val extra = usage.extraUsage!!

        assertEquals(usage.creditsLimitMajor(), extra.limitMajor())
        assertEquals(usage.creditsUsedMajor(), extra.usedMajor())
    }

    @Test
    fun `the five-hour and weekly tiers resolve from limits, not the legacy fields`() {
        val usage = decode()

        assertEquals(29.0, usage.utilizationForTier(QuotaTiers.FIVE_HOUR))
        assertEquals(16.0, usage.utilizationForTier(QuotaTiers.SEVEN_DAY))
        assertEquals("2026-08-05T00:49:59.690395+00:00", usage.resetsAtForTier(QuotaTiers.FIVE_HOUR))
    }
}
