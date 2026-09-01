package com.github.h2a.claudeusage

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import java.awt.Toolkit
import java.io.File

/**
 * The chimes that mark the status bar switching into and out of credit spend.
 *
 * The two directions get different sounds deliberately: the point of a sound is to save you looking,
 * and one tone for both events would still leave you checking which way it went. Submarine's low
 * note reads as "money is now going out", Glass's bright one as "back on included usage".
 *
 * macOS gets real system sounds because the AWT beep is easy to miss and is silenced outright by the
 * "Play user interface sound effects" preference, which would make the setting look broken.
 * Everywhere else falls back to the beep, which at least fires.
 *
 * Never throws. A missing player, a locked-down process or a headless run must not take down a usage
 * refresh - the sound is the least important thing happening on that thread.
 */
object UsageSound {
    private val LOG = Logger.getInstance(UsageSound::class.java)

    private const val MAC_PLAYER = "/usr/bin/afplay"
    private const val SOUND_CREDITS_STARTED = "/System/Library/Sounds/Submarine.aiff"
    private const val SOUND_CREDITS_ENDED = "/System/Library/Sounds/Glass.aiff"

    /** The displayed window is exhausted; usage now bills to credits. */
    fun creditsStarted() = play(SOUND_CREDITS_STARTED)

    /** The window reset; included usage is available again. */
    fun creditsEnded() = play(SOUND_CREDITS_ENDED)

    private fun play(sound: String) {
        try {
            if (SystemInfo.isMac && File(MAC_PLAYER).canExecute() && File(sound).isFile) {
                // Fire and forget - waiting on playback would stall the refresh scheduler.
                ProcessBuilder(MAC_PLAYER, sound)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            } else {
                Toolkit.getDefaultToolkit().beep()
            }
        } catch (e: Exception) {
            LOG.warn("Could not play the usage sound: ${Secrets.safeMessage(e)}")
        }
    }
}
