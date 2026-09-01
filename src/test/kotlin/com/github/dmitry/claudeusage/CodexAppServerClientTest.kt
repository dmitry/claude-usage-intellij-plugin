package com.github.h2a.claudeusage

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS

class CodexAppServerClientTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun `Codex launch PATH repairs a sparse macOS GUI environment`() {
        val launch = CodexAppServerLaunch.build(
            executable = "/opt/homebrew/bin/codex",
            inheritedPath = "/usr/bin:/bin:/usr/sbin:/sbin",
            fallbackDirectories = listOf(
                Path.of("/opt/homebrew/bin"),
                Path.of("/usr/local/bin")
            ),
            pathSeparator = ":",
            osName = "Mac OS X",
            realPathResolver = { null }
        )

        assertEquals(
            "/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin",
            launch.path
        )
        assertEquals(listOf("/opt/homebrew/bin/codex", "app-server", "--stdio"), launch.command)
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun `Codex launch PATH includes a version-manager bin directory without duplicates`() {
        val launch = CodexAppServerLaunch.build(
            executable = "/Users/test/.nvm/versions/node/v24/bin/codex",
            inheritedPath = "/Users/test/.nvm/versions/node/v24/bin:/usr/bin:/bin",
            fallbackDirectories = emptyList(),
            pathSeparator = ":",
            osName = "Mac OS X",
            realPathResolver = { null }
        )

        assertEquals(
            "/Users/test/.nvm/versions/node/v24/bin:/usr/bin:/bin",
            launch.path
        )
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun `Codex launch PATH includes the resolved executable directory`() {
        val launch = CodexAppServerLaunch.build(
            executable = "/usr/local/bin/codex",
            inheritedPath = "/usr/bin:/bin",
            fallbackDirectories = emptyList(),
            pathSeparator = ":",
            osName = "Mac OS X",
            realPathResolver = { Path.of("/opt/codex/package/bin/codex") }
        )

        assertEquals(
            "/usr/local/bin:/opt/codex/package/bin:/usr/bin:/bin",
            launch.path
        )
    }

    @Test
    fun `Windows npm command shim is launched through the command processor`() {
        val command = CodexAppServerCommand.build(
            executable = "C:\\Program Files\\nodejs\\codex.cmd",
            osName = "Windows 11",
            commandProcessor = "C:\\Windows\\System32\\cmd.exe"
        )

        assertEquals(
            listOf(
                "C:\\Windows\\System32\\cmd.exe",
                "/d",
                "/s",
                "/c",
                "\"\"C:\\Program Files\\nodejs\\codex.cmd\" app-server --stdio\""
            ),
            command
        )
    }

    @Test
    fun `fetch returns rate limits and closes the app-server process`() {
        val process = TestProcess(
            """
            {"id":0,"result":{"userAgent":"test"}}
            {"id":1,"result":{"rateLimits":{"limitId":"codex","primary":{"usedPercent":21,"windowDurationMins":10080,"resetsAt":1788796558}}}}
            """.trimIndent()
        )
        val client = CodexAppServerClient(
            timeoutMillis = 1_000,
            processStarter = { process }
        )

        val result = client.fetch("/test/codex")

        assertEquals(CodexUsageRow("Weekly", 21.0, 1788796558), result.statusBarRow())
        assertFalse(process.isAlive)
        assertTrue(process.streamsClosed)
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun `default launcher finds a CLI runtime installed beside Codex`() {
        val binDirectory = Files.createDirectory(tempDir.resolve("bin"))
        val codex = executable(
            binDirectory.resolve("codex"),
            """
            #!/usr/bin/env codex-test-runtime
            while IFS= read -r request; do
              case "${'$'}request" in
                *'"id":1'*)
                  printf '%s\n' '{"id":1,"result":{"rateLimits":{"limitId":"codex","primary":{"usedPercent":21,"windowDurationMins":10080,"resetsAt":1788796558}}}}'
                  exit 0
                  ;;
              esac
            done
            """.trimIndent()
        )
        Files.createSymbolicLink(binDirectory.resolve("codex-test-runtime"), Path.of("/bin/sh"))

        val result = CodexAppServerClient(timeoutMillis = 1_000).fetch(codex.toString())

        assertEquals(CodexUsageRow("Weekly", 21.0, 1788796558), result.statusBarRow())
    }

    @Test
    fun `missing Node stderr becomes a safe runtime diagnostic`() {
        val process = TestProcess(
            stdout = "",
            stderr = "env: node: No such file or directory"
        )
        val client = CodexAppServerClient(
            timeoutMillis = 1_000,
            processStarter = { process }
        )

        val error = assertFailsWith<CodexAppServerException> {
            client.fetch("/test/codex")
        }

        assertEquals(CodexAppServerFailure.RUNTIME_MISSING, error.failure)
        assertEquals("Codex CLI could not find Node.js", error.message)
        assertFalse(error.message.orEmpty().contains("env:"))
    }

    @Test
    fun `old Codex CLI stderr becomes an update diagnostic`() {
        val process = TestProcess(
            stdout = "",
            stderr = "error: unrecognized subcommand 'app-server'"
        )
        val client = CodexAppServerClient(
            timeoutMillis = 1_000,
            processStarter = { process }
        )

        val error = assertFailsWith<CodexAppServerException> {
            client.fetch("/test/codex")
        }

        assertEquals(CodexAppServerFailure.UPDATE_REQUIRED, error.failure)
        assertEquals("Codex CLI does not support app-server", error.message)
    }

    @Test
    fun `fetch times out and closes an unresponsive app-server process`() {
        val process = TestProcess(BlockingInputStream())
        val client = CodexAppServerClient(
            timeoutMillis = 20,
            processStarter = { process }
        )

        val error = assertFailsWith<CodexAppServerException> {
            client.fetch("/test/codex")
        }

        assertEquals("Codex usage request timed out", error.message)
        assertFalse(process.isAlive)
        assertTrue(process.streamsClosed)
    }

    @Test
    fun `fetch forcibly terminates an app-server that ignores graceful shutdown`() {
        val process = TestProcess(
            stdout = """
                {"id":0,"result":{"userAgent":"test"}}
                {"id":1,"result":{"rateLimits":{"limitId":"codex","primary":{"usedPercent":21}}}}
            """.trimIndent(),
            ignoreGracefulDestroy = true
        )
        val client = CodexAppServerClient(
            timeoutMillis = 1_000,
            processStarter = { process }
        )

        client.fetch("/test/codex")

        assertTrue(process.forciblyDestroyed)
        assertFalse(process.isAlive)
        assertTrue(process.streamsClosed)
    }

    private class BlockingInputStream : InputStream() {
        override fun read(): Int {
            return try {
                Thread.sleep(60_000)
                -1
            } catch (_: InterruptedException) {
                -1
            }
        }
    }

    private fun executable(path: Path, contents: String): Path {
        Files.writeString(path, contents)
        check(path.toFile().setExecutable(true))
        return path
    }

    private class TestProcess(
        stdout: InputStream,
        private val stderr: InputStream = ByteArrayInputStream(ByteArray(0)),
        private val ignoreGracefulDestroy: Boolean = false
    ) : Process() {
        constructor(
            stdout: String,
            stderr: String = "",
            ignoreGracefulDestroy: Boolean = false
        ) : this(
            ByteArrayInputStream(stdout.toByteArray()),
            ByteArrayInputStream(stderr.toByteArray()),
            ignoreGracefulDestroy
        )

        private val stdout = TrackingInputStream(stdout)
        private val stdin = TrackingOutputStream()
        var destroyed = false
            private set
        var forciblyDestroyed = false
            private set
        val streamsClosed: Boolean
            get() = stdout.closed && stdin.closed

        override fun getOutputStream(): OutputStream = stdin
        override fun getInputStream(): InputStream = stdout
        override fun getErrorStream(): InputStream = stderr
        override fun waitFor(): Int = 0
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = !isAlive
        override fun exitValue(): Int = 0
        override fun destroy() {
            if (!ignoreGracefulDestroy) destroyed = true
        }

        override fun destroyForcibly(): Process {
            destroyed = true
            forciblyDestroyed = true
            return this
        }

        override fun isAlive(): Boolean = !destroyed
    }

    private class TrackingInputStream(private val delegate: InputStream) : InputStream() {
        var closed = false
            private set

        override fun read(): Int = delegate.read()

        override fun close() {
            closed = true
            delegate.close()
        }
    }

    private class TrackingOutputStream : ByteArrayOutputStream() {
        var closed = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }
}
