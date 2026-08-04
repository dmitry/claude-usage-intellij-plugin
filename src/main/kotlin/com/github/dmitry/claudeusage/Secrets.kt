package com.github.dmitry.claudeusage

/**
 * Keeps secrets out of the IDE log and the settings UI.
 *
 * Two upstream log statements interpolated exception messages that can embed the very data being
 * parsed: kotlinx.serialization's `JsonDecodingException` appends a `JSON input: ...` excerpt, and
 * `java.net.URISyntaxException` echoes the offending URL. The inputs there are the Claude
 * credentials blob and the proxy URL, so those messages could write an OAuth access token or a
 * proxy password into `idea.log` - a file that routinely gets attached to bug reports.
 *
 * Deliberately free of IntelliJ imports so it stays unit-testable without a running IDE.
 */
object Secrets {
    private const val MASK = "***"

    /**
     * A `user:password@` segment, with or without a leading scheme, so both `http://u:p@host:3128`
     * and the scheme-less `u:p@host:3128` form the parser accepts are covered.
     */
    private val URL_CREDENTIALS = Regex("""([a-zA-Z][a-zA-Z0-9+.\-]*://)?([^/@\s:]+(?::[^/@\s]*)?)@""")

    /**
     * The exception's identity without its message, for cases where the message may quote the
     * input. Use this instead of `e.message` whenever the parsed input could hold a secret.
     */
    fun safeMessage(e: Throwable): String = e.javaClass.simpleName

    /** Replaces the credentials segment of any URL in [text] with a mask. */
    fun redactUrlCredentials(text: String): String =
        URL_CREDENTIALS.replace(text) { "${it.groupValues[1]}$MASK@" }
}
