package com.github.dmitry.claudeusage

import com.intellij.openapi.diagnostic.Logger
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object MacKeychainReader {
    private val LOG = Logger.getInstance(MacKeychainReader::class.java)

    private const val SECURITY_BIN = "/usr/bin/security"
    private const val DEFAULT_SERVICE = "Claude Code-credentials"
    private const val FALLBACK_USER = "claude-code-user"
    private const val TIMEOUT_SECONDS = 10L
    private val USERNAME_REGEX = Regex("^[a-zA-Z0-9._-]+$")

    sealed class Result {
        data class Success(val rawJson: String) : Result()
        object NotFound : Result()
        object Denied : Result()
        object TimedOut : Result()
        data class Error(val message: String) : Result()
    }

    fun read(): Result {
        val service = serviceName()
        val account = accountName()
        return runSecurity(service, account)
    }

    private fun runSecurity(service: String, account: String): Result {
        val process = try {
            ProcessBuilder(SECURITY_BIN, "find-generic-password", "-a", account, "-w", "-s", service)
                .redirectErrorStream(false)
                .start()
        } catch (e: IOException) {
            LOG.warn("Failed to launch `security`: ${e.message}")
            return Result.Error(e.message ?: "failed to launch security CLI")
        }

        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val stdoutThread = Thread { process.inputStream.bufferedReader().use { stdout.append(it.readText()) } }
        val stderrThread = Thread { process.errorStream.bufferedReader().use { stderr.append(it.readText()) } }
        stdoutThread.start()
        stderrThread.start()

        val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            LOG.warn("`security` timed out after ${TIMEOUT_SECONDS}s - likely keychain prompt awaiting user")
            return Result.TimedOut
        }
        stdoutThread.join(1000)
        stderrThread.join(1000)

        return when (process.exitValue()) {
            0 -> {
                val raw = stdout.toString().trim()
                if (raw.isEmpty()) Result.NotFound else Result.Success(raw)
            }
            44, 51 -> {
                LOG.info("Keychain entry not found for service='$service' account='$account'")
                Result.NotFound
            }
            45, 25293, -128 -> {
                LOG.warn("Keychain access denied for service='$service' (exit=${process.exitValue()})")
                Result.Denied
            }
            else -> {
                val err = stderr.toString().trim()
                if (err.contains("could not be found", ignoreCase = true)) {
                    Result.NotFound
                } else if (err.contains("interaction is not allowed", ignoreCase = true) ||
                    err.contains("user interaction", ignoreCase = true) ||
                    err.contains("denied", ignoreCase = true)
                ) {
                    Result.Denied
                } else {
                    LOG.warn("`security` exit=${process.exitValue()}, stderr=$err")
                    Result.Error("security exit=${process.exitValue()}: $err")
                }
            }
        }
    }

    private fun serviceName(): String {
        val configDir = System.getenv("CLAUDE_CONFIG_DIR")
        if (configDir.isNullOrBlank()) return DEFAULT_SERVICE
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(configDir.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .substring(0, 8)
        return "$DEFAULT_SERVICE-$hash"
    }

    private fun accountName(): String {
        val user = System.getenv("USER") ?: System.getProperty("user.name") ?: return FALLBACK_USER
        return if (USERNAME_REGEX.matches(user)) user else FALLBACK_USER
    }
}
