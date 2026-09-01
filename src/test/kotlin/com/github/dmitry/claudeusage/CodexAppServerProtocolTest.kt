package com.github.h2a.claudeusage

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CodexAppServerProtocolTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `rate limit request completes the app-server handshake and ignores notifications`() {
        val input = StringReader(
            """
            {"id":0,"result":{"userAgent":"test","codexHome":"/tmp/.codex"}}
            {"method":"account/rateLimits/updated","params":{"rateLimits":null}}
            {"id":1,"result":{"rateLimits":{"limitId":"codex","primary":{"usedPercent":21,"windowDurationMins":10080,"resetsAt":1788796558}}}}
            """.trimIndent()
        ).buffered()
        val rawOutput = StringWriter()
        val output = rawOutput.buffered()

        val result = CodexAppServerProtocol().readRateLimits(input, output)

        assertEquals(CodexUsageRow("Weekly", 21.0, 1788796558), result.statusBarRow())
        val requests = rawOutput.toString().lineSequence()
            .filter { it.isNotBlank() }
            .map { json.parseToJsonElement(it).jsonObject }
            .toList()
        assertEquals(listOf("initialize", "initialized", "account/rateLimits/read"), requests.map {
            it.getValue("method").jsonPrimitive.content
        })
        assertEquals("claude_usage_intellij_plugin", requests.first()
            .getValue("params").jsonObject
            .getValue("clientInfo").jsonObject
            .getValue("name").jsonPrimitive.content)
    }

    @Test
    fun `rate limit request reports the app-server error`() {
        val input = StringReader(
            """
            {"id":0,"result":{"userAgent":"test"}}
            {"id":1,"error":{"code":-32600,"message":"ChatGPT login required"}}
            """.trimIndent()
        ).buffered()

        val error = assertFailsWith<CodexAppServerException> {
            CodexAppServerProtocol().readRateLimits(input, StringWriter().buffered())
        }

        assertEquals("ChatGPT login required", error.message)
    }
}
