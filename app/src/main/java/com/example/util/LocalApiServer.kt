package com.example.util

import android.util.Log
import com.example.data.ApiKeyDao
import com.example.inference.EngineManager
import com.example.inference.GenerationEvent
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.security.MessageDigest

class LocalApiServer(
    private val engineManager: EngineManager,
    private val apiKeyDao: ApiKeyDao,
    private val port: Int = 8080
) {
    private val TAG = "LocalApiServer"
    private val mutex = Mutex()
    private val server = embeddedServer(Netty, port = port, host = "127.0.0.1") {
        routing {
            get("/health") {
                call.respond(mapOf("status" to "ok"))
            }

            get("/v1/models") {
                val models = listOf(mapOf("id" to "local-model", "object" to "model"))
                call.respond(mapOf("data" to models))
            }

            post("/v1/chat/completions") {
                val authHeader = call.request.header(HttpHeaders.Authorization)
                if (!authenticate(authHeader)) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to mapOf("message" to "Invalid API key")))
                    return@post
                }

                if (!engineManager.getEngine().isModelLoaded) {
                    call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to mapOf("message" to "No model loaded")))
                    return@post
                }

                val request = call.receive<JsonObject>()
                val stream = request["stream"]?.jsonPrimitive?.boolean ?: false
                val messages = request["messages"]?.jsonArray ?: JsonArray(emptyList())
                val prompt = messages.lastOrNull()?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""

                if (stream) {
                    call.respondTextWriter(ContentType.Text.EventStream) {
                        engineManager.getEngine().streamGenerate(prompt).collect { event ->
                            if (event is GenerationEvent.Token) {
                                write("data: {\"choices\": [{\"delta\": {\"content\": \"${event.text}\"}}]}\n\n")
                            }
                        }
                        write("data: [DONE]\n\n")
                    }
                } else {
                    var fullResponse = ""
                    engineManager.getEngine().streamGenerate(prompt).collect { event ->
                        if (event is GenerationEvent.Token) {
                            fullResponse += event.text
                        }
                    }
                    call.respond(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to fullResponse)))))
                }
            }
        }
    }

    private suspend fun authenticate(authHeader: String?): Boolean {
        if (authHeader == null || !authHeader.startsWith("Bearer nex-sk-")) return false
        val key = authHeader.removePrefix("Bearer ")
        val hash = sha256(key)
        return apiKeyDao.getByKeyHash(hash) != null
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun start() = server.start(wait = false)
    fun stop() = server.stop()
}
