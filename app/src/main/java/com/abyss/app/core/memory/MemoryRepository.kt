package com.abyss.app.core.memory

import com.abyss.app.core.data.MemoryDao
import com.abyss.app.core.data.MemoryEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * The knowledge base. Everything the background harvester learns from other
 * models, and everything offline reflection concludes, lands here — kept apart
 * from the visible chat so the main assistant stays fast and uncluttered.
 */
class MemoryRepository(private val dao: MemoryDao) {

    val count: Flow<Int> = dao.observeCount()

    suspend fun remember(kind: String, topic: String, source: String, content: String): Long =
        dao.insert(
            MemoryEntryEntity(kind = kind, topic = topic, source = source, content = content)
        )

    suspend fun recent(limit: Int = 50): List<MemoryEntryEntity> = dao.recent(limit)

    /**
     * Retrieve knowledge relevant to [queryText] for silent injection into a
     * prompt. Never surfaces in the chat transcript itself.
     *
     * Stage 1 uses lightweight term-overlap ranking over a recent pool. The
     * [MemoryEntryEntity.embedding] column and this method are the hook for
     * on-device vector search in a later stage.
     */
    suspend fun retrieve(queryText: String, limit: Int = 5): List<MemoryEntryEntity> {
        val terms = terms(queryText)
        val pool = dao.pool(300)
        if (terms.isEmpty()) return pool.take(limit)
        val ranked = pool
            .map { entry ->
                val hay = (entry.topic + " " + entry.content).lowercase()
                val score = terms.count { hay.contains(it) }
                entry to score
            }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<MemoryEntryEntity, Int>> { it.second }
                .thenByDescending { it.first.createdAt })
        return (if (ranked.isEmpty()) pool.map { it to 0 } else ranked)
            .take(limit).map { it.first }
    }

    /** Build a compact context block to prepend to a system prompt. */
    suspend fun contextBlock(queryText: String, limit: Int = 5): String {
        val hits = retrieve(queryText, limit)
        if (hits.isEmpty()) return ""
        return buildString {
            append("Relevant knowledge Abyss has accumulated:\n")
            hits.forEach { append("- [").append(it.topic).append("] ").append(it.content.take(400)).append('\n') }
        }
    }

    private fun terms(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd} ]"), " ")
            .split(' ')
            .map { it.trim() }
            .filter { it.length >= 3 }
            .distinct()
            .take(16)
}
