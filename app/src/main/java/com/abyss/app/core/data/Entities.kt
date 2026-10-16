package com.abyss.app.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A single turn in a visible conversation. */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val role: String,          // "user" | "assistant"
    val content: String,
    val providerId: String?,
    val model: String?,
    val agentId: String?,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * A learned fact kept in the knowledge base. Written by the background
 * self-improvement worker (answers harvested from other models) and by offline
 * self-reflection. Deliberately separate from [ChatMessageEntity] so the main
 * chat stays light and usable as an ordinary assistant.
 *
 * [kind]: "harvest" (from another model) | "reflection" (offline self-review).
 * [embedding] is reserved for on-device vector search in a later stage.
 */
@Entity(tableName = "memory")
data class MemoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val topic: String,
    val source: String,        // provider/model/agent that produced it
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val embedding: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean = this === other || (other is MemoryEntryEntity && other.id == id)
    override fun hashCode(): Int = id.hashCode()
}

/** A notepad note. */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val colorIndex: Int = 0,
    val pinned: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A configured alarm. [days] is a comma list of 1..7 (Mon..Sun), empty = one-shot. */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val enabled: Boolean = true,
    val days: String = "",                 // e.g. "1,2,3,4,5"
    val vibrate: Boolean = true,
    // Smart rules
    val smartVolume: Boolean = false,      // raise volume if you went to bed late
    val skipSunday: Boolean = false,       // never ring on Sunday
    val baseVolumePercent: Int = 70,       // manual baseline the smart rule adjusts from
)

