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
            throw LlmError.Network(RuntimeException(
                "This build doesn't include the on-device engine. Pick Google AI Studio for an instant free model."))

        // Not loaded yet → make sure provisioning is running and report progress
        // honestly rather than crashing. The download happens once, then it's
        // fully offline with no key.
        if (!LlamaBridge.isLoaded) {
            val mgr = com.abyss.app.core.ServiceLocator.localModel
            mgr.autoStart()
            if (!mgr.ready()) {
                val p = mgr.progress.value
                val pct = p?.takeIf { it.total > 0 }?.let { (it.bytes * 100 / it.total).toInt() }
                val where = if (pct != null) "downloading the on-device model ($pct%)"
                            else "setting up the on-device model"
                emit(LlmChunk(
                    "I'm $where. It downloads once (~1.1 GB) and then runs fully offline with no key — " +
                    "watch progress in Core. For an instant free model right now, open Settings → Model and " +
                    "pick Google AI Studio (one free key, no card)."))
                emit(LlmChunk("", done = true))
                return@flow
            }
        }

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
