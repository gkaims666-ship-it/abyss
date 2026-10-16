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
}

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
