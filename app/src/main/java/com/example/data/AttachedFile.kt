package com.example.data

data class AttachedFile(
    val uriString: String,
    val name: String,
    val sizeBytes: Long,
    val lineCount: Int,
    val estimatedTokens: Int,
    val content: String
)
