package com.abyss.app.core.provider

import com.abyss.app.core.net.Http
import com.abyss.app.core.net.sseFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Covers any OpenAI-compatible /chat/completions endpoint. Used for both
 * OpenAI itself and OpenRouter (which proxies many models behind one key).
 */
class OpenAiCompatProvider(
    override val id: String,
    override val displayName: String,
    private val baseUrl: String,
    override val models: List<String>,
    override val freeModels: List<String> = emptyList(),
    override val signupUrl: String? = null,
    private val extraHeaders: Map<String, String> = emptyMap(),
) : LlmProvider {

    private val jsonMedia = "application/json".toMediaType()

    private fun body(request: LlmRequest): String = buildJsonObject {
        put("model", request.model)
        put("stream", request.stream)
        put("temperature", request.temperature)
        put("max_tokens", request.maxTokens)
        put("messages", buildJsonArray {
            request.systemPrompt?.let { sys ->
                addJsonObject { put("role", "system"); put("content", sys) }
            }
            request.messages.forEach { m ->
                addJsonObject {
                    put("role", when (m.role) {
                        Role.SYSTEM -> "system"
                        Role.USER -> "user"
                        Role.ASSISTANT -> "assistant"
                    })
                    put("content", m.content)
                }
            }
        })
    }.toString()

    private fun buildRequest(request: LlmRequest, apiKey: String): Request {
        val b = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body(request).toRequestBody(jsonMedia))
        extraHeaders.forEach { (k, v) -> b.addHeader(k, v) }
        return b.build()
    }

    override fun stream(request: LlmRequest, apiKey: String): Flow<LlmChunk> = flow {
        val req = buildRequest(request.copy(stream = true), apiKey)
        sseFlow(req).collect { event ->
            val data = event.data.trim()
            if (data == "[DONE]") {
                emit(LlmChunk("", done = true)); return@collect
            }
            if (data.isEmpty()) return@collect
            runCatching {
                val obj = Http.json.parseToJsonElement(data).jsonObject
                val delta = obj["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("delta")?.jsonObject
                    ?.get("content")?.jsonPrimitive?.content
                if (!delta.isNullOrEmpty()) emit(LlmChunk(delta))
            }
        }
        emit(LlmChunk("", done = true))
    }

    override suspend fun complete(request: LlmRequest, apiKey: String): String {
        val sb = StringBuilder()
        stream(request, apiKey).collect { if (!it.done) sb.append(it.delta) }
        return sb.toString()
    }
}
