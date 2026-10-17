package com.abyss.app.core.debug

import com.abyss.app.core.keys.KeyStore
import com.abyss.app.core.net.Http
import com.abyss.app.core.provider.LlmMessage
import com.abyss.app.core.provider.LlmProvider
import com.abyss.app.core.provider.LlmRequest
import com.abyss.app.core.provider.ProviderRegistry
import com.abyss.app.core.provider.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Request
import java.io.IOException

/**
 * The in-app self-review. A strong coding model reads the app's own source
 * **straight from GitHub** (the public repo, raw files — the same code that is
 * running), is handed this device's live performance numbers and a short health
 * summary, and streams back a review: concrete flaws ranked by impact, each with
 * the file, what's wrong, and a proposed change.
 *
 * It uses whichever capable model you already have a key for (or the on-device
 * model), so it needs no special credential of its own and ships none.
 */
object CodeReviewer {

    private const val RAW = "https://raw.githubusercontent.com/gkaims666-ship-it/abyss/main/"

    /** The files most worth auditing — the hot rendering path, shell, providers. */
    private val FILES = listOf(
        "app/src/main/java/com/abyss/app/ui/background/AbyssBackground.kt",
        "app/src/main/java/com/abyss/app/ui/MainActivity.kt",
        "app/src/main/java/com/abyss/app/ui/components/Intro.kt",
        "app/src/main/java/com/abyss/app/core/provider/ProviderRegistry.kt",
        "app/src/main/java/com/abyss/app/core/debug/PerfMonitor.kt",
    )

    /** Cap per file and overall so the prompt stays within model limits. */
    private const val PER_FILE = 22_000
    private const val TOTAL = 90_000

    data class Pick(val provider: LlmProvider, val model: String, val key: String)

    /**
     * Pick the strongest coding model we can actually call: a keyed frontier
     * reasoner first, else an OpenRouter free reasoner, else the on-device model.
     */
    fun pick(keyStore: KeyStore): Pick? {
        fun keyed(id: String) = keyStore.getKey(id)?.takeIf { it.isNotBlank() }
        // (provider id, model) in order of coding strength.
        val order = listOf(
            "anthropic" to "claude-sonnet-4-5",
            "deepseek" to "deepseek-reasoner",
            "openai" to "gpt-5",
            "gemini" to "gemini-2.5-pro",
            "openrouter" to "deepseek/deepseek-r1-0528:free",
        )
        for ((pid, model) in order) {
            val k = keyed(pid) ?: continue
            val p = ProviderRegistry.byId(pid) ?: continue
            return Pick(p, model, k)
        }
        // No cloud key — fall back to the keyless on-device model.
        ProviderRegistry.byId("local")?.let { return Pick(it, it.models.first(), "") }
        return null
    }

    private fun fetch(path: String): String {
        val req = Request.Builder().url(RAW + path).get().build()
        Http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("GitHub ${resp.code} for $path")
            val body = resp.body?.string().orEmpty()
            return if (body.length > PER_FILE) body.take(PER_FILE) + "\n… [truncated]\n" else body
        }
    }

    private val SYSTEM = """
        You are a senior Android/Kotlin + Jetpack Compose performance engineer reviewing the Abyss app.
        You are reading its real source from GitHub and its live on-device metrics. Be specific and honest.
        Prioritise: dropped frames / jank on the render hot path, needless allocations per frame,
        recomposition scope, shader cost, and correctness bugs. For each finding give: (1) file and rough
        location, (2) the problem, (3) a concrete proposed change (a short diff or snippet). Rank findings
        by impact. End with a 'Quick wins' list. Do not invent files you were not shown.
    """.trimIndent()

    /**
     * Run the review. Emits progress lines, then streams the model's markdown.
     * Each emission is the *delta* to append to the output.
     */
    fun review(keyStore: KeyStore): Flow<String> = flow {
        val p = pick(keyStore)
        if (p == null) {
            emit("No model available. Add a free key for any provider in Settings, or enable the on-device model, then try again.")
            return@flow
        }
        emit("▸ Reviewer: ${p.provider.displayName} · ${p.model}\n")
        emit("▸ Reading source from GitHub…\n")

        val sb = StringBuilder()
        sb.append("# ON-DEVICE PERFORMANCE\n").append(PerfMonitor.report()).append("\n\n")
        sb.append("# SOURCE (from GitHub, live repo)\n")
        var total = 0
        for (path in FILES) {
            if (total > TOTAL) break
            val code = runCatching { fetch(path) }.getOrElse { "// could not fetch: ${it.message}" }
            total += code.length
            sb.append("\n## ").append(path).append("\n```kotlin\n").append(code).append("\n```\n")
        }
        emit("▸ Sent ${FILES.size} files (${total / 1000} KB). Waiting for the review…\n\n")

        val request = LlmRequest(
            model = p.model,
            messages = listOf(LlmMessage(Role.USER, sb.toString())),
            temperature = 0.3,
            maxTokens = 4096,
            stream = true,
            systemPrompt = SYSTEM,
        )
        emitAll(
            flow {
                p.provider.stream(request, p.key).collect { if (!it.done && it.delta.isNotEmpty()) emit(it.delta) }
            },
        )
    }.flowOn(Dispatchers.IO)
}
