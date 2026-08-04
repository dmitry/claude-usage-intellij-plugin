package com.github.dmitry.claudeusage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class UsageResponseTest {

    private val fiveHour = UsageQuota(utilization = 21.0, resetsAt = "2026-08-05T00:49:59+00:00")
    private val sevenDay = UsageQuota(utilization = 15.0, resetsAt = "2026-08-07T22:59:59+00:00")
    private val sevenDaySonnet = UsageQuota(utilization = 8.0, resetsAt = "2026-08-07T22:58:00+00:00")

    /** A modern payload: self-describing limits plus a `spend` block, no named quota fields. */
    private fun modern() = UsageResponse(
        limits = listOf(
            UsageLimit(kind = LimitKinds.SESSION, percent = 29.0, resetsAt = "2026-08-05T00:49:59+00:00"),
            UsageLimit(kind = LimitKinds.WEEKLY_ALL, percent = 16.0, resetsAt = "2026-08-07T22:59:59+00:00"),
            UsageLimit(
                kind = LimitKinds.WEEKLY_SCOPED,
                percent = 8.0,
                resetsAt = "2026-08-07T22:59:59+00:00",
                scope = LimitScope(model = ScopedModel(displayName = "Fable"))
            )
        ),
        spend = Spend(
            used = Money(amountMinor = 0, currency = "CAD", exponent = 2),
            limit = Money(amountMinor = 100000, currency = "CAD", exponent = 2),
            percent = 0.0,
            enabled = true
        )
    )

    /** An older payload: named quota fields and the `extra_usage` block only. */
    private fun legacy(extra: ExtraUsage? = null) = UsageResponse(
        fiveHour = fiveHour,
        sevenDay = sevenDay,
        sevenDaySonnet = sevenDaySonnet,
        extraUsage = extra
    )

    @Test
    fun `modern rows come from the self-describing limit list`() {
        val rows = modern().displayRows()

        assertEquals(listOf("5-hour", "Weekly · all models", "Weekly · Fable"), rows.map { it.label })
        assertEquals(listOf(29.0, 16.0, 8.0), rows.map { it.percent })
    }

    @Test
    fun `rows fall back to the named fields when there is no limit list`() {
        val rows = legacy().displayRows()

        assertEquals(listOf("5-hour", "7-day", "7-day Sonnet"), rows.map { it.label })
        assertEquals(listOf(21.0, 15.0, 8.0), rows.map { it.percent })
    }

    @Test
    fun `an unrecognised limit kind is still rendered, not dropped`() {
        // The regression this whole change exists to prevent: the scoped weekly limit was invisible
        // because the model only named the kinds it already knew about.
        val rows = UsageResponse(
            limits = listOf(UsageLimit(kind = "monthly_experimental", percent = 42.0))
        ).displayRows()

        assertEquals(1, rows.size)
        assertEquals("Monthly experimental", rows.single().label)
        assertEquals(42.0, rows.single().percent)
    }

    @Test
    fun `a limit with no percentage is skipped`() {
        val rows = UsageResponse(limits = listOf(UsageLimit(kind = LimitKinds.SESSION, percent = null))).displayRows()

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `a scoped limit without a model name degrades gracefully`() {
        val label = UsageLimit(kind = LimitKinds.WEEKLY_SCOPED, percent = 8.0).displayLabel()

        assertEquals("Weekly · scoped", label)
    }

    @Test
    fun `tier resolution prefers the limit list and falls back to named fields`() {
        assertEquals(29.0, modern().utilizationForTier(QuotaTiers.FIVE_HOUR))
        assertEquals(16.0, modern().utilizationForTier(QuotaTiers.SEVEN_DAY))
        assertEquals(8.0, modern().utilizationForTier(QuotaTiers.WEEKLY_SCOPED))

        assertEquals(21.0, legacy().utilizationForTier(QuotaTiers.FIVE_HOUR))
        assertEquals(15.0, legacy().utilizationForTier(QuotaTiers.SEVEN_DAY))
        assertEquals(8.0, legacy().utilizationForTier(QuotaTiers.SEVEN_DAY_SONNET))
    }

    @Test
    fun `an unknown tier falls back to the five-hour quota`() {
        // Guards the persisted-settings path: a stale statusBarQuotaTier must still render.
        assertSame(fiveHour, legacy().quotaForTier("something-we-removed"))
        assertEquals(21.0, legacy().utilizationForTier("something-we-removed"))
    }

    @Test
    fun `credits read their money from spend, in whole currency units`() {
        val usage = modern()

        assertTrue(usage.creditsEnabled())
        assertEquals(0.0, usage.creditsUsedMajor())
        assertEquals(1000.0, usage.creditsLimitMajor(), "100000 minor units at exponent 2 is 1000.00")
        assertEquals("CAD", usage.creditsCurrency())
        assertEquals(0.0, usage.creditsUtilization())
    }

    @Test
    fun `credits fall back to the legacy block and honour its decimal places`() {
        val usage = legacy(
            ExtraUsage(
                isEnabled = true,
                monthlyLimit = 100000.0,
                usedCredits = 2550.0,
                currency = "CAD",
                decimalPlaces = 2
            )
        )

        assertEquals(1000.0, usage.creditsLimitMajor())
        assertEquals(25.5, usage.creditsUsedMajor())
        assertEquals("CAD", usage.creditsCurrency())
    }

    @Test
    fun `credits are absent when the account has them switched off`() {
        val off = legacy(ExtraUsage(isEnabled = false, monthlyLimit = 100000.0, usedCredits = 0.0))

        assertFalse(off.creditsEnabled())
        assertNull(off.creditsUtilization())
        assertNull(legacy().utilizationForTier(QuotaTiers.CREDITS))
    }

    @Test
    fun `credits have no reset window but quotas do`() {
        assertNull(modern().resetsAtForTier(QuotaTiers.CREDITS))
        assertEquals("2026-08-05T00:49:59+00:00", modern().resetsAtForTier(QuotaTiers.FIVE_HOUR))
        assertEquals(fiveHour.resetsAt, legacy().resetsAtForTier(QuotaTiers.FIVE_HOUR))
    }

    @Test
    fun `utilizationOrDerived prefers the reported utilization`() {
        val extra = ExtraUsage(isEnabled = true, monthlyLimit = 100000.0, usedCredits = 25000.0, utilization = 99.0)

        assertEquals(99.0, extra.utilizationOrDerived())
    }

    @Test
    fun `utilizationOrDerived derives a ratio whose units cancel`() {
        val extra = ExtraUsage(isEnabled = true, monthlyLimit = 100000.0, usedCredits = 25000.0)

        assertEquals(25.0, extra.utilizationOrDerived())
    }

    @Test
    fun `utilizationOrDerived never divides by zero or guesses`() {
        listOf(
            ExtraUsage(isEnabled = true, monthlyLimit = 0.0, usedCredits = 250.0),
            ExtraUsage(isEnabled = true, monthlyLimit = null, usedCredits = 250.0),
            ExtraUsage(isEnabled = true, monthlyLimit = 1000.0, usedCredits = null),
            ExtraUsage(isEnabled = true)
        ).forEach { extra ->
            assertNull(extra.utilizationOrDerived(), "should not derive a percentage from $extra")
        }
    }

    @Test
    fun `Money defaults to two decimal places when the exponent is missing`() {
        assertEquals(1000.0, Money(amountMinor = 100000).toMajor())
        assertEquals(1000.0, Money(amountMinor = 1000, exponent = 0).toMajor())
        assertNull(Money(amountMinor = null, exponent = 2).toMajor())
    }

    @Test
    fun `every dropdown option resolves against a modern payload`() {
        // A tier offered in settings that nothing resolves would render a blank widget.
        val usage = modern()
        val unresolved = QuotaTiers.ALL.filter { usage.utilizationForTier(it) == null }

        assertEquals(listOf(QuotaTiers.SEVEN_DAY_SONNET), unresolved, "only the legacy Sonnet tier may be empty")
    }
}
