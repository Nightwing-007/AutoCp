package com.github.pushpavel.autocp.config.actions

import com.github.pushpavel.autocp.common.helpers.pathString
import com.github.pushpavel.autocp.common.res.R
import com.github.pushpavel.autocp.config.AutoCpConfig
import com.github.pushpavel.autocp.config.AutoCpExecutionTarget
import com.github.pushpavel.autocp.database.SolutionFiles
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import javax.swing.Icon

/**
 * Runs the currently focused solution file with an [AutoCpConfig] built on the fly.
 *
 * The configuration is created directly from the factory and never registered in
 * [RunManager], so AutoCp does not add entries to (or steal selection from) the
 * user's Run/Debug configurations.
 */
abstract class BaseRunAutoCpAction(text: String, description: String, icon: Icon) :
    AnAction(text, description, icon), DumbAware {

    abstract val configurationFactory: ConfigurationFactory

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val solutionPath = e.getData(CommonDataKeys.VIRTUAL_FILE)?.pathString
        if (project == null || solutionPath == null || solutionPath !in SolutionFiles.getInstance(project)) {
            R.notify.noConfigInContext()
            return
        }

        val settings = RunManager.getInstance(project).createConfiguration("AutoCp", configurationFactory)
        val configuration = settings.configuration as AutoCpConfig
        configuration.solutionFilePath = solutionPath
        configuration.suggestedName()?.let { configuration.name = it }

        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val runner = ProgramRunner.getRunner(executor.id, configuration)

        if (runner != null) {
            // Use custom ExecutionTarget to bypass CMake target checks
            val environment = ExecutionEnvironmentBuilder.create(executor, settings)
                .target(AutoCpExecutionTarget.getInstance())
                .build()
            runner.execute(environment)
        } else {
            // Fallback to default execution if no suitable runner found
            ExecutionUtil.runConfiguration(settings, executor)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null && e.getData(CommonDataKeys.VIRTUAL_FILE) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}
