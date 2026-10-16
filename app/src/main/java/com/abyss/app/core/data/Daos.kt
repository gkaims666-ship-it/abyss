package com.abyss.app.core.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY id ASC")
    fun observeConversation(conversationId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY id ASC")
    suspend fun loadConversation(conversationId: String): List<ChatMessageEntity>

    @Query("SELECT conversationId FROM chat_messages GROUP BY conversationId ORDER BY MAX(createdAt) DESC")
    fun observeConversations(): Flow<List<String>>

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    /**
     * Deletes the newest stored row matching this message. Freshly sent
     * messages carry temporary UI ids, so we match on content instead of id.
     */
    @Query(
        """
        DELETE FROM chat_messages WHERE id = (
            SELECT id FROM chat_messages
            WHERE conversationId = :conversationId AND role = :role AND content = :content
            ORDER BY id DESC LIMIT 1
        )
        """
    )
    suspend fun deleteMatching(conversationId: String, role: String, content: String)

    /** One row per conversation: its first user message as a title, newest first. */
    @Query(
        """
        SELECT m.conversationId AS conversationId,
               (SELECT u.content FROM chat_messages AS u
                 WHERE u.conversationId = m.conversationId AND u.role = 'user'
                 ORDER BY u.id ASC LIMIT 1) AS title,
               MAX(m.createdAt) AS lastAt
        FROM chat_messages AS m
        GROUP BY m.conversationId
        ORDER BY lastAt DESC
        """
    )
    fun observeConversationSummaries(): Flow<List<ConversationSummary>>
}

/** Row type for [ChatDao.observeConversationSummaries]. */
data class ConversationSummary(
    val conversationId: String,
    val title: String?,
    val lastAt: Long,
)

@Dao
interface MemoryDao {
    @Insert
    suspend fun insert(entry: MemoryEntryEntity): Long

    @Query("SELECT COUNT(*) FROM memory")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM memory ORDER BY id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<MemoryEntryEntity>

    /** Candidate pool for in-Kotlin ranking (see MemoryRepository.retrieve). */
    @Query("SELECT * FROM memory ORDER BY id DESC LIMIT :limit")
    suspend fun pool(limit: Int): List<MemoryEntryEntity>
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Upsert
    suspend fun upsert(note: NoteEntity): Long

    @Delete
    suspend fun delete(note: NoteEntity)
}

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms")
    suspend fun all(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun byId(id: Long): AlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Delete
    suspend fun delete(alarm: AlarmEntity)
}
