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

    /**
     * The subset of [models] that this vendor serves on a **free tier**. These
     * are the newest models you can run at no cost — but the vendor still needs
     * *your own* free key (free account, free key, no card). Abyss never ships
     * someone else's credentials, so these light up the moment you paste a free
     * key from [signupUrl]. Empty = this vendor has no free API tier.
     */
    val freeModels: List<String> get() = emptyList()

    /** True only when the model needs no key at all (the on-device engine). */
    val keyless: Boolean get() = false

    /** Where to get a key — opened by the "Get a free key" link. */
    val signupUrl: String? get() = null

    /** Stream a completion as text deltas. Throws [LlmError] on failure. */
    fun stream(request: LlmRequest, apiKey: String): Flow<LlmChunk>

    /** Convenience: collect the stream into a single string. */
    suspend fun complete(request: LlmRequest, apiKey: String): String
}
