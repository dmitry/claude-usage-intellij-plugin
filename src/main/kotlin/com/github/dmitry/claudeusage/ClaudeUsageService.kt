package com.github.dmitry.claudeusage

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

enum class UsageErrorState(val message: String) {
    NONE(""),
    NO_CREDENTIALS("Credentials file not found"),
    NO_ACCESS_TOKEN("No access token found in credentials file"),
    AUTH_FAILED("Authentication failed — token may be expired. Run 'claude' to re-authenticate"),
    HTTP_ERROR("Server returned an error"),
    NETWORK_ERROR("Network error — check your internet connection"),
    PARSE_ERROR("Failed to parse API response")
}

@Service(Service.Level.APP)
class ClaudeUsageService : Disposable {
    private val LOG = Logger.getInstance(ClaudeUsageService::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val cachedUsage = AtomicReference<UsageResponse?>(null)
    private val errorState = AtomicReference(UsageErrorState.NONE)
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
        ApplicationManager.getApplication().invokeLater {
            listenersCopy.forEach { it.invoke() }
        }
    }

    fun getUsage(): UsageResponse? = cachedUsage.get()

    fun getError(): UsageErrorState = errorState.get()

    fun getLastFetchTime(): Instant? = lastFetchTime.get()

    fun refreshUsage() {
        if (!ClaudeCredentialsReader.hasCredentials()) {
            cachedUsage.set(null)
            errorState.set(UsageErrorState.NO_CREDENTIALS)
            notifyListeners()
            return
        }

        val accessToken = ClaudeCredentialsReader.readAccessToken()
        if (accessToken == null) {
            cachedUsage.set(null)
            errorState.set(UsageErrorState.NO_ACCESS_TOKEN)
            notifyListeners()
            return
        }

        try {
            val connection = URI(USAGE_API_URL).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("anthropic-beta", ANTHROPIC_BETA)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                try {
                    val usageResponse = json.decodeFromString<UsageResponse>(response)
                    cachedUsage.set(usageResponse)
                    errorState.set(UsageErrorState.NONE)
                    lastFetchTime.set(Instant.now())
                    LOG.info("Successfully fetched Claude usage data")
                } catch (e: Exception) {
                    LOG.warn("Failed to parse Claude usage response: ${e.message}")
                    cachedUsage.set(null)
                    errorState.set(UsageErrorState.PARSE_ERROR)
                }
            } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED || responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                LOG.warn("Claude usage auth failed: HTTP $responseCode")
                cachedUsage.set(null)
                errorState.set(UsageErrorState.AUTH_FAILED)
            } else {
                LOG.warn("Failed to fetch Claude usage: HTTP $responseCode")
                cachedUsage.set(null)
                errorState.set(UsageErrorState.HTTP_ERROR)
            }
            connection.disconnect()
        } catch (e: java.net.UnknownHostException) {
            LOG.warn("Network error fetching Claude usage: ${e.message}")
            cachedUsage.set(null)
            errorState.set(UsageErrorState.NETWORK_ERROR)
        } catch (e: java.net.ConnectException) {
            LOG.warn("Connection error fetching Claude usage: ${e.message}")
            cachedUsage.set(null)
            errorState.set(UsageErrorState.NETWORK_ERROR)
        } catch (e: java.net.SocketTimeoutException) {
            LOG.warn("Timeout fetching Claude usage: ${e.message}")
            cachedUsage.set(null)
            errorState.set(UsageErrorState.NETWORK_ERROR)
        } catch (e: Exception) {
            LOG.warn("Error fetching Claude usage: ${e.message}")
            cachedUsage.set(null)
            errorState.set(UsageErrorState.NETWORK_ERROR)
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
