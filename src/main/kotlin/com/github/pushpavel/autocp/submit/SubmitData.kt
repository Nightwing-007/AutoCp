package com.github.pushpavel.autocp.submit

import kotlinx.serialization.Serializable

/**
 * Payload of a submission handed to the browser extension.
 * Matches cph-ng's `SubmitData` (packages/core/src/protocol.ts) so the
 * "CPH-NG Submit" browser extension can consume it unchanged.
 */
@Serializable
data class SubmitData(
    val url: String,
    val sourceCode: String,
)
