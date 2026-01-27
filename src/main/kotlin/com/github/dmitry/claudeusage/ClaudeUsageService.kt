package com.github.dmitry.claudeusage

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.APP)
class ClaudeUsageService : Disposable {
    private val LOG = Logger.getInstance(ClaudeUsageService::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val cachedUsage = AtomicReference<UsageResponse?>(null)
    private val lastFetchTime = AtomicReference<Instant?>(null)
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val listeners = mutableListOf<() -> Unit>()

    companion object {
        private const val USAGE_API_URL = "https://api.anthropic.com/api/oauth/usage"
        private const val ANTHROPIC_BETA = "oauth-2025-04-20"
        private const val REFRESH_INTERVAL_SECONDS = 60L

        fun getInstance(): ClaudeUsageService =
            ApplicationManager.getApplication().getService(ClaudeUsageService::class.java)
    }

    init {
        scheduler.scheduleAtFixedRate(
            { refreshUsage() },
            0,
            REFRESH_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        )
    }

    fun addListener(listener: () -> Unit) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: () -> Unit) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun notifyListeners() {
        val listenersCopy = synchronized(listeners) { listeners.toList() }
        listenersCopy.forEach { it.invoke() }
    }

    fun getUsage(): UsageResponse? = cachedUsage.get()

    fun refreshUsage() {
        val accessToken = ClaudeCredentialsReader.readAccessToken()
        if (accessToken == null) {
            cachedUsage.set(null)
            notifyListeners()
            return
        }

        try {
            val url = URL(USAGE_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("anthropic-beta", ANTHROPIC_BETA)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val usageResponse = json.decodeFromString<UsageResponse>(response)
                cachedUsage.set(usageResponse)
                lastFetchTime.set(Instant.now())
                LOG.info("Successfully fetched Claude usage data")
            } else {
                LOG.warn("Failed to fetch Claude usage: HTTP $responseCode")
                cachedUsage.set(null)
            }
            connection.disconnect()
        } catch (e: Exception) {
            LOG.warn("Error fetching Claude usage: ${e.message}")
            cachedUsage.set(null)
        }

        notifyListeners()
    }

    fun formatTimeUntilReset(resetsAt: String): String {
        return try {
            val resetTime = ZonedDateTime.parse(resetsAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val now = ZonedDateTime.now()
            val duration = Duration.between(now, resetTime)

            if (duration.isNegative) {
                "now"
            } else {
                val hours = duration.toHours()
                val minutes = duration.toMinutes() % 60
                when {
                    hours > 0 -> "${hours}h ${minutes}m"
                    minutes > 0 -> "${minutes}m"
                    else -> "<1m"
                }
            }
        } catch (e: Exception) {
            "unknown"
        }
    }

    override fun dispose() {
        scheduler.shutdownNow()
    }
}
