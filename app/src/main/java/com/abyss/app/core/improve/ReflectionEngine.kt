package com.abyss.app.core.improve

import com.abyss.app.core.keys.KeyStore
import com.abyss.app.core.local.LocalModelManager
import com.abyss.app.core.memory.MemoryRepository
import com.abyss.app.core.provider.LlmMessage
import com.abyss.app.core.provider.LlmRequest
import com.abyss.app.core.provider.ProviderRegistry
import com.abyss.app.core.provider.Role
import com.abyss.app.core.settings.SettingsStore

/**
 * Offline self-reflection. Takes recent gathered knowledge and distills a
 * single improved insight, correcting errors and connecting ideas. Prefers the
 * on-device model (so it works with no internet); falls back to an online
 * model only when no local model is loaded and a key is available.
 */
class ReflectionEngine(
    private val keyStore: KeyStore,
    private val memory: MemoryRepository,
    private val settingsStore: SettingsStore,
    private val localModel: LocalModelManager,
) {
    data class Result(val stored: Boolean, val usedLocal: Boolean, val reason: String? = null)

    suspend fun runOnce(): Result {
        val notes = memory.recent(12)
        if (notes.isEmpty()) return Result(false, false, "No knowledge yet to reflect on")

        val material = notes.joinToString("\n") { "- [${it.topic}] ${it.content.take(300)}" }
        val prompt = "These are notes Abyss has gathered:\n$material\n\n" +
            "Reflect: find the single most useful cross-cutting insight, or correct the most " +
            "important error, and write ONE concise improved note Abyss should remember. " +
            "Output only that note."

        // Prefer local (works offline).
        if (localModel.isLoaded()) {
            val out = runCatching { localModel.generate(prompt, maxTokens = 400, temperature = 0.6f) }
                .getOrNull()?.trim()
            if (!out.isNullOrBlank()) {
                memory.remember("reflection", "self-reflection", "Local model", out)
                settingsStore.bumpImprovement()
                return Result(true, usedLocal = true)
            }
        }

        // Online fallback.
        val provider = ProviderRegistry.all.firstOrNull { it.id != "local" && keyStore.hasKey(it.id) }
            ?: return Result(false, false, "Offline and no local model loaded")
        val key = keyStore.getKey(provider.id) ?: return Result(false, false, "No key")
        val out = runCatching {
            provider.complete(
                LlmRequest(
                    model = provider.models.firstOrNull() ?: "",
                    messages = listOf(LlmMessage(Role.USER, prompt)),
                    temperature = 0.6, maxTokens = 400, stream = false,
                ), key,
            )
        }.getOrNull()?.trim() ?: return Result(false, false, "Reflection call failed")
        if (out.isBlank()) return Result(false, false, "Empty reflection")
        memory.remember("reflection", "self-reflection", provider.displayName, out)
        settingsStore.bumpImprovement()
        return Result(true, usedLocal = false)
    }
}
