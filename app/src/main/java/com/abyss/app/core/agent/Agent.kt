package com.abyss.app.core.agent

/**
 * A specialized assistant persona. The user can pick one explicitly, or let
 * the Auto router choose per task.
 */
data class Agent(
    val id: String,
    val name: String,
    val tagline: String,
    val systemPrompt: String,
    /** Keywords the heuristic router matches when no model is available. */
    val keywords: List<String> = emptyList(),
)

object AgentRegistry {

    val Orchestrator = Agent(
        id = "auto",
        name = "Auto",
        tagline = "Routes each task to the best specialist",
        systemPrompt = "You are Abyss's orchestrator. Analyze the user's task and " +
            "dispatch it to the most suitable specialist agent. Reply with only the agent id.",
    )

    val General = Agent(
        id = "general",
        name = "General",
        tagline = "Everyday assistant",
        systemPrompt = "You are Abyss, a sharp, direct, capable assistant. Be concise and useful.",
        keywords = emptyList(),
    )

    val Coder = Agent(
        id = "coder",
        name = "Coder",
        tagline = "Code, debugging, architecture",
        systemPrompt = "You are Abyss Coder. You write correct, idiomatic code and explain " +
            "trade-offs briefly. Prefer complete, runnable snippets. Point out bugs precisely.",
        keywords = listOf("code", "bug", "compile", "function", "kotlin", "python", "java",
            "api", "gradle", "stacktrace", "error", "regex", "код", "ошибк", "функци", "скрипт"),
    )

    val Researcher = Agent(
        id = "researcher",
        name = "Researcher",
        tagline = "Deep research & synthesis",
        systemPrompt = "You are Abyss Researcher. You investigate thoroughly, weigh sources, " +
            "and synthesize clear, structured findings with caveats where evidence is thin.",
        keywords = listOf("research", "compare", "find", "sources", "history", "explain why",
            "analy", "исследуй", "сравни", "найди", "почему", "анализ"),
    )

    val MediaAnalyst = Agent(
        id = "media",
        name = "Media",
        tagline = "Audio, video & image understanding",
        systemPrompt = "You are Abyss Media. You interpret transcripts and descriptions of " +
            "audio, video and images, and answer precisely about what was said and shown.",
        keywords = listOf("audio", "video", "image", "photo", "transcribe", "voice", "record",
            "аудио", "видео", "картинк", "фото", "голос", "запись", "кружочек"),
    )

    val Reflector = Agent(
        id = "reflector",
        name = "Reflector",
        tagline = "Offline self-review",
        systemPrompt = "You are Abyss's reflective core. Critique prior answers, find gaps and " +
            "errors, and produce improved, distilled knowledge to store. Be rigorous and honest.",
    )

    /** Agents the user can pick from the selector (Auto first). */
    val selectable: List<Agent> = listOf(Orchestrator, General, Coder, Researcher, MediaAnalyst)

    /** All specialists the router may dispatch to (excludes Auto itself). */
    val specialists: List<Agent> = listOf(General, Coder, Researcher, MediaAnalyst)

    fun byId(id: String?): Agent =
        (selectable + Reflector).firstOrNull { it.id == id } ?: General
}
