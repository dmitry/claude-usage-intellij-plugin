package com.github.h2a.claudeusage

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.BufferedWriter

class CodexAppServerProtocol {
    private val json = Json { ignoreUnknownKeys = true }

    fun readRateLimits(input: BufferedReader, output: BufferedWriter): CodexRateLimitsResult {
        output.write(
            """{"method":"initialize","id":0,"params":{"clientInfo":{"name":"claude_usage_intellij_plugin","title":"Claude and Codex Usage IntelliJ Plugin","version":"0.3.2-local"}}}"""
        )
        output.newLine()
        output.write("""{"method":"initialized","params":{}}""")
        output.newLine()
        output.write("""{"method":"account/rateLimits/read","id":1}""")
        output.newLine()
        output.flush()

        while (true) {
            val line = input.readLine() ?: throw CodexAppServerException("Codex app-server closed before returning usage")
            val element = json.parseToJsonElement(line)
            val id = element.jsonObject["id"]?.jsonPrimitive?.intOrNull
            if (id != RATE_LIMITS_REQUEST_ID) continue

            val response = json.decodeFromJsonElement<CodexRateLimitsRpcResponse>(element)
            response.error?.let { error ->
                throw CodexAppServerException(error.message ?: "Codex app-server returned an error")
            }
            return response.result
                ?: throw CodexAppServerException("Codex app-server returned no usage data")
        }
    }

    private companion object {
        const val RATE_LIMITS_REQUEST_ID = 1
    }
}

enum class CodexAppServerFailure {
    START_FAILED,
    RUNTIME_MISSING,
    UPDATE_REQUIRED,
    TIMEOUT,
    INTERRUPTED,
    REQUEST_FAILED
}

class CodexAppServerException(
    message: String,
    cause: Throwable? = null,
    val failure: CodexAppServerFailure = CodexAppServerFailure.REQUEST_FAILED
) : Exception(message, cause)
