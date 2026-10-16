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
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Google Gemini (generativelanguage API), SSE streaming. */
class GeminiProvider(
    override val models: List<String> = listOf(
        "gemini-2.0-flash",
        "gemini-1.5-pro",
        "gemini-1.5-flash",
    ),
) : LlmProvider {
    override val id = "gemini"
    override val displayName = "Gemini"

    private val jsonMedia = "application/json".toMediaType()

    private fun body(request: LlmRequest): String = buildJsonObject {
        put("contents", buildJsonArray {
            request.messages.filter { it.role != Role.SYSTEM }.forEach { m ->
                addJsonObject {
                    put("role", if (m.role == Role.ASSISTANT) "model" else "user")
                    put("parts", buildJsonArray { addJsonObject { put("text", m.content) } })
                }
            }
        })
        request.systemPrompt?.let { sys ->
            putJsonObject("systemInstruction") {
                put("parts", buildJsonArray { addJsonObject { put("text", sys) } })
            }
        }
        putJsonObject("generationConfig") {
            put("temperature", request.temperature)
            put("maxOutputTokens", request.maxTokens)
        }
    }.toString()

    override fun stream(request: LlmRequest, apiKey: String): Flow<LlmChunk> = flow {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
            "${request.model}:streamGenerateContent?alt=sse&key=$apiKey"
        val req = Request.Builder()
            .url(url)
            .addHeader("content-type", "application/json")
            .post(body(request).toRequestBody(jsonMedia))
            .build()

        sseFlow(req).collect { event ->
            val data = event.data.trim()
            if (data.isEmpty()) return@collect
            runCatching {
                val obj = Http.json.parseToJsonElement(data).jsonObject
                val text = obj["candidates"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("text")?.jsonPrimitive?.content
                if (!text.isNullOrEmpty()) emit(LlmChunk(text))
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
