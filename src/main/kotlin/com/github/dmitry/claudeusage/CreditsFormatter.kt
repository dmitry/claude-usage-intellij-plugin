package com.github.h2a.claudeusage

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Formats the extra-usage credit figures the API already returns but that upstream never displayed.
 *
 * Amounts are taken in whole currency units - see [ExtraUsage] on the payload's units.
 *
 * Platform-free so it stays unit-testable without a running IDE.
 */
object CreditsFormatter {

    /** `$0.00 of $1,000.00` for the detail popup, or null when there is nothing to show. */
    fun long(used: Double?, limit: Double?, currency: String? = null, locale: Locale = Locale.getDefault()): String? =
        render(used, limit, currency, locale, separator = "of")

    /** `$0.00 / $1,000.00` for the status bar, where horizontal space is scarce. */
    fun compact(used: Double?, limit: Double?, currency: String? = null, locale: Locale = Locale.getDefault()): String? =
        render(used, limit, currency, locale, separator = "/")

    private fun render(used: Double?, limit: Double?, currency: String?, locale: Locale, separator: String): String? {
        val format = currencyFormat(currency, locale)
        return when {
            used != null && limit != null -> "${format.format(used)} $separator ${format.format(limit)}"
            used != null -> "${format.format(used)} used"
            limit != null -> "${format.format(limit)} available"
            else -> null
        }
    }

    /**
     * The viewer's own locale decides the symbol and grouping, with the payload's currency swapped in.
     * That is what makes a CAD account read `$1,000.00` on a Canadian machine - matching the official
     * usage view - rather than the `CA$1,000.00` a fixed US locale would produce. An unrecognised code
     * falls back to the locale default instead of throwing.
     */
    private fun currencyFormat(currency: String?, locale: Locale): NumberFormat {
        val format = NumberFormat.getCurrencyInstance(locale)
        val code = currency?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return format
        runCatching { format.currency = Currency.getInstance(code) }
        return format
    }
}
