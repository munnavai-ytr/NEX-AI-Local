package com.example.inference

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.data.ChatMessageEntity
import com.example.util.DeviceProtectionManager
import com.example.util.LowMemoryProtectionException
import com.example.util.ProtectionAlert
import com.example.util.ThermalProtectionException
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig as LiteRtEngineConfig
import com.google.ai.edge.litertlm.Message
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LiteRtLmInferenceEngine(private val context: Context? = null) : LocalInferenceEngine {

    companion object {
        private const val TAG = "LiteRtLmInferenceEngine"
    }

    private val _status = MutableStateFlow<EngineStatus>(EngineStatus.Unloaded)
    override val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private var _currentConfig: EngineConfig? = null
    override val currentConfig: EngineConfig?
        get() = _currentConfig

    override val isModelLoaded: Boolean
        get() = _status.value is EngineStatus.Ready && engine != null

    override val lastLoadTimeMs: Long
        get() = recordedLoadTimeMs

    override val activeBackend: String
        get() = activeBackendName

    private var engine: Engine? = null
    private var recordedLoadTimeMs: Long = 0L
    private var activeBackendName: String = "CPU"
    private var activeFallbackNotice: String? = null

    private val engineMutex = Mutex()
    private val isGenerating = AtomicBoolean(false)
    private var activeGenerationJob: Job? = null

    override suspend fun initialize(config: EngineConfig) = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            closeInternal()
            _currentConfig = config
            activeFallbackNotice = null

            val modelFile = File(config.modelPath)
            if (!modelFile.exists()) {
                val errorMsg = "Model file does not exist at path: ${config.modelPath}"
                Log.e(TAG, errorMsg)
                _status.value = EngineStatus.Error(errorMsg)
                return@withLock
            }

            // GPU is experimental; CPU is default
            if (config.useGpu) {
                _status.value = EngineStatus.Initializing(
                    "Initializing model on GPU (experimental)..."
                )
                val gpuSuccess = tryInitBackend(
                    modelPath = config.modelPath,
                    backend = Backend.GPU(),
                    backendName = "GPU"
                )

                if (!gpuSuccess) {
                    val notice = "GPU initialization failed. Automatically fell back to CPU."
                    activeFallbackNotice = notice
                    Log.w(TAG, notice)
                    _status.value = EngineStatus.Initializing(
                        "GPU unavailable on this device. Falling back to CPU..."
                    )
                    val cpuSuccess = tryInitBackend(
                        modelPath = config.modelPath,
                        backend = Backend.CPU(),
                        backendName = "CPU"
                    )
                    if (!cpuSuccess) {
                        _status.value = EngineStatus.Error("Failed to initialize model on CPU fallback.")
                    }
                }
            } else {
                _status.value = EngineStatus.Initializing(
                    "Initializing model on CPU (loading weights)..."
                )
                val cpuSuccess = tryInitBackend(
                    modelPath = config.modelPath,
                    backend = Backend.CPU(),
                    backendName = "CPU"
                )
                if (!cpuSuccess) {
                    _status.value = EngineStatus.Error("Failed to initialize model on CPU.")
                }
            }
        }
    }

    private fun tryInitBackend(
        modelPath: String,
        backend: Backend,
        backendName: String
    ): Boolean {
        return try {
            val startTime = SystemClock.elapsedRealtime()
            val liteRtConfig = LiteRtEngineConfig(
                modelPath = modelPath,
                backend = backend
            )
            val newEngine = Engine(liteRtConfig)
            newEngine.initialize()

            val durationMs = SystemClock.elapsedRealtime() - startTime
            recordedLoadTimeMs = durationMs
            activeBackendName = backendName

            engine = newEngine

            _status.value = EngineStatus.Ready(
                modelName = File(modelPath).name,
                backend = backendName,
                loadTimeMs = durationMs,
                fallbackNotice = activeFallbackNotice
            )
            Log.i(TAG, "Successfully initialized LiteRT-LM engine on $backendName in ${durationMs}ms")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing on $backendName: ${t.message}", t)
            false
        }
    }

    override fun streamGenerate(
        prompt: String,
        systemInstruction: String?,
        history: List<ChatMessageEntity>
    ): Flow<GenerationEvent> = flow {
        if (!isModelLoaded || engine == null) {
            emit(GenerationEvent.Error(IllegalStateException("Select a model first")))
            return@flow
        }

        if (!isGenerating.compareAndSet(false, true)) {
            emit(GenerationEvent.Error(IllegalStateException("Another generation is currently in progress.")))
            return@flow
        }

        // Check thermal and memory safety before starting
        if (context != null) {
            val initialSafety = DeviceProtectionManager.checkSafety(context)
            if (initialSafety is DeviceProtectionManager.SafetyStatus.Overheating) {
                isGenerating.set(false)
                DeviceProtectionManager.postAlert(ProtectionAlert.ThermalWarning(initialSafety.tempCelsius))
                emit(
                    GenerationEvent.Error(
                        ThermalProtectionException(initialSafety.tempCelsius),
                        "Thermal protection: Battery is at ${"%.1f".format(initialSafety.tempCelsius)}°C (>42°C). Generation paused."
                    )
                )
                return@flow
            }
        }

        // Build conversation using LiteRT-LM Conversation API so the model's chat template is applied automatically
        val initialMessages = history.mapNotNull { entity ->
            when (entity.role) {
                "user" -> Message.user(entity.content)
                "assistant" -> Message.model(entity.content)
                else -> null
            }
        }

        val convConfig = ConversationConfig(
            systemInstruction = if (!systemInstruction.isNullOrBlank()) Contents.of(systemInstruction) else null,
            initialMessages = initialMessages
        )

        val conv: Conversation
        try {
            conv = engine?.createConversation(convConfig)
                ?: throw IllegalStateException("Engine conversation could not be created.")
        } catch (t: Throwable) {
            isGenerating.set(false)
            emit(GenerationEvent.Error(t, "Failed to initialize conversation: ${t.message}"))
            return@flow
        }

        val generationStartTime = SystemClock.elapsedRealtime()
        var timeToFirstTokenMs: Long? = null
        var tokenCount = 0
        var peakMemoryMb = DeviceProtectionManager.getCurrentProcessMemoryMb()

        activeGenerationJob = currentCoroutineContext()[Job]

        try {
            val flowResponse = conv.sendMessageAsync(prompt)

            flowResponse.collect { message ->
                if (!currentCoroutineContext().isActive) {
                    throw CancellationException("Generation stopped by user")
                }

                // Active protection watch during generation
                if (context != null) {
                    val safety = DeviceProtectionManager.checkSafety(context)
                    if (safety is DeviceProtectionManager.SafetyStatus.Overheating) {
                        DeviceProtectionManager.postAlert(ProtectionAlert.ThermalWarning(safety.tempCelsius))
                        throw ThermalProtectionException(safety.tempCelsius)
                    } else if (safety is DeviceProtectionManager.SafetyStatus.LowMemory) {
                        DeviceProtectionManager.postAlert(
                            ProtectionAlert.LowMemoryWarning(safety.availMemMb, "Critically low system RAM")
                        )
                        EngineManager.getInstance(context).onLowMemory("RAM dropped to ${safety.availMemMb}MB")
                        throw LowMemoryProtectionException(safety.availMemMb)
                    }
                }

                val currentMem = DeviceProtectionManager.getCurrentProcessMemoryMb()
                if (currentMem > peakMemoryMb) {
                    peakMemoryMb = currentMem
                }

                val tokenText = extractText(message)
                if (tokenText.isNotEmpty()) {
                    if (timeToFirstTokenMs == null) {
                        timeToFirstTokenMs = SystemClock.elapsedRealtime() - generationStartTime
                    }
                    tokenCount++
                    emit(GenerationEvent.Token(tokenText))
                }
            }

            val totalDurationMs = SystemClock.elapsedRealtime() - generationStartTime
            val ttft = timeToFirstTokenMs ?: totalDurationMs
            val decodeDurationMs = (totalDurationMs - ttft).coerceAtLeast(1)
            val tokensPerSec = if (tokenCount > 0) {
                (tokenCount.toDouble() / (decodeDurationMs / 1000.0))
            } else {
                0.0
            }

            val stats = GenerationStats(
                loadTimeMs = recordedLoadTimeMs,
                timeToFirstTokenMs = ttft,
                tokensPerSecond = tokensPerSec,
                totalTokens = tokenCount,
                durationMs = totalDurationMs,
                backend = activeBackendName,
                peakMemoryMb = peakMemoryMb
            )
            emit(GenerationEvent.Complete(stats))

        } catch (c: CancellationException) {
            Log.d(TAG, "Generation cancelled cleanly.")
            emit(GenerationEvent.Complete(null))
        } catch (th: ThermalProtectionException) {
            Log.w(TAG, "Thermal protection triggered: ${th.message}")
            emit(
                GenerationEvent.Error(
                    th,
                    "Thermal protection: Battery temperature reached ${"%.1f".format(th.tempCelsius)}°C (>42°C). Generation paused."
                )
            )
        } catch (lm: LowMemoryProtectionException) {
            Log.w(TAG, "Low memory protection triggered: ${lm.message}")
            emit(
                GenerationEvent.Error(
                    lm,
                    "Low memory protection: Available RAM dropped to ${lm.availMb}MB. Model safely unloaded."
                )
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Inference error: ${t.message}", t)
            emit(GenerationEvent.Error(t, t.message ?: "Generation error"))
        } finally {
            isGenerating.set(false)
            activeGenerationJob = null
        }
    }.flowOn(Dispatchers.IO)

    private fun extractText(message: Message): String {
        return try {
            val contentsList = message.contents?.contents
            if (!contentsList.isNullOrEmpty()) {
                contentsList.filterIsInstance<Content.Text>().joinToString("") { it.text }
            } else {
                ""
            }
        } catch (e: Throwable) {
            ""
        }
    }

    override fun stopGeneration() {
        if (isGenerating.get()) {
            activeGenerationJob?.cancel(CancellationException("Generation stopped by user"))
            isGenerating.set(false)
        }
    }

    private fun closeInternal() {
        stopGeneration()
        try {
            engine?.close()
        } catch (t: Throwable) {
            Log.e(TAG, "Error closing LiteRT-LM engine: ${t.message}", t)
        } finally {
            engine = null
            _status.value = EngineStatus.Unloaded
        }
    }

    override fun close() {
        closeInternal()
    }
}
