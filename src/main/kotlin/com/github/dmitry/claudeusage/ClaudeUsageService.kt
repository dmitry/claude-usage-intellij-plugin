package com.github.dmitry.claudeusage

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import java.net.Authenticator
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.PasswordAuthentication
import java.net.URI
import java.net.UnknownHostException
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
    NO_CREDENTIALS("Credentials not found - run 'claude' to authenticate"),
    NO_ACCESS_TOKEN("No access token found in credentials"),
    AUTH_FAILED("Authentication failed - token may be expired. Run 'claude' to re-authenticate"),
    HTTP_ERROR("Server returned an error"),
    NETWORK_ERROR("Network error - check your internet connection"),
    PROXY_ERROR("Proxy error - check the Proxy URL in Settings > Tools > Claude Code Usage"),
    RATE_LIMITED("Rate limited - will retry shortly"),
    PARSE_ERROR("Failed to parse API response"),
    KEYCHAIN_DENIED("Keychain access denied - grant your IDE access to 'Claude Code-credentials' (see the hint below)"),
    KEYCHAIN_TIMEOUT("Keychain prompt timed out - approve access in the macOS dialog and refresh"),
    KEYCHAIN_ERROR("Failed to read macOS Keychain")
}

@Service(Service.Level.APP)
class ClaudeUsageService : Disposable {
    private val LOG = Logger.getInstance(ClaudeUsageService::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val cachedUsage = AtomicReference<UsageResponse?>(null)
    private val errorState = AtomicReference(UsageErrorState.NONE)
    private val lastFetchTime = AtomicReference<Instant?>(null)
    private val previousUtilization = AtomicReference<Double?>(null)
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val listeners = mutableListOf<() -> Unit>()
    @Volatile
    private var currentBackoffSeconds = getRefreshIntervalSeconds()
    @Volatile
    private var usingProxy = false

    companion object {
        private const val USAGE_API_URL = "https://api.anthropic.com/api/oauth/usage"
        private const val ANTHROPIC_BETA = "oauth-2025-04-20"
        private const val USER_AGENT = "claude-code/2.1.131"
        private const val MAX_BACKOFF_SECONDS = 3600L
        private const val MANUAL_REFRESH_COOLDOWN_SECONDS = 5L
        private const val NOTIFICATION_GROUP_ID = "Claude Code Usage"

        private fun getRefreshIntervalSeconds(): Long =
            ClaudeUsageSettings.getInstance().state.refreshIntervalMinutes * 60L

        fun getInstance(): ClaudeUsageService =
            ApplicationManager.getApplication().getService(ClaudeUsageService::class.java)
    }

    init {
        scheduleNext(0)
    }

    private fun scheduleNext(delaySeconds: Long) {
        scheduler.schedule({ refreshAndReschedule() }, delaySeconds, TimeUnit.SECONDS)
    }

    private fun refreshAndReschedule() {
        refreshUsage()
        scheduleNext(currentBackoffSeconds)
    }

    fun addListener(listener: () -> Unit) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeListener(listener: () -> Unit) {
        synchronized(listeners) { listeners.remove(listener) }
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

    fun canRefresh(): Boolean {
        val last = lastFetchTime.get() ?: return true
        return Duration.between(last, Instant.now()).seconds >= MANUAL_REFRESH_COOLDOWN_SECONDS
    }

    fun secondsSinceLastFetch(): Long? {
        val last = lastFetchTime.get() ?: return null
        return Duration.between(last, Instant.now()).seconds
    }

    fun secondsUntilCanRefresh(): Long {
        val last = lastFetchTime.get() ?: return 0
        val remaining = MANUAL_REFRESH_COOLDOWN_SECONDS - Duration.between(last, Instant.now()).seconds
        return remaining.coerceAtLeast(0)
    }

    fun refreshUsage() {
        if (!canRefresh()) {
            LOG.info("Skipping refresh, last fetch was ${secondsSinceLastFetch()}s ago")
            return
        }

        val accessToken = when (val r = ClaudeCredentialsReader.read()) {
            is ClaudeCredentialsReader.Result.Success -> r.accessToken
            is ClaudeCredentialsReader.Result.Failure -> {
                setError(r.error)
                return
            }
        }

        try {
            fetchFromApi(accessToken)
        } catch (e: Exception) {
            // Connection failures name the proxy they tried, which may carry credentials.
            LOG.warn("Error fetching Claude usage: ${Secrets.redactUrlCredentials(e.message ?: Secrets.safeMessage(e))}")
            setError(classifyFetchError(e))
        }

        notifyListeners()
    }

    private fun fetchFromApi(accessToken: String) {
        val url = URI(USAGE_API_URL).toURL()
        val resolvedProxy = ClaudeProxyResolver.resolve()
        usingProxy = resolvedProxy != null
        val connection = (resolvedProxy?.let { url.openConnection(it.proxy) } ?: url.openConnection()) as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("anthropic-beta", ANTHROPIC_BETA)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        resolvedProxy?.let { applyProxyAuthentication(connection, it) }

        try {
            handleResponse(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun applyProxyAuthentication(connection: HttpURLConnection, resolvedProxy: ResolvedProxy) {
        val username = resolvedProxy.username ?: return
        val password = resolvedProxy.password ?: ""
        try {
            connection.setAuthenticator(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication? =
                    if (requestorType == RequestorType.PROXY) {
                        PasswordAuthentication(username, password.toCharArray())
                    } else {
                        null
                    }
            })
        } catch (e: UnsupportedOperationException) {
            LOG.warn("Proxy authentication is not supported by this connection: ${e.message}")
        }
    }

    private fun classifyFetchError(e: Exception): UsageErrorState {
        val tunnelingFailed = e.message?.contains("Unable to tunnel through proxy", ignoreCase = true) == true
        val proxyUnreachable = usingProxy && (e is ConnectException || e is UnknownHostException)
        return if (tunnelingFailed || proxyUnreachable) UsageErrorState.PROXY_ERROR else UsageErrorState.NETWORK_ERROR
    }

    private fun handleResponse(connection: HttpURLConnection) {
        val responseCode = connection.responseCode

        if (responseCode == HttpURLConnection.HTTP_OK) {
            handleSuccess(connection)
            return
        }

        val error = when {
            responseCode == HttpURLConnection.HTTP_UNAUTHORIZED || responseCode == HttpURLConnection.HTTP_FORBIDDEN -> UsageErrorState.AUTH_FAILED
            responseCode == HttpURLConnection.HTTP_PROXY_AUTH -> UsageErrorState.PROXY_ERROR
            responseCode == 429 -> UsageErrorState.RATE_LIMITED
            else -> UsageErrorState.HTTP_ERROR
        }
        LOG.warn("Failed to fetch Claude usage: HTTP $responseCode")
        setError(error)
    }

    private fun handleSuccess(connection: HttpURLConnection) {
        val response = connection.inputStream.bufferedReader().readText()
        try {
            val parsed = json.decodeFromString<UsageResponse>(response)
            cachedUsage.set(parsed)
            errorState.set(UsageErrorState.NONE)
            lastFetchTime.set(Instant.now())
            currentBackoffSeconds = getRefreshIntervalSeconds()
            LOG.info("Successfully fetched Claude usage data")
            maybeNotifyThresholdCrossing(parsed)
        } catch (e: Exception) {
            LOG.warn("Failed to parse Claude usage response: ${e.message}")
            setError(UsageErrorState.PARSE_ERROR)
        }
    }

    private fun maybeNotifyThresholdCrossing(usage: UsageResponse) {
        val settings = ClaudeUsageSettings.getInstance().state
        val tier = settings.statusBarQuotaTier
        val current = usage.utilizationForTier(tier)
        val previous = previousUtilization.getAndSet(current)

        if (!settings.notifyOnThreshold || current == null || previous == null) return

        val resetHint = usage.resetsAtForTier(tier)
            ?.let { " - resets in ${formatTimeUntilReset(it)}" } ?: ""
        val percent = current.toInt()
        when {
            crossedUp(previous, current, settings.redThreshold) ->
                showThresholdNotification("Claude Code usage critical", "$tier usage at $percent%$resetHint", NotificationType.WARNING)
            crossedUp(previous, current, settings.yellowThreshold) ->
                showThresholdNotification("Claude Code usage warning", "$tier usage at $percent%$resetHint", NotificationType.INFORMATION)
        }
    }

    private fun crossedUp(previous: Double, current: Double, threshold: Int): Boolean =
        previous < threshold && current >= threshold

    private fun showThresholdNotification(title: String, content: String, type: NotificationType) {
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(title, content, type)
                .notify(null)
        }
    }

    private fun setError(error: UsageErrorState) {
        cachedUsage.set(null)
        errorState.set(error)
        lastFetchTime.set(Instant.now())
        currentBackoffSeconds = (currentBackoffSeconds * 2).coerceAtMost(MAX_BACKOFF_SECONDS)
        LOG.info("Set error $error, backoff ${currentBackoffSeconds}s")
        notifyListeners()
    }

    fun formatTimeUntilReset(resetsAt: String?): String {
        if (resetsAt == null) return "n/a"
        return try {
            val duration = Duration.between(ZonedDateTime.now(), ZonedDateTime.parse(resetsAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME))

            if (duration.isNegative) return "now"

            val days = duration.toDays()
            val hours = duration.toHours() % 24
            val minutes = duration.toMinutes() % 60
            when {
                days > 0 -> "${days}d ${hours}h ${minutes}m"
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m"
                else -> "<1m"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }

    override fun dispose() {
        scheduler.shutdownNow()
    }
}
