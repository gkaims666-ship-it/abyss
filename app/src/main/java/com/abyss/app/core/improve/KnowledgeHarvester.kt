package com.abyss.app.core.improve

import com.abyss.app.core.keys.KeyStore
import com.abyss.app.core.memory.MemoryRepository
import com.abyss.app.core.provider.LlmMessage
import com.abyss.app.core.provider.LlmRequest
import com.abyss.app.core.provider.ProviderRegistry
import com.abyss.app.core.provider.Role
import com.abyss.app.core.settings.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * The recursive self-improvement loop. Quietly asks whichever external models
 * have keys to teach Abyss something new across a rotating curriculum, and
 * stores every answer in the knowledge base — never shown in the main chat.
 * Accumulated knowledge is then injected (RAG) into normal conversations, so
 * Abyss effectively keeps learning. Respects a per-run budget.
 */
class KnowledgeHarvester(
    private val keyStore: KeyStore,
    private val memory: MemoryRepository,
    private val settingsStore: SettingsStore,
) {
    data class Result(val stored: Int, val skippedNoKeys: Boolean)

    suspend fun runOnce(perRun: Int): Result {
        val providers = ProviderRegistry.all.filter { it.id != "local" && keyStore.hasKey(it.id) }
        if (providers.isEmpty()) return Result(0, skippedNoKeys = true)

        val base = settingsStore.settings.first().improvementCount
        var stored = 0
        repeat(perRun.coerceIn(1, 10)) { i ->
            val topic = Curriculum.topicFor(base + stored)
            val provider = providers[(base + i) % providers.size]
            val key = keyStore.getKey(provider.id) ?: return@repeat
            val answer = runCatching {
                provider.complete(
                    LlmRequest(
                        model = provider.models.firstOrNull() ?: return@repeat,
                        messages = listOf(LlmMessage(Role.USER, Curriculum.question(topic))),
                        temperature = 0.7, maxTokens = 400, stream = false,
                    ), key,
                )
            }.getOrNull()
            if (!answer.isNullOrBlank()) {
                memory.remember("harvest", topic, provider.displayName, answer.trim())
                settingsStore.bumpImprovement()
                stored++
            }
        }
        return Result(stored, skippedNoKeys = false)
    }
}
