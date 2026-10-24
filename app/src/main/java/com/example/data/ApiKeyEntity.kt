package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "api_keys",
    indices = [Index(value = ["modelId"])]
)
data class ApiKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modelId: String,
    val label: String,
    val hash: String, // SHA-256 hash
    val prefix: String, // First 8 chars
    val createdAt: Long,
    val lastUsedAt: Long
)
