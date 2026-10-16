package com.abyss.app.core.agent

import com.abyss.app.core.provider.LlmMessage
import com.abyss.app.core.provider.LlmRequest
import com.abyss.app.core.provider.Role

/**
 * Chooses which specialist handles a task when the user is in Auto mode.
 *
 * Primary path: ask a model to classify. Fallback: keyword heuristic, so Auto
 * still works offline or without keys.
 */
class Router(
    /** Completes a request against the user's active provider; may fail/return "". */
    private val completer: suspend (LlmRequest) -> String,
) {
    suspend fun route(task: String, model: String): Agent {
        val viaModel = runCatching { classifyWithModel(task, model) }.getOrNull()
        return viaModel ?: heuristic(task)
    }

    private suspend fun classifyWithModel(task: String, model: String): Agent? {
        val ids = AgentRegistry.specialists.joinToString(", ") { "${it.id} (${it.tagline})" }
        val prompt = "Available specialists: $ids.\n" +
            "Task:\n\"\"\"\n$task\n\"\"\"\n" +
            "Reply with ONLY the single best specialist id, nothing else."
        val answer = completer(
            LlmRequest(
                model = model,
                messages = listOf(LlmMessage(Role.USER, prompt)),
                systemPrompt = AgentRegistry.Orchestrator.systemPrompt,
                temperature = 0.0,
                maxTokens = 12,
                stream = false,
            )
        ).trim().lowercase()
        if (answer.isBlank()) return null
        return AgentRegistry.specialists.firstOrNull { answer.contains(it.id) }
    }

    private fun heuristic(task: String): Agent {
        val t = task.lowercase()
        val best = AgentRegistry.specialists
            .maxByOrNull { agent -> agent.keywords.count { t.contains(it) } }
        val score = best?.keywords?.count { t.contains(it) } ?: 0
        return if (score > 0 && best != null) best else AgentRegistry.General
    }
}
