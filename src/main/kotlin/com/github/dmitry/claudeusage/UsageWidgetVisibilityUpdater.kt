package com.github.h2a.claudeusage

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager

object UsageWidgetVisibilityUpdater {
    fun update(project: Project) {
        if (project.isDisposed) return
        val statusBar = WindowManager.getInstance().getStatusBar(project) ?: return
        val settings = ClaudeUsageSettings.getInstance().state

        sync(
            enabled = settings.showClaudeWidget,
            isInstalled = statusBar.getWidget(ClaudeUsageStatusBarWidgetFactory.WIDGET_ID) != null,
            add = {
                statusBar.addWidget(
                    ClaudeUsageStatusBarWidgetFactory().createWidget(project),
                    "after positionWidget",
                    project
                )
            },
            remove = { statusBar.removeWidget(ClaudeUsageStatusBarWidgetFactory.WIDGET_ID) }
        )
        sync(
            enabled = settings.showCodexWidget,
            isInstalled = statusBar.getWidget(CodexUsageStatusBarWidgetFactory.WIDGET_ID) != null,
            add = {
                statusBar.addWidget(
                    CodexUsageStatusBarWidgetFactory().createWidget(project),
                    "after ${ClaudeUsageStatusBarWidgetFactory.WIDGET_ID}",
                    project
                )
            },
            remove = { statusBar.removeWidget(CodexUsageStatusBarWidgetFactory.WIDGET_ID) }
        )
    }

    internal fun sync(
        enabled: Boolean,
        isInstalled: Boolean,
        add: () -> Unit,
        remove: () -> Unit
    ) {
        when {
            enabled && !isInstalled -> add()
            !enabled && isInstalled -> remove()
        }
    }
}
