package com.github.h2a.claudeusage

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.APP)
class CodexUsageService : Disposable {
    private val log = Logger.getInstance(CodexUsageService::class.java)
    private val state = CodexUsageState()
    private val lastFetchTime = AtomicReference<Instant?>(null)
    private val refreshing = AtomicBoolean(false)
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val listeners = mutableListOf<() -> Unit>()

    @Volatile
    private var currentBackoffSeconds = refreshIntervalSeconds()

    init {
        scheduleNext(0)
    }

    fun getUsage(): CodexRateLimitsResult? = state.snapshot().usage

    fun getError(): CodexUsageErrorState = state.snapshot().error

    fun addListener(listener: () -> Unit) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeListener(listener: () -> Unit) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    fun canRefresh(): Boolean {
        if (refreshing.get()) return false
        val last = lastFetchTime.get() ?: return true
        return Duration.between(last, Instant.now()).seconds >= MANUAL_REFRESH_COOLDOWN_SECONDS
    }

    fun secondsUntilCanRefresh(): Long {
        val last = lastFetchTime.get() ?: return 0
        val remaining = MANUAL_REFRESH_COOLDOWN_SECONDS - Duration.between(last, Instant.now()).seconds
        return remaining.coerceAtLeast(0)
    }

    fun refreshUsage() {
        val settings = ClaudeUsageSettings.getInstance().state
        if (!settings.showCodexWidget || !canRefresh() || !refreshing.compareAndSet(false, true)) return

        try {
            val snapshot = state.refresh(settings.codexExecutablePath)
            lastFetchTime.set(Instant.now())
            currentBackoffSeconds = if (snapshot.error == CodexUsageErrorState.NONE) {
                refreshIntervalSeconds()
            } else {
                (currentBackoffSeconds * 2).coerceAtMost(MAX_BACKOFF_SECONDS)
            }
            if (snapshot.error == CodexUsageErrorState.NONE) {
                log.info("Successfully fetched Codex usage data")
            } else {
                log.info("Codex usage refresh failed: ${snapshot.error}")
            }
        } finally {
            refreshing.set(false)
            notifyListeners()
        }
    }

    fun formatTimeUntilReset(resetsAtEpochSeconds: Long): String =
        CodexResetTimeFormatter.format(resetsAtEpochSeconds)

    private fun refreshAndReschedule() {
        refreshUsage()
        scheduleNext(currentBackoffSeconds)
    }

    private fun scheduleNext(delaySeconds: Long) {
        scheduler.schedule({ refreshAndReschedule() }, delaySeconds, TimeUnit.SECONDS)
    }

    private fun notifyListeners() {
        val listenersCopy = synchronized(listeners) { listeners.toList() }
        ApplicationManager.getApplication().invokeLater {
            listenersCopy.forEach { it.invoke() }
        }
    }

    override fun dispose() {
        scheduler.shutdownNow()
    }

    companion object {
        private const val MAX_BACKOFF_SECONDS = 3600L
        private const val MANUAL_REFRESH_COOLDOWN_SECONDS = 5L

        private fun refreshIntervalSeconds(): Long =
            ClaudeUsageSettings.getInstance().state.refreshIntervalMinutes * 60L

        fun getInstance(): CodexUsageService =
            ApplicationManager.getApplication().getService(CodexUsageService::class.java)
    }
}
