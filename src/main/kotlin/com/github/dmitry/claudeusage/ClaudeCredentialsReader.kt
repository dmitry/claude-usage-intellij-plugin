package com.github.dmitry.claudeusage

import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import java.io.File

object ClaudeCredentialsReader {
    private val LOG = Logger.getInstance(ClaudeCredentialsReader::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val credentialsPath: File
        get() {
            val configuredPath = ClaudeUsageSettings.getInstance().state.credentialsFilePath
            val resolved = if (configuredPath.startsWith("~")) {
                configuredPath.replaceFirst("~", System.getProperty("user.home"))
            } else {
                configuredPath
            }
            return File(resolved)
        }

    fun readAccessToken(): String? {
        val file = credentialsPath
        if (!file.exists()) {
            LOG.info("Claude credentials file not found at: ${file.absolutePath}")
            return null
        }

        return try {
            val content = file.readText()
            val credentials = json.decodeFromString<CredentialsFile>(content)
            credentials.claudeAiOauth?.accessToken
        } catch (e: Exception) {
            LOG.warn("Failed to read Claude credentials: ${e.message}")
            null
        }
    }

    fun hasCredentials(): Boolean = credentialsPath.exists()
}
