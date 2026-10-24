package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dataset_examples")
data class DatasetExampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val systemPrompt: String = "",
    val userPrompt: String = "",
    val assistantAnswer: String = "",
    val feedback: Int = 0, // +1 = thumbs up, -1 = thumbs down, 0 = neutral
    val timestamp: Long = System.currentTimeMillis(),
    val modelName: String? = null
)
