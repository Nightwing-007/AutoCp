package com.github.pushpavel.autocp.submit

/**
 * Maps a solution file extension to the normalized language token carried in
 * [SubmitData.language]. The browser extension keys its per-judge language
 * option matching on these tokens, so both sides must agree on them.
 */
object SubmitLanguage {

    private val byExtension = mapOf(
        "cpp" to "cpp",
        "cc" to "cpp",
        "cxx" to "cpp",
        "c++" to "cpp",
        "c" to "c",
        "rs" to "rust",
        "py" to "python",
        "java" to "java",
        "kt" to "kotlin",
        "kts" to "kotlin",
        "go" to "go",
        "cs" to "csharp",
        "js" to "javascript",
        "rb" to "ruby",
        "hs" to "haskell",
        "pas" to "pascal",
        "d" to "d",
        "ml" to "ocaml",
        "scala" to "scala",
        "php" to "php",
    )

    /** Returns the normalized token, or null when the extension is not recognized. */
    fun fromExtension(extension: String): String? = byExtension[extension.lowercase()]
}
