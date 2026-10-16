package com.abyss.app.core.provider

enum class Role { SYSTEM, USER, ASSISTANT }

data class LlmMessage(val role: Role, val content: String)

data class LlmRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
    val stream: Boolean = true,
    val systemPrompt: String? = null,
)

/** A streamed piece of a response. [done] marks the final emission. */
data class LlmChunk(val delta: String, val done: Boolean = false)

sealed class LlmError(message: String) : Exception(message) {
    class MissingKey(provider: String) : LlmError("No API key set for $provider")
    class Http(val code: Int, body: String) : LlmError("HTTP $code: ${body.take(400)}")
    class Network(cause: Throwable) : LlmError(cause.message ?: "Network error")
    class Parse(message: String) : LlmError("Parse error: $message")
}
