package com.abyss.app.core.improve

/**
 * A rotating self-improvement curriculum across many domains. The harvester
 * walks this list, asking external models to teach Abyss something new in each
 * area, so its knowledge base grows broadly over time.
 */
object Curriculum {
    val topics: List<String> = listOf(
        "advanced reasoning & problem decomposition",
        "software architecture patterns",
        "debugging techniques across languages",
        "algorithms & data structures",
        "mathematics for programming",
        "prompt engineering & tool use",
        "systems & networking",
        "security & threat modeling",
        "data analysis & statistics",
        "machine learning fundamentals",
        "natural language understanding",
        "writing & rhetoric",
        "creativity & ideation methods",
        "negotiation & persuasion",
        "productivity & planning",
        "finance & economics literacy",
        "physics & engineering intuition",
        "biology & medicine basics",
        "history & geopolitics",
        "philosophy & critical thinking",
        "design & UX principles",
        "memory & learning strategies",
        "languages & translation nuance",
        "edge cases & failure analysis",
    )

    fun topicFor(index: Int): String = topics[((index % topics.size) + topics.size) % topics.size]

    fun question(topic: String): String =
        "Teach Abyss one high-signal, non-obvious thing about \"$topic\". " +
        "Give a concrete technique, rule of thumb, or fact it can reuse later. " +
        "Be concise (a few sentences), no preamble."
}
