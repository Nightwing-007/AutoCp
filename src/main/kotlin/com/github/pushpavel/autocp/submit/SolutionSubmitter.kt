package com.github.pushpavel.autocp.submit

import com.github.pushpavel.autocp.common.res.R
import com.github.pushpavel.autocp.database.SolutionFiles
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.net.URI
import kotlin.io.path.Path
import kotlin.io.path.extension

/**
 * Resolves a solution file to its linked problem and hands `{url, sourceCode}`
 * to the browser extension via [SubmitBridge].
 */
object SolutionSubmitter {

    fun submit(project: Project, pathString: String) {
        val solutionFile = SolutionFiles.getInstance(project)[pathString]
            ?: return R.notify.submitFileNotEnabled()
        val problem = solutionFile.getLinkedProblem(project)
            ?: return R.notify.submitNoLinkedProblem()
        if (problem.url.isBlank() || runCatching { URI(problem.url).toURL() }.isFailure)
            return R.notify.submitInvalidUrl(problem.url)

        val bridge = SubmitBridge.getInstance()
        if (!bridge.isBrowserConnected)
            return R.notify.submitBrowserNotConnected()

        val virtualFile = LocalFileSystem.getInstance().findFileByPath(pathString)
            ?: return R.notify.submitFileNotEnabled()
        val sourceCode = FileDocumentManager.getInstance().getDocument(virtualFile)?.text
            ?: String(virtualFile.contentsToByteArray(), virtualFile.charset)
        if (sourceCode.isBlank())
            return R.notify.submitEmptySource()

        val code = runCatching { CppHeaderExpander.expandIfCpp(Path(pathString), sourceCode) }.getOrNull()
            ?: sourceCode
        val language = SubmitLanguage.fromExtension(Path(pathString).extension)

        if (bridge.submit(SubmitData(problem.url, code, language)))
            R.notify.submitSent(problem.name)
        else
            R.notify.submitBrowserNotConnected()
    }
}
