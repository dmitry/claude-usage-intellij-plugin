package com.github.h2a.claudeusage

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression guards for the token-leak class of bug this fork exists to fix. Each test asserts a
 * property that has to hold for the access token to stay out of `idea.log`.
 */
class CredentialLeakTest {

    private companion object {
        const val ACCESS_TOKEN = "sk-ant-oat01-EXAMPLE-ACCESS-TOKEN"
        const val REFRESH_TOKEN = "sk-ant-ort01-EXAMPLE-REFRESH-TOKEN"
    }

    @Test
    fun `OAuthCredentials toString hides both tokens`() {
        val credentials = OAuthCredentials(
            accessToken = ACCESS_TOKEN,
            refreshToken = REFRESH_TOKEN,
            expiresAt = 1234567890,
            subscriptionType = "max",
            rateLimitTier = "default"
        )

        val rendered = credentials.toString()

        assertFalse(rendered.contains(ACCESS_TOKEN), "toString() must not print the access token")
        assertFalse(rendered.contains(REFRESH_TOKEN), "toString() must not print the refresh token")
        assertTrue(rendered.contains("max"), "non-secret fields stay visible for diagnostics")
    }

    @Test
    fun `credentials read Success toString hides the token`() {
        val rendered = ClaudeCredentialsReader.Result.Success(ACCESS_TOKEN).toString()

        assertFalse(rendered.contains(ACCESS_TOKEN), "toString() must not print the access token")
    }

    /**
     * Documents *why* [Secrets.safeMessage] exists: with the pinned kotlinx-serialization 1.6.2,
     * a decoding failure appends a `JSON input: ...` excerpt to the exception message. Since the
     * input here is the credentials blob, interpolating `e.message` into a log statement - which
     * is what upstream did - writes the access token to disk.
     */
    @Test
    fun `kotlinx decoding failure echoes its input, so only the exception type may be logged`() {
        val malformed = """{"claudeAiOauth":{"accessToken":"$ACCESS_TOKEN""""

        val thrown = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString<CredentialsFile>(malformed)
        }.exceptionOrNull()

        requireNotNull(thrown) { "malformed JSON should have failed to decode" }
        assertTrue(
            thrown.message.orEmpty().contains(ACCESS_TOKEN),
            "pinned kotlinx-serialization is expected to echo the input; if this fails the upstream " +
                "leak vector is gone and the safeMessage() workaround can be revisited"
        )
        assertFalse(
            Secrets.safeMessage(thrown).contains(ACCESS_TOKEN),
            "what the plugin actually logs must not contain the token"
        )
    }
}
