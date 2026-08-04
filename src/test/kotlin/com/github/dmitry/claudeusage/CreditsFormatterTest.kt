package com.github.dmitry.claudeusage

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreditsFormatterTest {

    private val canada = Locale.CANADA
    private val us = Locale.US

    @Test
    fun `long form matches the official usage view`() {
        assertEquals("$0.00 of $1,000.00", CreditsFormatter.long(0.0, 1000.0, "CAD", canada))
        assertEquals("$0.00 of $1,000.00", CreditsFormatter.long(0.0, 1000.0, "USD", us))
    }

    @Test
    fun `compact form is used for the status bar`() {
        assertEquals("$0.00 / $1,000.00", CreditsFormatter.compact(0.0, 1000.0, "CAD", canada))
    }

    @Test
    fun `amounts are grouped and shown to two decimal places`() {
        assertEquals("$1,234,567.89 of $2,000,000.00", CreditsFormatter.long(1234567.89, 2000000.0, "CAD", canada))
        assertEquals("$12.30 of $1,000.00", CreditsFormatter.long(12.3, 1000.0, "CAD", canada))
    }

    @Test
    fun `a partial payload still renders what it has`() {
        assertEquals("$12.34 used", CreditsFormatter.long(12.34, null, "CAD", canada))
        assertEquals("$1,000.00 available", CreditsFormatter.long(null, 1000.0, "CAD", canada))
    }

    @Test
    fun `nothing to show renders nothing`() {
        assertNull(CreditsFormatter.long(null, null, "CAD", canada))
        assertNull(CreditsFormatter.compact(null, null, "CAD", canada))
    }

    @Test
    fun `a foreign currency is disambiguated rather than passed off as local`() {
        // A CAD account viewed on a US machine should not silently read as US dollars.
        val onUsMachine = CreditsFormatter.long(0.0, 1000.0, "CAD", us)

        assertTrue(onUsMachine.orEmpty().contains("1,000.00"), "the amount is still grouped: $onUsMachine")
        assertTrue(
            onUsMachine != CreditsFormatter.long(0.0, 1000.0, "USD", us),
            "CAD must not render identically to USD in the same locale"
        )
    }

    @Test
    fun `an unusable currency code falls back instead of throwing`() {
        val localeDefault = CreditsFormatter.long(0.0, 1000.0, null, canada)

        listOf("", "   ", "NOTACURRENCY").forEach { code ->
            assertEquals(localeDefault, CreditsFormatter.long(0.0, 1000.0, code, canada), "'$code' should fall back")
        }
    }

    @Test
    fun `currency codes are case-insensitive`() {
        assertEquals(
            CreditsFormatter.long(0.0, 1000.0, "CAD", canada),
            CreditsFormatter.long(0.0, 1000.0, "cad", canada)
        )
    }
}
