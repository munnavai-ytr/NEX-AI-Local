package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_models")
data class ModelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val filePath: String,
    val sizeBytes: Long,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val isLastUsed: Boolean = false,
    val isLoaded: Boolean = false,
    val downloadUrl: String = "",
    val isCurated: Boolean = false
)

