package com.github.h2a.claudeusage

import java.lang.reflect.InvocationTargetException
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusBarWidgetAccessibilityTest {

    @Test
    fun `Claude status widget initializes its accessible name without crashing`() {
        assertAccessibleComponent(
            ownerClass = ClaudeUsageStatusBarWidget::class.java,
            componentClassName = "com.github.h2a.claudeusage.ClaudeUsageStatusBarWidget\$UsageWidgetComponent",
            expectedName = "Claude usage"
        )
    }

    @Test
    fun `Codex status widget initializes its accessible name without crashing`() {
        assertAccessibleComponent(
            ownerClass = CodexUsageStatusBarWidget::class.java,
            componentClassName = "com.github.h2a.claudeusage.CodexUsageStatusBarWidget\$UsageWidgetComponent",
            expectedName = "Codex usage"
        )
    }

    private fun assertAccessibleComponent(
        ownerClass: Class<*>,
        componentClassName: String,
        expectedName: String
    ) {
        val owner = allocateWithoutConstructor(ownerClass)
        val componentClass = Class.forName(componentClassName)
        val constructor = componentClass.getDeclaredConstructor(ownerClass).apply {
            isAccessible = true
        }
        val result = runCatching { constructor.newInstance(owner) as JComponent }

        assertTrue(
            result.isSuccess,
            "Status widget component initialization failed: ${result.exceptionOrNull()?.rootCause()}"
        )
        assertEquals(expectedName, result.getOrThrow().getAccessibleContext().accessibleName)
    }

    private fun allocateWithoutConstructor(type: Class<*>): Any {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = field.get(null)
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type)
    }

    private fun Throwable.rootCause(): Throwable {
        var current = this
        while (current is InvocationTargetException && current.cause != null) {
            current = current.cause!!
        }
        return current
    }
}
