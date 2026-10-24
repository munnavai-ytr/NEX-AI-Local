package com.example.inference

import com.example.data.ChatMessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LocalInferenceEngine : AutoCloseable {
    val status: StateFlow<EngineStatus>
    val isModelLoaded: Boolean
    val currentConfig: EngineConfig?
    val lastLoadTimeMs: Long get() = 0L
    val activeBackend: String get() = "CPU"

    /**
     * Loads the model into memory asynchronously.
     */
    suspend fun initialize(config: EngineConfig)

    /**
     * Streams tokens asynchronously using LiteRT-LM's Conversation API with automatic chat template application.
     */
    fun streamGenerate(
        prompt: String,
        systemInstruction: String? = null,
        history: List<ChatMessageEntity> = emptyList()
    ): Flow<GenerationEvent>

    /**
     * Cleanly stops and cancels any active token generation.
     */
    fun stopGeneration()

    /**
     * Closes the engine and frees all native and heap memory.
     */
    override fun close()
}
