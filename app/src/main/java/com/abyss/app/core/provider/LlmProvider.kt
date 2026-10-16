package com.abyss.app.core.provider

import kotlinx.coroutines.flow.Flow

/**
 * One external model vendor. Keys are supplied per call (entered manually by
 * the user and stored encrypted), never baked in.
 */
interface LlmProvider {
    /** Stable id used for storage and routing, e.g. "openai". */
    val id: String
    val displayName: String

    /** Suggested models; the user may type any model string too. */
    val models: List<String>

    /** Stream a completion as text deltas. Throws [LlmError] on failure. */
    fun stream(request: LlmRequest, apiKey: String): Flow<LlmChunk>

    /** Convenience: collect the stream into a single string. */
    suspend fun complete(request: LlmRequest, apiKey: String): String
}
