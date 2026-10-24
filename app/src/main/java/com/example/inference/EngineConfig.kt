package com.example.inference

data class EngineConfig(
    val modelPath: String,
    val useGpu: Boolean = false,
    val maxTokens: Int = 1024
)
