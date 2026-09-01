package com.github.h2a.claudeusage

import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class CodexAppServerClient(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val processStarter: (CodexAppServerLaunchSpec) -> Process = { launch ->
        ProcessBuilder(launch.command)
            .apply { environment()["PATH"] = launch.path }
            .start()
    }
) {
    fun fetch(executable: String): CodexRateLimitsResult {
        val process = try {
            processStarter(CodexAppServerLaunch.build(executable))
        } catch (e: Exception) {
            throw CodexAppServerException(
                "Could not start Codex CLI",
                e,
                CodexAppServerFailure.START_FAILED
            )
        }
        val worker = Executors.newFixedThreadPool(2)
        var request: Future<CodexRateLimitsResult>? = null
        var stderrRead: Future<String>? = null
        return try {
            stderrRead = worker.submit<String> { readBounded(process.errorStream) }
            request = worker.submit<CodexRateLimitsResult> {
                process.inputStream.bufferedReader().use { input ->
                    process.outputStream.bufferedWriter().use { output ->
                        CodexAppServerProtocol().readRateLimits(input, output)
                    }
                }
            }
            request.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            request?.cancel(true)
            throw CodexAppServerException(
                "Codex usage request timed out",
                e,
                CodexAppServerFailure.TIMEOUT
            )
        } catch (e: InterruptedException) {
            request?.cancel(true)
            Thread.currentThread().interrupt()
            throw CodexAppServerException(
                "Codex usage request was interrupted",
                e,
                CodexAppServerFailure.INTERRUPTED
            )
        } catch (e: ExecutionException) {
            val cause = e.cause
            val failure = if (cause is CodexAppServerException) {
                cause
            } else {
                CodexAppServerException("Could not read Codex usage", cause)
            }
            throw diagnose(failure, stderrRead)
        } finally {
            closeStreams(process)
            terminate(process)
            worker.shutdownNow()
            awaitWorker(worker)
        }
    }

    private fun readBounded(stream: InputStream): String {
        val captured = StringBuilder()
        val buffer = CharArray(STDERR_BUFFER_CHARS)
        stream.bufferedReader().use { reader ->
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                val remaining = MAX_STDERR_CHARS - captured.length
                if (remaining > 0) captured.append(buffer, 0, count.coerceAtMost(remaining))
            }
        }
        return captured.toString()
    }

    private fun diagnose(
        failure: CodexAppServerException,
        stderrRead: Future<String>?
    ): CodexAppServerException {
        val stderr = try {
            stderrRead?.get(STDERR_READ_GRACE_MILLIS, TimeUnit.MILLISECONDS).orEmpty()
        } catch (_: Exception) {
            ""
        }
        val normalized = stderr.lowercase()
        val nodeMissing = normalized.contains("node") && (
            normalized.contains("no such file") ||
                normalized.contains("not found") ||
                normalized.contains("not recognized")
            )
        val appServerMissing = normalized.contains("app-server") && (
            normalized.contains("unrecognized subcommand") ||
                normalized.contains("unknown command") ||
                normalized.contains("not recognized")
            )
        return when {
            nodeMissing -> CodexAppServerException(
                "Codex CLI could not find Node.js",
                failure,
                CodexAppServerFailure.RUNTIME_MISSING
            )
            appServerMissing -> CodexAppServerException(
                "Codex CLI does not support app-server",
                failure,
                CodexAppServerFailure.UPDATE_REQUIRED
            )
            else -> failure
        }
    }

    private fun closeStreams(process: Process) {
        runCatching { process.outputStream.close() }
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
    }

    private fun terminate(process: Process) {
        if (!process.isAlive) return
        process.destroy()
        if (waitFor(process, PROCESS_SHUTDOWN_GRACE_MILLIS) || !process.isAlive) return
        process.destroyForcibly()
        waitFor(process, PROCESS_SHUTDOWN_FORCE_MILLIS)
    }

    private fun waitFor(process: Process, timeoutMillis: Long): Boolean = try {
        process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    private fun awaitWorker(worker: java.util.concurrent.ExecutorService) {
        try {
            worker.awaitTermination(WORKER_SHUTDOWN_MILLIS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
        const val PROCESS_SHUTDOWN_GRACE_MILLIS = 250L
        const val PROCESS_SHUTDOWN_FORCE_MILLIS = 250L
        const val WORKER_SHUTDOWN_MILLIS = 250L
        const val STDERR_READ_GRACE_MILLIS = 100L
        const val STDERR_BUFFER_CHARS = 1_024
        const val MAX_STDERR_CHARS = 4_096
    }
}

data class CodexAppServerLaunchSpec(
    val command: List<String>,
    val path: String
)

object CodexAppServerLaunch {
    fun build(
        executable: String,
        inheritedPath: String? = System.getenv("PATH"),
        fallbackDirectories: List<Path> = CodexCliResolver.defaultFallbackDirectories(),
        pathSeparator: String = File.pathSeparator,
        osName: String = System.getProperty("os.name"),
        commandProcessor: String? = System.getenv("ComSpec"),
        realPathResolver: (Path) -> Path? = { path ->
            runCatching { path.toRealPath() }.getOrNull()
        }
    ): CodexAppServerLaunchSpec {
        val executablePath = Path.of(executable).toAbsolutePath().normalize()
        val directories = buildList {
            executablePath.parent?.let(::add)
            realPathResolver(executablePath)?.parent?.let(::add)
            addAll(fallbackDirectories)
            inheritedPath.orEmpty()
                .split(pathSeparator)
                .filter { it.isNotBlank() }
                .mapTo(this) { Path.of(it) }
        }
        val path = directories
            .map { it.toAbsolutePath().normalize().toString() }
            .distinct()
            .joinToString(pathSeparator)
        return CodexAppServerLaunchSpec(
            command = CodexAppServerCommand.build(executable, osName, commandProcessor),
            path = path
        )
    }
}

object CodexAppServerCommand {
    fun build(
        executable: String,
        osName: String = System.getProperty("os.name"),
        commandProcessor: String? = System.getenv("ComSpec")
    ): List<String> {
        val isWindows = osName.startsWith("Windows", ignoreCase = true)
        val isCommandShim = executable.endsWith(".cmd", ignoreCase = true) ||
            executable.endsWith(".bat", ignoreCase = true)
        if (isWindows && isCommandShim) {
            val processor = commandProcessor?.takeIf { it.isNotBlank() } ?: "cmd.exe"
            return listOf(
                processor,
                "/d",
                "/s",
                "/c",
                "\"\"$executable\" app-server --stdio\""
            )
        }
        return listOf(executable, "app-server", "--stdio")
    }
}
