package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatMessageEntity::class,
        ModelEntity::class,
        MemoryFactEntity::class,
        ProjectFolderEntity::class,
        CodeChunkEntity::class,
        BenchmarkResultEntity::class,
        DatasetExampleEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun modelDao(): ModelDao
    abstract fun memoryDao(): MemoryDao
    abstract fun projectDao(): ProjectDao
    abstract fun benchmarkDao(): BenchmarkDao
    abstract fun datasetDao(): DatasetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nex_ai_local.db"
                )
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
