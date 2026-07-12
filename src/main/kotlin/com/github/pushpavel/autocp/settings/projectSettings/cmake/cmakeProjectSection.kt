package com.github.pushpavel.autocp.settings.projectSettings.cmake

import com.github.pushpavel.autocp.common.res.R
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import java.nio.file.Paths
import kotlin.io.path.exists

fun Panel.cmakeProjectSection(project: Project) {
    val cmakeSettings = project.cmakeSettings()
    // basePath is null for the default project (settings opened with no project open)
    val cmakeFile = project.basePath?.let { Paths.get(it, ".autocp") }
    if (ApplicationInfo.getInstance().build.productCode == "CL" || cmakeFile?.exists() == true)
        row {
            checkBox(R.strings.addToCMakeMsg).bindSelected(
                { cmakeSettings.addToCMakeLists },
                { cmakeSettings.addToCMakeLists = it }
            )
        }
}