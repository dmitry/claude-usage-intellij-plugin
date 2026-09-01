package com.github.h2a.claudeusage

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import kotlinx.serialization.json.Json
import java.io.File

object ClaudeCredentialsReader {
    private val LOG = Logger.getInstance(ClaudeCredentialsReader::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    sealed class Result {
        data class Success(val accessToken: String) : Result() {
            // The generated data-class toString() prints the token verbatim, so any future
            // LOG.info("$result") would leak it. Keep the redaction with the token.
            override fun toString(): String = "Success(accessToken=***)"
        }

        data class Failure(val error: UsageErrorState) : Result()
    }

    fun read(): Result {
        val settings = ClaudeUsageSettings.getInstance().state

        if (SystemInfo.isMac && settings.useMacKeychain) {
            val keychainResult = readFromKeychain()
            if (keychainResult is Result.Success) return keychainResult

            val fileResult = readFromFile()
            if (fileResult is Result.Success) return fileResult

            return keychainResult
        }

        return readFromFile()
    }

    private fun readFromKeychain(): Result {
        return when (val r = MacKeychainReader.read()) {
            is MacKeychainReader.Result.Success -> parseJson(r.rawJson)
            is MacKeychainReader.Result.NotFound -> Result.Failure(UsageErrorState.NO_CREDENTIALS)
            is MacKeychainReader.Result.Denied -> Result.Failure(UsageErrorState.KEYCHAIN_DENIED)
            is MacKeychainReader.Result.TimedOut -> Result.Failure(UsageErrorState.KEYCHAIN_TIMEOUT)
            is MacKeychainReader.Result.Error -> Result.Failure(UsageErrorState.KEYCHAIN_ERROR)
        }
    }

    private fun readFromFile(): Result {
        val file = credentialsPath
        if (!file.exists()) {
            LOG.info("Claude credentials file not found at: ${file.absolutePath}")
            return Result.Failure(UsageErrorState.NO_CREDENTIALS)
        }
        return parseJson(file.readTextSafely() ?: return Result.Failure(UsageErrorState.NO_CREDENTIALS))
    }

    private fun parseJson(raw: String): Result {
        return try {
            val token = json.decodeFromString<CredentialsFile>(raw).claudeAiOauth?.accessToken
            if (token.isNullOrBlank()) Result.Failure(UsageErrorState.NO_ACCESS_TOKEN)
            else Result.Success(token)
        } catch (e: Exception) {
            // kotlinx.serialization appends a "JSON input: ..." excerpt of what it was decoding to
            // its exception message. That input is the credentials blob, so logging the message
            // would put the access token in idea.log. Only the exception type is safe here.
            LOG.warn("Failed to parse Claude credentials: ${Secrets.safeMessage(e)}")
            Result.Failure(UsageErrorState.NO_ACCESS_TOKEN)
        }
    }

    private fun File.readTextSafely(): String? = try {
        readText()
    } catch (e: Exception) {
        LOG.warn("Failed to read Claude credentials file at $absolutePath: ${Secrets.safeMessage(e)}")
        null
    }

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
}
