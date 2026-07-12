package com.github.pushpavel.autocp.submit

import kotlinx.serialization.Serializable

/**
 * Payload of a submission handed to the browser extension.
 * Matches cph-ng's `SubmitData` (packages/core/src/protocol.ts) so the
 * "CPH-NG Submit" browser extension can consume it unchanged.
 *
 * [language] is an AutoCp extension to that protocol: a normalized token
 * (see [SubmitLanguage]) the extension uses to pick the language option on
 * the judge's submit form. It defaults to null and is then omitted from the
 * JSON, so extensions that only know the cph-ng shape are unaffected.
 */
@Serializable
data class SubmitData(
    val url: String,
    val sourceCode: String,
    val language: String? = null,
)
