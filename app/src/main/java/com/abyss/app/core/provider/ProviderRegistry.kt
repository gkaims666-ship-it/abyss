package com.abyss.app.core.provider

/**
 * Every provider Abyss knows about.
 *
 * Honest free-vs-paid model:
 *  - [LocalLlmProvider] is the only genuinely **keyless** path — it runs on the
 *    phone, no account, no network.
 *  - Every cloud vendor's newest models are pre-listed and selectable now.
 *    The ones in each provider's `freeModels` run on that vendor's **free tier**
 *    — but the vendor still authenticates *your own* free key (free account,
 *    free key, no card). Abyss never bundles anyone else's credentials and never
 *    routes through unofficial "free" proxies, so free models activate the
 *    instant you paste a free key from the provider's `signupUrl`.
 *  - Paid models need a paid key for that same provider.
 */
object ProviderRegistry {

    /**
     * OpenRouter is the widest free door: one free key unlocks a rotating set of
     * `:free` models (newest DeepSeek, Llama, Gemini-exp, Qwen, …) at no cost.
     * Listed first so it is the obvious keyless-ish default alongside Local.
     */
    val openRouter = OpenAiCompatProvider(
        id = "openrouter",
        displayName = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        models = listOf(
            // free tier (free key)
            "deepseek/deepseek-chat-v3-0324:free",
            "deepseek/deepseek-r1-0528:free",
            "meta-llama/llama-3.3-70b-instruct:free",
            "google/gemini-2.0-flash-exp:free",
            "qwen/qwen3-235b-a22b:free",
            "mistralai/mistral-small-3.2-24b-instruct:free",
            // paid (keyed)
            "anthropic/claude-sonnet-4.5",
            "openai/gpt-5",
            "google/gemini-2.5-pro",
            "deepseek/deepseek-chat",
            "x-ai/grok-4",
        ),
        freeModels = listOf(
            "deepseek/deepseek-chat-v3-0324:free",
            "deepseek/deepseek-r1-0528:free",
            "meta-llama/llama-3.3-70b-instruct:free",
            "google/gemini-2.0-flash-exp:free",
            "qwen/qwen3-235b-a22b:free",
            "mistralai/mistral-small-3.2-24b-instruct:free",
        ),
        signupUrl = "https://openrouter.ai/keys",
        extraHeaders = mapOf(
            "HTTP-Referer" to "https://abyss.app",
            "X-Title" to "Abyss",
        ),
    )

    val gemini = GeminiProvider()

    val openAi = OpenAiCompatProvider(
        id = "openai",
        displayName = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        // OpenAI's API has no free tier; these are the current paid models.
        models = listOf("gpt-5", "gpt-5-mini", "gpt-4.1", "gpt-4o", "o4-mini", "o3"),
        signupUrl = "https://platform.openai.com/api-keys",
    )

    val anthropic = AnthropicProvider()

    val deepSeek = OpenAiCompatProvider(
        id = "deepseek",
        displayName = "DeepSeek",
        baseUrl = "https://api.deepseek.com",
        // No free tier, but by far the cheapest frontier models (pennies/M tokens).
        models = listOf("deepseek-chat", "deepseek-reasoner"),
        signupUrl = "https://platform.deepseek.com/api_keys",
    )

    /** On-device GGUF model via JNI (llama.cpp). No key, no network — the keyless default. */
    val local = LocalLlmProvider()

    // Order = how they appear in Settings: the free/keyless doors first.
    val all: List<LlmProvider> = listOf(openRouter, gemini, local, openAi, anthropic, deepSeek)

    fun byId(id: String): LlmProvider? = all.firstOrNull { it.id == id }
}
