package com.github.h2a.claudeusage

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * The proxy password lives in the IDE password safe (Keychain-backed on macOS), never on disk in
 * plain text.
 *
 * Upstream persisted whatever was typed into the Proxy URL field - including `user:password@` -
 * verbatim in `ClaudeCodeUsage.xml` under the IDE config directory.
 *
 * Entries are keyed by proxy endpoint and only returned for a matching username, so a password
 * stored for one proxy can never be sent to a different host or account.
 */
object ProxyCredentialStore {
    private fun attributes(host: String, port: Int): CredentialAttributes =
        CredentialAttributes(generateServiceName("Claude Code Usage", "proxy@$host:$port"))

    fun savePassword(host: String, port: Int, username: String, password: String) {
        PasswordSafe.instance.set(attributes(host, port), Credentials(username, password))
    }

    fun loadPassword(host: String, port: Int, username: String): String? {
        val credentials = PasswordSafe.instance.get(attributes(host, port)) ?: return null
        if (credentials.userName != username) return null
        return credentials.getPasswordAsString()?.takeIf { it.isNotEmpty() }
    }

    fun clear(host: String, port: Int) {
        PasswordSafe.instance.set(attributes(host, port), null)
    }
}
