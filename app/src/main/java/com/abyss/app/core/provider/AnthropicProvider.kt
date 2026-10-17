package com.abyss.app.core.provider

import com.abyss.app.core.net.Http
import com.abyss.app.core.net.sseFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Anthropic Messages API (/v1/messages). */
class AnthropicProvider(
    override val models: List<String> = listOf(
        "claude-sonnet-4-5",
        "claude-opus-4-1",
        "claude-opus-4-20250514",
        "claude-sonnet-4-20250514",
        "claude-3-7-sonnet-latest",
        "claude-3-5-haiku-20241022",
    ),
) : LlmProvider {
    override val id = "anthropic"
    override val displayName = "Anthropic"
    // Anthropic's API has no free tier — keyed access only.
    override val signupUrl = "https://console.anthropic.com/settings/keys"

    private val jsonMedia = "application/json".toMediaType()

    private fun body(request: LlmRequest): String = buildJsonObject {
        put("model", request.model)
        put("max_tokens", request.maxTokens)
        put("temperature", request.temperature)
        put("stream", request.stream)
        request.systemPrompt?.let { put("system", it) }
        put("messages", buildJsonArray {
            request.messages.filter { it.role != Role.SYSTEM }.forEach { m ->
                addJsonObject {
                    put("role", if (m.role == Role.ASSISTANT) "assistant" else "user")
                    put("content", m.content)
                }
            }
        })
    }.toString()

    override fun stream(request: LlmRequest, apiKey: String): Flow<LlmChunk> = flow {
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body(request.copy(stream = true)).toRequestBody(jsonMedia))
            .build()

        sseFlow(req).collect { event ->
            val data = event.data.trim()
            if (data.isEmpty()) return@collect
            runCatching {
                val obj = Http.json.parseToJsonElement(data).jsonObject
                when (obj["type"]?.jsonPrimitive?.content) {
                    "content_block_delta" -> {
                        val text = obj["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.content
                        if (!text.isNullOrEmpty()) emit(LlmChunk(text))
                    }
                    "message_stop" -> emit(LlmChunk("", done = true))
                }
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
