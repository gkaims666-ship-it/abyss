package com.abyss.app.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatMessageEntity::class, MemoryEntryEntity::class,
        NoteEntity::class, AlarmEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AbyssDb : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun memoryDao(): MemoryDao
    abstract fun noteDao(): NoteDao
    abstract fun alarmDao(): AlarmDao

    companion object {
        @Volatile private var INSTANCE: AbyssDb? = null

        fun get(context: Context): AbyssDb = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, AbyssDb::class.java, "abyss.db"
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
