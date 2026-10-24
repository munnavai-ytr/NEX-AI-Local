package com.example.util

import com.example.inference.EngineManager
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.*

class LocalApiServer(private val engineManager: EngineManager) {

    private val server = embeddedServer(Netty, port = 8080, host = "127.0.0.1") {
        install(ContentNegotiation) {
            json()
        }
        routing {
            get("/v1/models") {
                // Return model list
                call.respond(mapOf("data" to listOf(mapOf("id" to "gemini-model"))))
            }
            post("/v1/chat/completions") {
                // Handle chat completion
                val request = call.receive<JsonObject>()
                // Perform inference using engineManager...
                call.respond(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to "Hello!")))))
            }
        }
    }

    fun start() = server.start(wait = false)
    fun stop() = server.stop()
}
