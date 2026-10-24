package com.example.inference

sealed interface EngineStatus {
    data object Unloaded : EngineStatus

    data class Initializing(
        val message: String
    ) : EngineStatus

    data class Ready(
        val modelName: String,
        val backend: String,
        val loadTimeMs: Long,
        val fallbackNotice: String? = null
    ) : EngineStatus

    data class Error(
        val message: String
    ) : EngineStatus
}
