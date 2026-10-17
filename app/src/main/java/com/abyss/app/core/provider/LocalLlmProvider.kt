package com.abyss.app.core.provider

import com.abyss.app.core.local.LlamaBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Runs the on-device uncensored GGUF model via [LlamaBridge]. No network, no
 * key. Generation is blocking under the hood; we emit it word-by-word so the
 * chat UI still streams.
 */
class LocalLlmProvider : LlmProvider {
    override val id = "local"
    override val displayName = "Local (on-device)"
    override val models = listOf("local-gguf")
    override val keyless = true

    override fun stream(request: LlmRequest, apiKey: String): Flow<LlmChunk> = flow {
        if (!LlamaBridge.available)
            throw LlmError.Network(RuntimeException("Local model not built into this APK (opt-in)"))
        if (!LlamaBridge.isLoaded)
            throw LlmError.Network(RuntimeException("No local model loaded — download & select one in Core"))

        val prompt = buildPrompt(request)
        val out = LlamaBridge.generate(prompt, request.maxTokens, request.temperature.toFloat())
        // Emit in small pieces so the UI animates like a stream.
        val pieces = out.split(Regex("(?<= )"))
        for (p in pieces) { if (p.isNotEmpty()) emit(LlmChunk(p)) }
        emit(LlmChunk("", done = true))
    }.flowOn(Dispatchers.Default)

    override suspend fun complete(request: LlmRequest, apiKey: String): String {
        val sb = StringBuilder()
        stream(request, apiKey).collect { if (!it.done) sb.append(it.delta) }
        return sb.toString()
    }

    /** ChatML-style prompt that most instruct GGUF models accept. */
    private fun buildPrompt(request: LlmRequest): String = buildString {
        request.systemPrompt?.let { append("<|im_start|>system\n").append(it).append("<|im_end|>\n") }
        request.messages.forEach { m ->
            val role = when (m.role) {
                Role.SYSTEM -> "system"; Role.USER -> "user"; Role.ASSISTANT -> "assistant"
            }
            append("<|im_start|>").append(role).append('\n').append(m.content).append("<|im_end|>\n")
        }
        append("<|im_start|>assistant\n")
    }
}
