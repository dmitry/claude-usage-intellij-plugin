package com.github.dmitry.claudeusage

import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy

data class ResolvedProxy(
    val proxy: Proxy,
    val username: String? = null,
    val password: String? = null
)

object ClaudeProxyResolver {
    private val LOG = Logger.getInstance(ClaudeProxyResolver::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val PROXY_ENV_KEYS = listOf("HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy")

    fun resolve(): ResolvedProxy? {
        val url = effectiveProxyUrl() ?: return null
        if (ProxyUrlParser.isDirect(url)) return null

        val parsed = ProxyUrlParser.parse(url)
        if (parsed == null) {
            LOG.warn("Failed to parse proxy URL: ${Secrets.redactUrlCredentials(url)}")
            return null
        }

        val proxy = Proxy(
            if (parsed.type == ProxyUrlParser.Type.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP,
            InetSocketAddress(parsed.host, parsed.port)
        )

        if (parsed.type == ProxyUrlParser.Type.SOCKS) {
            // The JDK only reads SOCKS credentials from the JVM-wide default Authenticator,
            // which the plugin must not replace, so they are dropped here.
            if (parsed.username != null) {
                LOG.warn("Credentials in a SOCKS proxy URL are not supported and will be ignored")
            }
            return ResolvedProxy(proxy)
        }

        // The password is kept in the IDE password safe, so the URL persisted in ClaudeCodeUsage.xml
        // holds only scheme/host/port and the username. The stored entry is scoped to this exact
        // endpoint and username, so it cannot be sent to a proxy it was not entered for.
        val username = parsed.username
        val password = parsed.password
            ?: username?.let { ProxyCredentialStore.loadPassword(parsed.host, parsed.port, it) }
        return ResolvedProxy(proxy, username, password)
    }

    fun isValidProxyUrl(raw: String): Boolean {
        val value = raw.trim()
        return value.isEmpty() || ProxyUrlParser.isDirect(value) || ProxyUrlParser.parse(value) != null
    }

    fun autoDetectedProxyUrl(): String? {
        readFromClaudeSettingsFile()?.let { return it }
        return PROXY_ENV_KEYS.firstNotNullOfOrNull { System.getenv(it)?.trim()?.takeIf { url -> url.isNotEmpty() } }
    }

    private fun effectiveProxyUrl(): String? {
        val manual = ClaudeUsageSettings.getInstance().state.proxyUrl.trim()
        return manual.ifEmpty { autoDetectedProxyUrl() }
    }

    private fun readFromClaudeSettingsFile(): String? {
        val file = File(System.getProperty("user.home"), ".claude/settings.json")
        if (!file.exists()) return null
        return try {
            val env = json.decodeFromString<ClaudeSettingsFile>(file.readText()).env ?: return null
            PROXY_ENV_KEYS.firstNotNullOfOrNull { env[it]?.trim()?.takeIf { url -> url.isNotEmpty() } }
        } catch (e: Exception) {
            // That env block routinely holds API keys and the decoding exception quotes the input,
            // so only the exception's type is logged.
            LOG.warn("Failed to read ~/.claude/settings.json for proxy config: ${Secrets.safeMessage(e)}")
            null
        }
    }
}
