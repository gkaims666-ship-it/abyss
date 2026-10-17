package com.abyss.app.core.provider

/** Every provider Abyss knows about. Keys are entered manually by the user. */
object ProviderRegistry {

    val openAi = OpenAiCompatProvider(
        id = "openai",
        displayName = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        models = listOf("gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini", "o3", "o4-mini"),
    )

    val deepSeek = OpenAiCompatProvider(
        id = "deepseek",
        displayName = "DeepSeek",
        baseUrl = "https://api.deepseek.com",
        models = listOf("deepseek-chat", "deepseek-reasoner"),
    )

    /**
     * OpenRouter proxies many models behind one key. The `:free` models run on a
     * free OpenRouter key — the genuinely free cloud path. The rest need a paid
     * key; what you can actually call is whatever your own key is entitled to.
     */
    val openRouter = OpenAiCompatProvider(
        id = "openrouter",
        displayName = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        models = listOf(
            // free tier (free key)
            "deepseek/deepseek-chat-v3-0324:free",
            "deepseek/deepseek-r1:free",
            "meta-llama/llama-3.3-70b-instruct:free",
            "google/gemini-2.0-flash-exp:free",
            "qwen/qwen-2.5-72b-instruct:free",
            // paid (keyed)
            "anthropic/claude-3.7-sonnet",
            "openai/gpt-4.1",
            "google/gemini-2.5-pro",
            "deepseek/deepseek-chat",
        ),
        extraHeaders = mapOf(
            "HTTP-Referer" to "https://abyss.app",
            "X-Title" to "Abyss",
        ),
    )

    val anthropic = AnthropicProvider()
    val gemini = GeminiProvider()

    /** On-device GGUF model via JNI (llama.cpp). No key, no network — the keyless default. */
    val local = LocalLlmProvider()

    val all: List<LlmProvider> = listOf(openAi, anthropic, gemini, deepSeek, openRouter, local)

    fun byId(id: String): LlmProvider? = all.firstOrNull { it.id == id }
}
