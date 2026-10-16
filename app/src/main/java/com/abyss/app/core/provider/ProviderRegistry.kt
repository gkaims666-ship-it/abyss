package com.abyss.app.core.provider

/** Every provider Abyss knows about. Keys are entered manually by the user. */
object ProviderRegistry {

    val openAi = OpenAiCompatProvider(
        id = "openai",
        displayName = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        models = listOf("gpt-4o", "gpt-4o-mini", "gpt-4.1", "o3-mini"),
    )

    val openRouter = OpenAiCompatProvider(
        id = "openrouter",
        displayName = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        models = listOf(
            "anthropic/claude-3.7-sonnet",
            "openai/gpt-4o",
            "google/gemini-2.0-flash-001",
            "meta-llama/llama-3.3-70b-instruct",
            "deepseek/deepseek-chat",
        ),
        extraHeaders = mapOf(
            "HTTP-Referer" to "https://abyss.app",
            "X-Title" to "Abyss",
        ),
    )

    val anthropic = AnthropicProvider()
    val gemini = GeminiProvider()

    /** On-device uncensored GGUF model via JNI (llama.cpp). No key/network. */
    val local = LocalLlmProvider()

    val all: List<LlmProvider> = listOf(openAi, anthropic, gemini, openRouter, local)

    fun byId(id: String): LlmProvider? = all.firstOrNull { it.id == id }
}
