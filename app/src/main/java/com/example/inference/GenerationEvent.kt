package com.example.inference

sealed interface GenerationEvent {
    data class Token(val text: String) : GenerationEvent
    data class Complete(val stats: GenerationStats?) : GenerationEvent
    data class Error(val error: Throwable, val message: String = error.message ?: "Inference error occurred") : GenerationEvent
}
