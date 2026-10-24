package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChatMessageEntity::class,
        ModelEntity::class,
        MemoryFactEntity::class,
        ProjectFolderEntity::class,
        CodeChunkEntity::class,
        BenchmarkResultEntity::class,
        DatasetExampleEntity::class,
        ApiKeyEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun modelDao(): ModelDao
    abstract fun memoryDao(): MemoryDao
    abstract fun projectDao(): ProjectDao
    abstract fun benchmarkDao(): BenchmarkDao
    abstract fun datasetDao(): DatasetDao
    abstract fun apiKeyDao(): ApiKeyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_models ADD COLUMN tier TEXT NOT NULL DEFAULT 'Small'")
                db.execSQL("ALTER TABLE local_models ADD COLUMN minRamGb REAL NOT NULL DEFAULT 2.0")
                db.execSQL("ALTER TABLE local_models ADD COLUMN recommendedRamGb REAL NOT NULL DEFAULT 4.0")
                db.execSQL("ALTER TABLE local_models ADD COLUMN task TEXT NOT NULL DEFAULT 'chat'")
                db.execSQL("ALTER TABLE local_models ADD COLUMN licenseNote TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE local_models ADD COLUMN requiresLicenseAcceptance INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE local_models ADD COLUMN sourceLink TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE local_models ADD COLUMN measuredTokensPerSecond REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `api_keys` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `modelId` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `hash` TEXT NOT NULL,
                        `prefix` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `lastUsedAt` INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_api_keys_modelId` ON `api_keys` (`modelId`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nex_ai_local.db"
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
