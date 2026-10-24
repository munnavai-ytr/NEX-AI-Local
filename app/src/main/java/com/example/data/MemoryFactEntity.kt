package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_facts")
data class MemoryFactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fact: String,
    val isEnabled: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)
