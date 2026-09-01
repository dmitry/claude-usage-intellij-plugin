package com.github.h2a.claudeusage

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CodexCliResolverTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `configured executable takes precedence over PATH`() {
        val configured = executable(tempDir.resolve("configured-codex"))
        val pathDirectory = Files.createDirectory(tempDir.resolve("bin"))
        executable(pathDirectory.resolve("codex"))

        val resolved = CodexCliResolver.resolve(
            configuredPath = configured.toString(),
            pathEnvironment = pathDirectory.toString()
        )

        assertEquals(configured.toAbsolutePath().normalize().toString(), resolved)
    }

    @Test
    fun `common install directory is used when the IDE PATH does not contain Codex`() {
        val installed = executable(tempDir.resolve("codex"))

        val resolved = CodexCliResolver.resolve(
            configuredPath = "",
            pathEnvironment = "",
            fallbackDirectories = listOf(tempDir)
        )

        assertEquals(installed.toAbsolutePath().normalize().toString(), resolved)
    }

    @Test
    fun `invalid configured executable does not silently select a different Codex from PATH`() {
        val pathDirectory = Files.createDirectory(tempDir.resolve("bin"))
        executable(pathDirectory.resolve("codex"))

        val resolved = CodexCliResolver.resolve(
            configuredPath = tempDir.resolve("missing-codex").toString(),
            pathEnvironment = pathDirectory.toString()
        )

        assertNull(resolved)
    }

    @Test
    fun `Windows PATH discovery recognizes the npm Codex command shim`() {
        val pathDirectory = Files.createDirectory(tempDir.resolve("windows-bin"))
        val installed = executable(pathDirectory.resolve("codex.cmd"))

        val resolved = CodexCliResolver.resolve(
            configuredPath = "",
            pathEnvironment = pathDirectory.toString(),
            fallbackDirectories = emptyList(),
            osName = "Windows 11"
        )

        assertEquals(installed.toAbsolutePath().normalize().toString(), resolved)
    }

    private fun executable(path: Path): Path {
        Files.writeString(path, "test")
        check(path.toFile().setExecutable(true))
        return path
    }
}
