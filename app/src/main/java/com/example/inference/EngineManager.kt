package com.example.inference

import android.content.Context
import android.util.Log
import com.example.util.DeviceProtectionManager
import com.example.util.ProtectionAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Singleton coordinator ensuring:
 * 1. Only ONE engine instance exists at a time.
 * 2. Unloaded state uses PlaceholderInferenceEngine.
 * 3. Loading creates LiteRtLmInferenceEngine with device protection monitoring.
 * 4. System low-memory callbacks immediately close and unload engine to preserve RAM.
 */
class EngineManager(private val context: Context) {

    companion object {
        private const val TAG = "EngineManager"

        @Volatile
        private var INSTANCE: EngineManager? = null

        fun getInstance(context: Context): EngineManager {
            return INSTANCE ?: synchronized(this) {
                val instance = EngineManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val placeholderEngine = PlaceholderInferenceEngine()
    private var activeEngine: LocalInferenceEngine = placeholderEngine

    private val _engineStatus = MutableStateFlow<EngineStatus>(EngineStatus.Unloaded)
    val engineStatus: StateFlow<EngineStatus> = _engineStatus.asStateFlow()

    private val _useGpuSetting = MutableStateFlow(false)
    val useGpuSetting: StateFlow<Boolean> = _useGpuSetting.asStateFlow()

    private val _currentModelPath = MutableStateFlow<String?>(null)
    val currentModelPath: StateFlow<String?> = _currentModelPath.asStateFlow()

    fun setUseGpu(enabled: Boolean) {
        _useGpuSetting.value = enabled
    }

    fun getEngine(): LocalInferenceEngine {
        return activeEngine
    }

    suspend fun loadModel(modelPath: String, useGpu: Boolean = _useGpuSetting.value) {
        mutex.withLock {
            _currentModelPath.value = modelPath
            _engineStatus.value = EngineStatus.Initializing("Starting LiteRT-LM runtime (${if (useGpu) "GPU" else "CPU"})...")

            // Close existing engine
            activeEngine.close()

            val realEngine = LiteRtLmInferenceEngine(context)
            activeEngine = realEngine

            // Forward status updates from realEngine
            scope.launch {
                realEngine.status.collect { status ->
                    _engineStatus.value = status
                }
            }

            try {
                val config = EngineConfig(
                    modelPath = modelPath,
                    useGpu = useGpu
                )
                realEngine.initialize(config)
            } catch (t: Throwable) {
                Log.e(TAG, "Error in engine initialization: ${t.message}", t)
                _engineStatus.value = EngineStatus.Error("Initialization failed: ${t.message}")
            }
        }
    }

    fun unloadModel() {
        scope.launch {
            mutex.withLock {
                activeEngine.close()
                activeEngine = placeholderEngine
                _currentModelPath.value = null
                _engineStatus.value = EngineStatus.Unloaded
                Log.i(TAG, "Model unloaded successfully.")
            }
        }
    }

    fun onLowMemory(reason: String = "System low memory signal") {
        Log.w(TAG, "Low memory triggered ($reason). Closing and unloading model to reclaim RAM.")
        val mem = DeviceProtectionManager.getMemorySnapshot(context)
        DeviceProtectionManager.postAlert(
            ProtectionAlert.LowMemoryWarning(
                availMb = mem.availMemMb,
                reason = reason
            )
        )
        unloadModel()
    }
}
