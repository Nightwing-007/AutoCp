package com.github.pushpavel.autocp.database

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

class AutoCpExternalReloader : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
        val autocpEvents = events.asSequence()
            .filter { it is VFileCreateEvent || (it is VFileContentChangeEvent && !it.isFromSave) }
            .filter { it.path.endsWith("/.autocp") }
            .toList()
        if (autocpEvents.isEmpty()) return

        val openProjects = ProjectManager.getInstanceIfCreated()?.openProjects ?: return
        val affectedProjects = autocpEvents
            .map { it.path.removeSuffix("/.autocp") }
            .flatMap { parentPath ->
                openProjects.filter { !it.isDefault && FileUtil.pathsEqual(it.basePath, parentPath) }
            }
            .toSet()

        // defer disk I/O out of the write action that delivered the VFS events
        for (project in affectedProjects) {
            ApplicationManager.getApplication().invokeLater({
                project.service<AutoCpStorage>().reloadFromDisk()
            }, project.disposed)
        }
    }
}
