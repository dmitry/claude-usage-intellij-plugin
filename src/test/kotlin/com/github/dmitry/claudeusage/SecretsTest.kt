package com.github.dmitry.claudeusage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretsTest {

    @Test
    fun `safeMessage keeps the exception type and drops the message`() {
        val token = "sk-ant-oat01-EXAMPLE-TOKEN"
        val e = IllegalStateException("failed while parsing $token")

        val safe = Secrets.safeMessage(e)

        assertEquals("IllegalStateException", safe)
        assertFalse(safe.contains(token), "safeMessage must not echo the exception message")
    }

    @Test
    fun `redactUrlCredentials masks user and password with a scheme`() {
        assertEquals(
            "http://***@proxy.corp:3128",
            Secrets.redactUrlCredentials("http://user:pa55w0rd@proxy.corp:3128")
        )
    }

    @Test
    fun `redactUrlCredentials masks credentials without a scheme`() {
        assertEquals("***@proxy.corp:3128", Secrets.redactUrlCredentials("user:pa55w0rd@proxy.corp:3128"))
    }

    @Test
    fun `redactUrlCredentials masks a username-only URL`() {
        assertEquals("http://***@proxy.corp:3128", Secrets.redactUrlCredentials("http://user@proxy.corp:3128"))
    }

    @Test
    fun `redactUrlCredentials leaves credential-free URLs untouched`() {
        assertEquals("http://proxy.corp:3128", Secrets.redactUrlCredentials("http://proxy.corp:3128"))
        assertEquals("socks5://proxy.corp:1080", Secrets.redactUrlCredentials("socks5://proxy.corp:1080"))
        assertEquals("proxy.corp:3128", Secrets.redactUrlCredentials("proxy.corp:3128"))
    }

    @Test
    fun `redactUrlCredentials masks a URL embedded in a log line`() {
        val redacted = Secrets.redactUrlCredentials(
            "Unable to tunnel through proxy socks5://bob:hunter2@proxy.corp:1080 - giving up"
        )

        assertFalse(redacted.contains("hunter2"), "password must not survive redaction")
        assertFalse(redacted.contains("bob"), "username must not survive redaction")
        assertTrue(redacted.contains("proxy.corp:1080"), "host and port stay visible for diagnostics")
    }
}
