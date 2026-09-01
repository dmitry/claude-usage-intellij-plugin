package com.github.h2a.claudeusage

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

object CodexCliResolver {
    fun resolve(
        configuredPath: String,
        pathEnvironment: String? = System.getenv("PATH"),
        fallbackDirectories: List<Path> = defaultFallbackDirectories(),
        osName: String = System.getProperty("os.name")
    ): String? {
        configuredPath.trim().takeIf { it.isNotEmpty() }?.let { configured ->
            return executablePath(configured)
        }

        val pathDirectories = pathEnvironment.orEmpty()
            .split(File.pathSeparatorChar)
            .asSequence()
            .filter { it.isNotBlank() }
            .map(Path::of)

        return (pathDirectories + fallbackDirectories.asSequence())
            .flatMap { directory -> executableNames(osName).map(directory::resolve) }
            .mapNotNull(::executablePath)
            .firstOrNull()
    }

    private fun executableNames(osName: String): List<String> =
        if (osName.startsWith("Windows", ignoreCase = true)) {
            listOf("codex.exe", "codex.cmd", "codex.bat", "codex")
        } else {
            listOf("codex")
        }

    internal fun defaultFallbackDirectories(): List<Path> {
        val userHome = Path.of(System.getProperty("user.home"))
        return listOf(
            Path.of("/opt/homebrew/bin"),
            Path.of("/usr/local/bin"),
            userHome.resolve(".local/bin"),
            userHome.resolve(".npm-global/bin")
        )
    }

    private fun executablePath(rawPath: String): String? = executablePath(Path.of(rawPath))

    private fun executablePath(path: Path): String? =
        path.takeIf { Files.isRegularFile(it) && Files.isExecutable(it) }
            ?.toAbsolutePath()
            ?.normalize()
            ?.toString()
}
