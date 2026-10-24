package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "project_folders")
data class ProjectFolderEntity(
    @PrimaryKey
    val uriString: String,
    val displayName: String,
    val fileCount: Int = 0,
    val chunkCount: Int = 0,
    val lastIndexedTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "code_chunks",
    indices = [
        Index("projectUri"),
        Index("fileName"),
        Index("filePath")
    ]
)
data class CodeChunkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectUri: String,
    val filePath: String,
    val fileName: String,
    val startLine: Int,
    val endLine: Int,
    val content: String
)
