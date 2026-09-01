package com.github.h2a.claudeusage

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class CodexUsageResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a single weekly Codex limit becomes one popup row`() {
        val response = json.decodeFromString<CodexRateLimitsRpcResponse>(
            """
            {
              "id": 1,
              "result": {
                "rateLimits": {
                  "limitId": "codex",
                  "limitName": null,
                  "primary": {
                    "usedPercent": 21,
                    "windowDurationMins": 10080,
                    "resetsAt": 1788796558
                  },
                  "secondary": null,
                  "credits": {
                    "hasCredits": false,
                    "unlimited": false,
                    "balance": null
                  },
                  "individualLimit": null,
                  "spendControlReached": false,
                  "planType": "self_serve_business_prolite",
                  "rateLimitReachedType": null
                },
                "rateLimitsByLimitId": {
                  "codex": {
                    "limitId": "codex",
                    "limitName": null,
                    "primary": {
                      "usedPercent": 21,
                      "windowDurationMins": 10080,
                      "resetsAt": 1788796558
                    },
                    "secondary": null,
                    "credits": {
                      "hasCredits": false,
                      "unlimited": false,
                      "balance": null
                    },
                    "individualLimit": null,
                    "spendControlReached": false,
                    "planType": "self_serve_business_prolite",
                    "rateLimitReachedType": null
                  }
                },
                "rateLimitResetCredits": {
                  "availableCount": 0,
                  "credits": []
                }
              }
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(CodexUsageRow("Weekly", 21.0, 1788796558)),
            response.result!!.displayRows()
        )
    }

    @Test
    fun `multi-bucket payload renders each window once and chooses the fullest for the status bar`() {
        val response = json.decodeFromString<CodexRateLimitsRpcResponse>(
            """
            {
              "id": 6,
              "result": {
                "rateLimits": {
                  "limitId": "codex",
                  "limitName": null,
                  "primary": { "usedPercent": 25, "windowDurationMins": 300, "resetsAt": 1730947200 },
                  "secondary": { "usedPercent": 55, "windowDurationMins": 10080, "resetsAt": 1731206400 },
                  "rateLimitReachedType": null
                },
                "rateLimitsByLimitId": {
                  "codex": {
                    "limitId": "codex",
                    "limitName": null,
                    "primary": { "usedPercent": 25, "windowDurationMins": 300, "resetsAt": 1730947200 },
                    "secondary": { "usedPercent": 55, "windowDurationMins": 10080, "resetsAt": 1731206400 },
                    "rateLimitReachedType": null
                  },
                  "codex_other": {
                    "limitId": "codex_other",
                    "limitName": "codex_other",
                    "primary": { "usedPercent": 42, "windowDurationMins": 60, "resetsAt": 1730950800 },
                    "secondary": null,
                    "rateLimitReachedType": null
                  }
                },
                "rateLimitResetCredits": null
              }
            }
            """.trimIndent()
        )

        val result = response.result!!
        assertEquals(
            listOf(
                CodexUsageRow("5-hour", 25.0, 1730947200),
                CodexUsageRow("Weekly", 55.0, 1731206400),
                CodexUsageRow("Codex other · 1-hour", 42.0, 1730950800)
            ),
            result.displayRows()
        )
        assertEquals(CodexUsageRow("Weekly", 55.0, 1731206400), result.statusBarRow())
    }
}
