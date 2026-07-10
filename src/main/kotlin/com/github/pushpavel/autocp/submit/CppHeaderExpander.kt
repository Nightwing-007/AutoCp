package com.github.pushpavel.autocp.submit

import java.nio.file.Files
import java.nio.file.Path

/**
 * Inlines local `#include "..."` headers so the submitted code is self-contained,
 * mirroring cph-ng's C++ header expansion. System includes (`<...>`) are left untouched.
 */
object CppHeaderExpander {
    private val includeRegex = Regex("""^\s*#\s*include\s*"([^"]+)"\s*(//.*)?$""")
    private val cppExtensions = setOf("c", "cc", "cpp", "cxx", "c++", "h", "hh", "hpp")

    /** @return the expanded source, or null when [filePath] is not C/C++ or nothing was expanded */
    fun expandIfCpp(filePath: Path, sourceCode: String): String? {
        val extension = filePath.fileName.toString().substringAfterLast('.', "").lowercase()
        if (extension !in cppExtensions) return null

        val visited = mutableSetOf(normalize(filePath))
        var expandedAny = false

        fun expand(baseDir: Path?, text: String, isRoot: Boolean): String = buildString {
            for (line in text.trimEnd('\n').lines()) {
                val match = includeRegex.matchEntire(line)
                val target = if (match != null && baseDir != null)
                    normalize(baseDir.resolve(match.groupValues[1]))
                else null

                when {
                    target != null && Files.isRegularFile(target) -> {
                        expandedAny = true
                        // repeated includes are dropped, like a header guard would
                        if (visited.add(target))
                            append(expand(target.parent, Files.readString(target), false))
                    }
                    !isRoot && line.trim() == "#pragma once" -> {}
                    else -> appendLine(line)
                }
            }
        }

        val result = expand(filePath.parent, sourceCode, true)
        return if (expandedAny) result else null
    }

    private fun normalize(path: Path): Path = path.toAbsolutePath().normalize()
}
