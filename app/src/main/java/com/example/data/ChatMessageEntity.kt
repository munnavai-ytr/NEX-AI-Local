package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val thinking: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val modelLoadTimeMs: Long? = null,
    val timeToFirstTokenMs: Long? = null,
    val tokensPerSecond: Double? = null,
    val backendUsed: String? = null,
    val sources: String? = null, // Formatted source references: "File.kt:1-50, Other.kt:10-40"
    val feedback: Int = 0 // +1 = thumbs up, -1 = thumbs down, 0 = neutral
)
