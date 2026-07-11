package com.github.pushpavel.autocp.database

import com.github.pushpavel.autocp.database.models.Problem
import com.github.pushpavel.autocp.database.models.SolutionFile
import kotlinx.coroutines.flow.MutableStateFlow

class AutoCpDatabase(
    val problemsFlow: MutableStateFlow<Map<String, Map<String, Problem>>>,
    val solutionFilesFlow: MutableStateFlow<Map<String, SolutionFile>>
) {
    val problems get() = problemsFlow.value

    /**
     * true once the in-memory state diverged from what was loaded from disk,
     * so an intentionally emptied database is distinguishable from a never-populated one
     */
    @Volatile
    var mutated = false

    fun updateProblem(problem: Problem) {
        mutated = true
        val group = this.problems[problem.groupName]?.toMutableMap() ?: mutableMapOf()
        group[problem.name] = problem
        this.problemsFlow.value = problems.toMutableMap().apply { this[problem.groupName] = group }
    }

    fun modifySolutionFiles(action: MutableMap<String, SolutionFile>.() -> Unit) {
        mutated = true
        solutionFilesFlow.value = solutionFilesFlow.value.toMutableMap().apply(action)
    }
}