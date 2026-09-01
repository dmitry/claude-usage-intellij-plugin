package com.github.h2a.claudeusage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProxyUrlParserTest {

    @Test
    fun `direct keywords are recognised case-insensitively`() {
        listOf("none", "None", "  NONE  ", "direct", "off").forEach {
            assertTrue(ProxyUrlParser.isDirect(it), "'$it' should force a direct connection")
        }
    }

    @Test
    fun `a real proxy URL is not a direct keyword`() {
        assertFalse(ProxyUrlParser.isDirect("http://proxy.corp:3128"))
    }

    @Test
    fun `a scheme-less host and port defaults to HTTP`() {
        val parsed = ProxyUrlParser.parse("proxy.corp:3128")!!

        assertEquals(ProxyUrlParser.Type.HTTP, parsed.type)
        assertEquals("proxy.corp", parsed.host)
        assertEquals(3128, parsed.port)
        assertNull(parsed.username)
        assertNull(parsed.password)
    }

    @Test
    fun `HTTP proxy without a port defaults to 80`() {
        assertEquals(80, ProxyUrlParser.parse("http://proxy.corp")!!.port)
    }

    @Test
    fun `SOCKS schemes are detected and default to port 1080`() {
        listOf("socks", "socks4", "socks5").forEach { scheme ->
            val parsed = ProxyUrlParser.parse("$scheme://proxy.corp")!!
            assertEquals(ProxyUrlParser.Type.SOCKS, parsed.type, "$scheme should map to SOCKS")
            assertEquals(1080, parsed.port)
        }
    }

    @Test
    fun `an explicit SOCKS port wins over the default`() {
        assertEquals(9050, ProxyUrlParser.parse("socks5://proxy.corp:9050")!!.port)
    }

    @Test
    fun `credentials are split out of the URL`() {
        val parsed = ProxyUrlParser.parse("http://user:pa55w0rd@proxy.corp:3128")!!

        assertEquals("user", parsed.username)
        assertEquals("pa55w0rd", parsed.password)
        assertEquals("proxy.corp", parsed.host)
        assertEquals(3128, parsed.port)
    }

    @Test
    fun `a username without a password parses with a null password`() {
        val parsed = ProxyUrlParser.parse("http://user@proxy.corp:3128")!!

        assertEquals("user", parsed.username)
        assertNull(parsed.password)
    }

    @Test
    fun `unusable input parses to null rather than throwing`() {
        listOf("", "   ", "http://", "://nope", "none", "off").forEach {
            assertNull(ProxyUrlParser.parse(it), "'$it' should not parse to a proxy")
        }
    }

    @Test
    fun `stripPassword drops the password and keeps the username and endpoint`() {
        assertEquals(
            "http://user@proxy.corp:3128",
            ProxyUrlParser.stripPassword("http://user:pa55w0rd@proxy.corp:3128")
        )
    }

    @Test
    fun `stripPassword keeps the scheme-less form scheme-less`() {
        assertEquals("user@proxy.corp:3128", ProxyUrlParser.stripPassword("user:pa55w0rd@proxy.corp:3128"))
    }

    @Test
    fun `stripPassword never leaks a password into what gets persisted`() {
        listOf(
            "socks5://bob:hunter2@proxy.corp:1080",
            "http://bob:hunter2@proxy.corp:3128",
            "bob:hunter2@proxy.corp:3128"
        ).forEach { url ->
            val persisted = ProxyUrlParser.stripPassword(url)
            assertFalse(persisted.contains("hunter2"), "'$url' must not persist its password")
        }
    }

    @Test
    fun `stripPassword drops a password given without a username`() {
        assertEquals("http://proxy.corp:3128", ProxyUrlParser.stripPassword("http://:hunter2@proxy.corp:3128"))
    }

    @Test
    fun `stripPassword round-trips its own output unchanged`() {
        val once = ProxyUrlParser.stripPassword("http://user:pa55w0rd@proxy.corp:3128")

        assertEquals(once, ProxyUrlParser.stripPassword(once), "re-applying Apply must not alter the stored URL")
        assertEquals("user", ProxyUrlParser.parse(once)!!.username, "the username must survive the round trip")
        assertNull(ProxyUrlParser.parse(once)!!.password, "no password may survive the round trip")
    }

    @Test
    fun `stripPassword leaves credential-free and direct values untouched`() {
        assertEquals("http://proxy.corp:3128", ProxyUrlParser.stripPassword("http://proxy.corp:3128"))
        assertEquals("http://user@proxy.corp:3128", ProxyUrlParser.stripPassword("http://user@proxy.corp:3128"))
        assertEquals("none", ProxyUrlParser.stripPassword("none"))
        assertEquals("", ProxyUrlParser.stripPassword(""))
    }
}
