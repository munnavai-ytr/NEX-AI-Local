package com.example.inference

import com.example.data.ChatMessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Placeholder engine strictly representing the "no model loaded" state.
 * Emits an explicit error if generation is attempted and never fabricates or fakes AI output.
 */
class PlaceholderInferenceEngine : LocalInferenceEngine {
    private val _status = MutableStateFlow<EngineStatus>(EngineStatus.Unloaded)
    override val status: StateFlow<EngineStatus> = _status.asStateFlow()

    override val isModelLoaded: Boolean = false
    override val currentConfig: EngineConfig? = null

    override suspend fun initialize(config: EngineConfig) {
        _status.value = EngineStatus.Unloaded
    }

    override fun streamGenerate(
        prompt: String,
        systemInstruction: String?,
        history: List<ChatMessageEntity>
    ): Flow<GenerationEvent> = flow {
        // Never fake AI output. Clearly signal that a model must be selected first.
        emit(
            GenerationEvent.Error(
                IllegalStateException("Select a model first")
            )
        )
    }

    override fun stopGeneration() {
        // No-op for placeholder
    }

    override fun close() {
        _status.value = EngineStatus.Unloaded
    }
}
