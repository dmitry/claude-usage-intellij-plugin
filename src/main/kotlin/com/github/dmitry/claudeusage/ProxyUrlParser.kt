package com.github.dmitry.claudeusage

import java.net.URI

/**
 * Pure proxy-URL parsing, extracted from [ClaudeProxyResolver] so it carries no IntelliJ or
 * `java.net.Proxy` dependency and can be unit-tested without a running IDE.
 *
 * Parsing never logs: a malformed proxy URL may contain a password, and the exception message
 * from [URI] quotes its input. Callers decide what to log, via [Secrets.redactUrlCredentials].
 */
object ProxyUrlParser {
    enum class Type { HTTP, SOCKS }

    data class Parsed(
        val type: Type,
        val host: String,
        val port: Int,
        val username: String? = null,
        val password: String? = null
    )

    private val SOCKS_SCHEMES = setOf("socks", "socks4", "socks5")
    private val DIRECT_KEYWORDS = setOf("none", "direct", "off")
    private const val DEFAULT_HTTP_PROXY_PORT = 80
    private const val DEFAULT_SOCKS_PORT = 1080

    /** True when the user asked for a direct connection, overriding any auto-detected proxy. */
    fun isDirect(url: String): Boolean = url.trim().lowercase() in DIRECT_KEYWORDS

    fun parse(raw: String): Parsed? {
        val value = raw.trim()
        if (value.isEmpty() || isDirect(value)) return null
        return try {
            val uri = if ("://" in value) URI(value) else URI("http://$value")
            val host = uri.host?.takeIf { it.isNotEmpty() } ?: return null
            val socks = uri.scheme?.lowercase() in SOCKS_SCHEMES
            val port = when {
                uri.port != -1 -> uri.port
                socks -> DEFAULT_SOCKS_PORT
                else -> DEFAULT_HTTP_PROXY_PORT
            }
            val credentials = uri.userInfo?.split(":", limit = 2)
            Parsed(
                type = if (socks) Type.SOCKS else Type.HTTP,
                host = host,
                port = port,
                username = credentials?.getOrNull(0)?.takeIf { it.isNotEmpty() },
                password = credentials?.getOrNull(1)
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The URL with the password removed and the (non-secret) username kept, safe to persist in
     * plain text. This mirrors how the IDE stores its own HTTP proxy login in options XML while the
     * password lives in the password safe, and it lets the settings field round-trip so the user can
     * still see that credentials are configured.
     */
    fun stripPassword(raw: String): String {
        val value = raw.trim()
        val parsed = parse(value) ?: return value
        if (parsed.password == null) return value
        val scheme = if ("://" in value) value.substringBefore("://") + "://" else ""
        val user = parsed.username?.let { "$it@" } ?: ""
        return "$scheme$user${parsed.host}:${parsed.port}"
    }
}
