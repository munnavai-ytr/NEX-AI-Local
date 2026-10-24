package com.example.ui

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AttachedFile
import com.example.data.BenchmarkResultEntity
import com.example.data.ChatMessageEntity
import com.example.data.CodeChunkEntity
import com.example.data.DatasetExampleEntity
import com.example.data.MemoryFactEntity
import com.example.data.ModelEntity
import com.example.data.ProjectFolderEntity
import com.example.data.StarterCatalogModel
import com.example.data.StarterModelCatalog
import com.example.inference.EngineManager
import com.example.inference.EngineStatus
import com.example.inference.GenerationEvent
import com.example.inference.GenerationStats
import com.example.util.DeviceProtectionManager
import com.example.util.DownloadMetadata
import com.example.util.DownloadState
import com.example.util.EncryptedTokenStorage
import com.example.util.ImportProgress
import com.example.util.IndexingProgress
import com.example.util.LocalApiService
import com.example.util.ModelDownloadManager
import com.example.util.ModelFileImporter
import com.example.util.ProjectIndexer
import com.example.util.ProtectionAlert
import com.example.util.RetrievedSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

object SystemPromptPresets {
    const val PRO_DEVELOPER = "Act as a senior software engineer. Be concise. Ask one clarifying question when the request is ambiguous. Show code in fenced blocks with the language name. Mention edge cases and risks briefly. Say 'I am not sure' instead of guessing."
    const val CHAT = "You are a helpful, careful on-device AI assistant. Be concise, direct, and honest about uncertainty."
}

object BenchmarkPrompts {
    const val SHORT_NAME = "Short Prompt"
    const val SHORT_TEXT = "Write a concise Kotlin function that counts the vowels in a given string."

    const val LONG_NAME = "Long Prompt"
    const val LONG_TEXT = "Write a complete, thread-safe Kotlin implementation of an LRU Cache with generic Key and Value types. Include evictOldest(), get(), and put() methods, comments explaining time and space complexity, and sample usage."
}

sealed class BenchmarkState {
    object Idle : BenchmarkState()
    data class Running(
        val stage: String,
        val backend: String,
        val promptType: String,
        val progressText: String? = null
    ) : BenchmarkState()
    data class Completed(
        val lastResult: BenchmarkResultEntity,
        val sideBySideCpu: BenchmarkResultEntity? = null,
        val sideBySideGpu: BenchmarkResultEntity? = null
    ) : BenchmarkState()
    data class Error(val message: String) : BenchmarkState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("nex_settings", Context.MODE_PRIVATE)

    private val db = AppDatabase.getInstance(application)
    private val chatDao = db.chatDao()
    private val modelDao = db.modelDao()
    private val memoryDao = db.memoryDao()
    private val projectDao = db.projectDao()
    private val benchmarkDao = db.benchmarkDao()
    private val datasetDao = db.datasetDao()

    private val engineManager = EngineManager.getInstance(application)
    private val downloadManager = ModelDownloadManager.getInstance(application)

    // Chat messages
    val messages: StateFlow<List<ChatMessageEntity>> = chatDao.getAllMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dataset search & list
    private val _datasetSearchQuery = MutableStateFlow("")
    val datasetSearchQuery: StateFlow<String> = _datasetSearchQuery.asStateFlow()

    fun setDatasetSearchQuery(query: String) {
        _datasetSearchQuery.value = query
    }

    val datasetExamples: StateFlow<List<DatasetExampleEntity>> = combine(_datasetSearchQuery, datasetDao.getAllExamples()) { query, list ->
        if (query.isBlank()) {
            list
        } else {
            val q = query.trim().lowercase(java.util.Locale.ROOT)
            list.filter {
                it.userPrompt.lowercase(java.util.Locale.ROOT).contains(q) ||
                it.assistantAnswer.lowercase(java.util.Locale.ROOT).contains(q) ||
                it.systemPrompt.lowercase(java.util.Locale.ROOT).contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalDatasetCount: StateFlow<Int> = datasetDao.getTotalCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val thumbsUpDatasetCount: StateFlow<Int> = datasetDao.getThumbsUpCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val thumbsDownDatasetCount: StateFlow<Int> = datasetDao.getThumbsDownCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Models
    val models: StateFlow<List<ModelEntity>> = modelDao.getAllModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _localApiEnabled = mutableStateOf(false)
    val localApiEnabled: Boolean get() = _localApiEnabled.value

    fun setLocalApiEnabled(enabled: Boolean) {
        _localApiEnabled.value = enabled
        if (enabled) {
            val intent = Intent(getApplication(), LocalApiService::class.java)
            getApplication<android.app.Application>().startForegroundService(intent)
        } else {
            val intent = Intent(getApplication(), LocalApiService::class.java)
            getApplication<android.app.Application>().stopService(intent)
        }
    }

    val lastUsedModel: StateFlow<ModelEntity?> = models.map { list ->
        list.firstOrNull { it.isLastUsed }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val engineStatus: StateFlow<EngineStatus> = engineManager.engineStatus
    val useGpuSetting: StateFlow<Boolean> = engineManager.useGpuSetting

    val downloadState: StateFlow<DownloadState> = downloadManager.downloadState
    val wifiOnlyDownload: StateFlow<Boolean> = downloadManager.wifiOnly

    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress.asStateFlow()

    private val _streamingText = MutableStateFlow<String?>(null)
    val streamingText: StateFlow<String?> = _streamingText.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private val _hasHfToken = MutableStateFlow(EncryptedTokenStorage.hasToken(application))
    val hasHfToken: StateFlow<Boolean> = _hasHfToken.asStateFlow()

    // System prompt configuration & presets
    private val _systemPrompt = MutableStateFlow(
        prefs.getString("system_prompt", SystemPromptPresets.PRO_DEVELOPER) ?: SystemPromptPresets.PRO_DEVELOPER
    )
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    // Context guard settings (default 2048 tokens)
    private val _maxContextTokens = MutableStateFlow(
        prefs.getInt("max_context_tokens", 2048)
    )
    val maxContextTokens: StateFlow<Int> = _maxContextTokens.asStateFlow()

    // Input text prefill for Edit-and-resend
    private val _inputPrefill = MutableSharedFlow<String>()
    val inputPrefill: SharedFlow<String> = _inputPrefill.asSharedFlow()

    // Personal Memory & Preferences
    val memoryFacts: StateFlow<List<MemoryFactEntity>> = memoryDao.getAllFacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isMemoryInjectionEnabled = MutableStateFlow(
        prefs.getBoolean("memory_injection_enabled", true)
    )
    val isMemoryInjectionEnabled: StateFlow<Boolean> = _isMemoryInjectionEnabled.asStateFlow()

    val injectedMemoryText: StateFlow<String> = combine(memoryFacts, _isMemoryInjectionEnabled) { facts, enabled ->
        if (!enabled) ""
        else {
            val active = facts.filter { it.isEnabled }
            if (active.isEmpty()) ""
            else buildString {
                appendLine("[User Preferences & Memory]")
                active.forEach { appendLine("- ${it.fact}") }
            }.trimEnd()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // Project Awareness & Indexing
    val projects: StateFlow<List<ProjectFolderEntity>> = projectDao.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeProjectUri = MutableStateFlow(prefs.getString("active_project_uri", null))
    val activeProject: StateFlow<ProjectFolderEntity?> = combine(projects, _activeProjectUri) { list, activeUri ->
        list.firstOrNull { it.uriString == activeUri } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _indexingProgress = MutableStateFlow<IndexingProgress?>(null)
    val indexingProgress: StateFlow<IndexingProgress?> = _indexingProgress.asStateFlow()

    // Attached files for the next message
    private val _attachedFiles = MutableStateFlow<List<AttachedFile>>(emptyList())
    val attachedFiles: StateFlow<List<AttachedFile>> = _attachedFiles.asStateFlow()

    // Benchmark & Protection
    val benchmarkHistory: StateFlow<List<BenchmarkResultEntity>> = benchmarkDao.getAllResults()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProtectionAlert: StateFlow<ProtectionAlert?> = DeviceProtectionManager.lastAlert

    private val _benchmarkState = MutableStateFlow<BenchmarkState>(BenchmarkState.Idle)
    val benchmarkState: StateFlow<BenchmarkState> = _benchmarkState.asStateFlow()

    private val _batteryTemp = MutableStateFlow(DeviceProtectionManager.getBatteryTemperature(application))
    val batteryTemp: StateFlow<Float> = _batteryTemp.asStateFlow()

    private val _memorySnapshot = MutableStateFlow(DeviceProtectionManager.getMemorySnapshot(application))
    val memorySnapshot: StateFlow<DeviceProtectionManager.MemorySnapshot> = _memorySnapshot.asStateFlow()

    private var activeGenerationJob: Job? = null
    private var activeBenchmarkJob: Job? = null

    init {
        syncModelsDirectory()
        viewModelScope.launch {
            DeviceProtectionManager.alerts.collect { alert ->
                _snackbarMessage.emit(alert.message)
            }
        }
    }

    fun refreshHardwareStats() {
        _batteryTemp.value = DeviceProtectionManager.getBatteryTemperature(getApplication())
        _memorySnapshot.value = DeviceProtectionManager.getMemorySnapshot(getApplication())
    }

    fun dismissProtectionAlert() {
        DeviceProtectionManager.clearAlert()
    }

    private fun syncModelsDirectory() {
        viewModelScope.launch(Dispatchers.IO) {
            val modelsDir = ModelDownloadManager.getModelsDir(getApplication())
            val filesOnDisk = modelsDir.listFiles { f -> f.isFile && f.name.endsWith(".litertlm") } ?: emptyArray()
            for (file in filesOnDisk) {
                val exists = modelDao.getModelByPath(file.absolutePath) != null
                if (!exists) {
                    val size = file.length()
                    val catalogMatch = StarterModelCatalog.MODELS.firstOrNull {
                        it.fileName.equals(file.name, ignoreCase = true)
                    }
                    val entity = if (catalogMatch != null) {
                        ModelEntity(
                            name = catalogMatch.name,
                            filePath = file.absolutePath,
                            sizeBytes = size,
                            downloadUrl = catalogMatch.downloadUrl,
                            isCurated = true,
                            tier = catalogMatch.tier,
                            minRamGb = catalogMatch.minRamGb,
                            recommendedRamGb = catalogMatch.recommendedRamGb,
                            task = catalogMatch.task,
                            licenseNote = catalogMatch.license,
                            requiresLicenseAcceptance = catalogMatch.requiresLicenseAcceptance,
                            sourceLink = catalogMatch.downloadUrl
                        )
                    } else {
                        ModelEntity(
                            name = file.name,
                            filePath = file.absolutePath,
                            sizeBytes = size,
                            downloadUrl = "",
                            isCurated = false,
                            tier = ModelEntity.inferTier(size),
                            minRamGb = ModelEntity.inferMinRamGb(size),
                            recommendedRamGb = ModelEntity.inferRecommendedRamGb(size)
                        )
                    }
                    modelDao.insertModel(entity)
                }
            }
        }
    }

    // Benchmark execution
    fun runBenchmark(
        promptType: String = BenchmarkPrompts.SHORT_NAME,
        useGpu: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val targetModel = models.value.firstOrNull { it.isLoaded }
                ?: lastUsedModel.value
                ?: models.value.firstOrNull()

            if (targetModel == null) {
                _benchmarkState.value = BenchmarkState.Error("No model available. Please download or import a model first.")
                _snackbarMessage.emit("No model available to benchmark.")
                return@launch
            }

            refreshHardwareStats()
            _benchmarkState.value = BenchmarkState.Running(
                stage = "Initializing benchmark on ${if (useGpu) "GPU" else "CPU"}...",
                backend = if (useGpu) "GPU" else "CPU",
                promptType = promptType
            )

            runBenchmarkInternal(targetModel, promptType, useGpu).onSuccess { res ->
                _benchmarkState.value = BenchmarkState.Completed(lastResult = res)
                _snackbarMessage.emit("Benchmark complete: ${"%.1f".format(res.tokensPerSecond)} tok/s (${res.verdict})")
            }.onFailure { err ->
                _benchmarkState.value = BenchmarkState.Error(err.message ?: "Benchmark failed")
                _snackbarMessage.emit("Benchmark failed: ${err.message}")
            }
        }
    }

    fun runComparisonBenchmark(promptType: String = BenchmarkPrompts.SHORT_NAME) {
        viewModelScope.launch(Dispatchers.IO) {
            val targetModel = models.value.firstOrNull { it.isLoaded }
                ?: lastUsedModel.value
                ?: models.value.firstOrNull()

            if (targetModel == null) {
                _benchmarkState.value = BenchmarkState.Error("No model available. Please download or import a model first.")
                _snackbarMessage.emit("No model available to benchmark.")
                return@launch
            }

            refreshHardwareStats()
            var cpuResult: BenchmarkResultEntity? = null
            var gpuResult: BenchmarkResultEntity? = null

            // 1. Run CPU Benchmark
            _benchmarkState.value = BenchmarkState.Running(
                stage = "Step 1/2: Running CPU Benchmark ($promptType)...",
                backend = "CPU",
                promptType = promptType
            )

            runBenchmarkInternal(targetModel, promptType, useGpu = false).onSuccess { res ->
                cpuResult = res
            }.onFailure { err ->
                _benchmarkState.value = BenchmarkState.Error("CPU benchmark failed: ${err.message}")
                _snackbarMessage.emit("CPU benchmark failed: ${err.message}")
                return@launch
            }

            // Short pause between tests for thermal and RAM recovery
            kotlinx.coroutines.delay(1200)
            refreshHardwareStats()

            // 2. Run GPU Benchmark
            _benchmarkState.value = BenchmarkState.Running(
                stage = "Step 2/2: Running GPU Benchmark ($promptType)...",
                backend = "GPU",
                promptType = promptType
            )

            runBenchmarkInternal(targetModel, promptType, useGpu = true).onSuccess { res ->
                gpuResult = res
            }.onFailure { err ->
                _benchmarkState.value = BenchmarkState.Error("GPU benchmark failed: ${err.message}")
                _snackbarMessage.emit("GPU benchmark failed: ${err.message}")
                return@launch
            }

            val finalCpu = cpuResult
            val finalGpu = gpuResult
            if (finalCpu != null && finalGpu != null) {
                _benchmarkState.value = BenchmarkState.Completed(
                    lastResult = finalGpu,
                    sideBySideCpu = finalCpu,
                    sideBySideGpu = finalGpu
                )
                _snackbarMessage.emit("Comparison done! CPU: ${"%.1f".format(finalCpu.tokensPerSecond)} tps vs GPU: ${"%.1f".format(finalGpu.tokensPerSecond)} tps")
            }
        }
    }

    private suspend fun runBenchmarkInternal(
        model: ModelEntity,
        promptType: String,
        useGpu: Boolean
    ): Result<BenchmarkResultEntity> {
        val promptText = if (promptType == BenchmarkPrompts.SHORT_NAME) {
            BenchmarkPrompts.SHORT_TEXT
        } else {
            BenchmarkPrompts.LONG_TEXT
        }

        val backendName = if (useGpu) "GPU" else "CPU"
        val startLoad = SystemClock.elapsedRealtime()
        engineManager.loadModel(model.filePath, useGpu = useGpu)

        // Wait for ready
        var waitMs = 35000L
        while (waitMs > 0 && engineStatus.value !is EngineStatus.Ready && engineStatus.value !is EngineStatus.Error) {
            kotlinx.coroutines.delay(200)
            waitMs -= 200
        }

        val currentStatus = engineStatus.value
        if (currentStatus !is EngineStatus.Ready) {
            val errorMsg = if (currentStatus is EngineStatus.Error) currentStatus.message else "Model load timed out"
            return Result.failure(IllegalStateException(errorMsg))
        }

        val loadTimeMs = currentStatus.loadTimeMs.coerceAtLeast(SystemClock.elapsedRealtime() - startLoad)
        val actualBackend = currentStatus.backend

        val engine = engineManager.getEngine()
        val startGenTime = SystemClock.elapsedRealtime()
        var timeToFirstTokenMs: Long? = null
        var tokenCount = 0
        var peakMemoryMb = DeviceProtectionManager.getCurrentProcessMemoryMb()

        val deferred = CompletableDeferred<Result<BenchmarkResultEntity>>()

        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val flow = engine.streamGenerate(prompt = promptText)
                flow.collect { event ->
                    when (event) {
                        is GenerationEvent.Token -> {
                            if (timeToFirstTokenMs == null) {
                                timeToFirstTokenMs = SystemClock.elapsedRealtime() - startGenTime
                            }
                            tokenCount++
                            val currentMem = DeviceProtectionManager.getCurrentProcessMemoryMb()
                            if (currentMem > peakMemoryMb) {
                                peakMemoryMb = currentMem
                            }
                            val elapsedSec = ((SystemClock.elapsedRealtime() - startGenTime) / 1000.0).coerceAtLeast(0.1)
                            _benchmarkState.value = BenchmarkState.Running(
                                stage = "Generating tokens ($tokenCount)...",
                                backend = actualBackend,
                                promptType = promptType,
                                progressText = "Speed: ${"%.1f".format(tokenCount.toDouble() / elapsedSec)} tok/s"
                            )
                        }
                        is GenerationEvent.Complete -> {
                            val totalDurationMs = SystemClock.elapsedRealtime() - startGenTime
                            val ttft = timeToFirstTokenMs ?: totalDurationMs
                            val decodeDurationMs = (totalDurationMs - ttft).coerceAtLeast(1)
                            val tokensPerSec = if (tokenCount > 0) {
                                (tokenCount.toDouble() / (decodeDurationMs / 1000.0))
                            } else {
                                0.0
                            }
                            val verdict = BenchmarkResultEntity.computeVerdict(tokensPerSec)
                            val temp = DeviceProtectionManager.getBatteryTemperature(getApplication())

                            val res = BenchmarkResultEntity(
                                modelName = model.name,
                                modelPath = model.filePath,
                                backend = actualBackend,
                                promptType = promptType,
                                promptText = promptText,
                                loadTimeMs = loadTimeMs,
                                timeToFirstTokenMs = ttft,
                                tokensPerSecond = tokensPerSec,
                                totalTokens = tokenCount,
                                durationMs = totalDurationMs,
                                peakMemoryMb = peakMemoryMb,
                                verdict = verdict,
                                batteryTempCelsius = temp
                            )
                            benchmarkDao.insert(res)
                            if (tokensPerSec > 0.0) {
                                modelDao.updateMeasuredSpeed(model.filePath, tokensPerSec)
                            }
                            deferred.complete(Result.success(res))
                        }
                        is GenerationEvent.Error -> {
                            deferred.complete(Result.failure(event.error))
                        }
                    }
                }
            } catch (t: Throwable) {
                deferred.complete(Result.failure(t))
            }
        }

        activeBenchmarkJob = job
        val result = deferred.await()
        activeBenchmarkJob = null
        return result
    }

    fun stopBenchmark() {
        activeBenchmarkJob?.cancel()
        engineManager.getEngine().stopGeneration()
        _benchmarkState.value = BenchmarkState.Idle
        viewModelScope.launch {
            _snackbarMessage.emit("Benchmark stopped.")
        }
    }

    fun deleteBenchmarkResult(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            benchmarkDao.deleteById(id)
        }
    }

    fun clearBenchmarkHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            benchmarkDao.clearAll()
            _snackbarMessage.emit("Benchmark history cleared.")
        }
    }

    fun shareBenchmarkReport(
        context: Context,
        modelName: String,
        cpuResult: BenchmarkResultEntity? = null,
        gpuResult: BenchmarkResultEntity? = null,
        singleResult: BenchmarkResultEntity? = null
    ) {
        val report = buildBenchmarkReportText(
            context = context,
            modelName = modelName,
            cpuResult = cpuResult,
            gpuResult = gpuResult,
            singleResult = singleResult
        )
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, report)
            putExtra(Intent.EXTRA_SUBJECT, "LiteRT-LM Benchmark Report: $modelName")
            type = "text/plain"
        }
        val chooser = Intent.createChooser(sendIntent, "Share Benchmark Results")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun buildBenchmarkReportText(
        context: Context,
        modelName: String,
        cpuResult: BenchmarkResultEntity? = null,
        gpuResult: BenchmarkResultEntity? = null,
        singleResult: BenchmarkResultEntity? = null
    ): String {
        val mem = DeviceProtectionManager.getMemorySnapshot(context)
        val temp = DeviceProtectionManager.getBatteryTemperature(context)
        val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        val osVersion = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)

        return buildString {
            appendLine("==================================================")
            appendLine("LiteRT-LM On-Device Benchmark Report")
            appendLine("==================================================")
            appendLine("Model: $modelName")
            appendLine("Device: $deviceModel ($osVersion)")
            appendLine("RAM: ${mem.availMemMb} MB free / ${mem.totalMemMb} MB total")
            appendLine("Battery Temp: ${"%.1f".format(temp)}°C (Thermal safety limit: 42.0°C)")
            appendLine("Date: ${dateFormat.format(java.util.Date())}")
            appendLine()

            if (cpuResult != null && gpuResult != null) {
                appendLine("--- SIDE-BY-SIDE HARDWARE COMPARISON (${cpuResult.promptType}) ---")
                appendLine("%-22s | %-14s | %-14s".format("Metric", "CPU", "GPU"))
                appendLine("-----------------------+----------------+----------------")
                appendLine("%-22s | %-14s | %-14s".format("Load Time", "${cpuResult.loadTimeMs} ms", "${gpuResult.loadTimeMs} ms"))
                appendLine("%-22s | %-14s | %-14s".format("TTFT", "${cpuResult.timeToFirstTokenMs} ms", "${gpuResult.timeToFirstTokenMs} ms"))
                appendLine("%-22s | %-14s | %-14s".format("Decode Speed", "${"%.1f".format(cpuResult.tokensPerSecond)} tps", "${"%.1f".format(gpuResult.tokensPerSecond)} tps"))
                appendLine("%-22s | %-14s | %-14s".format("Total Tokens", "${cpuResult.totalTokens} tok", "${gpuResult.totalTokens} tok"))
                appendLine("%-22s | %-14s | %-14s".format("Peak RAM", "${"%.0f".format(cpuResult.peakMemoryMb)} MB", "${"%.0f".format(gpuResult.peakMemoryMb)} MB"))
                appendLine("%-22s | %-14s | %-14s".format("Verdict", cpuResult.verdict, gpuResult.verdict))
                appendLine()
                if (cpuResult.tokensPerSecond > 0 && gpuResult.tokensPerSecond > 0) {
                    val ratio = gpuResult.tokensPerSecond / cpuResult.tokensPerSecond
                    appendLine("GPU Speedup: ${"%.2f".format(ratio)}x relative to CPU")
                    appendLine()
                }
            } else if (singleResult != null) {
                appendLine("--- BENCHMARK RESULT (${singleResult.backend} - ${singleResult.promptType}) ---")
                appendLine("• Load Time: ${singleResult.loadTimeMs} ms")
                appendLine("• Time to First Token (TTFT): ${singleResult.timeToFirstTokenMs} ms")
                appendLine("• Decode Speed: ${"%.1f".format(singleResult.tokensPerSecond)} tokens/sec")
                appendLine("• Total Generated Tokens: ${singleResult.totalTokens} tokens")
                appendLine("• Duration: ${singleResult.durationMs} ms")
                appendLine("• Peak Process Memory: ${"%.0f".format(singleResult.peakMemoryMb)} MB")
                appendLine("• Performance Verdict: ${singleResult.verdict}")
                appendLine()
            }
            appendLine("Prompt: ${cpuResult?.promptText ?: singleResult?.promptText ?: ""}")
            appendLine("==================================================")
            appendLine("Exported from NEX Mobile Coding Assistant")
        }
    }

    fun showToast(message: String) {
        viewModelScope.launch {
            _snackbarMessage.emit(message)
        }
    }

    // Memory facts management
    fun addMemoryFact(factText: String) {
        if (factText.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val fact = MemoryFactEntity(fact = factText.trim(), isEnabled = true)
            memoryDao.insertFact(fact)
            _snackbarMessage.emit("Memory fact added.")
        }
    }

    fun updateMemoryFact(fact: MemoryFactEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            memoryDao.updateFact(fact)
        }
    }

    fun toggleMemoryFact(fact: MemoryFactEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            memoryDao.updateFact(fact.copy(isEnabled = !fact.isEnabled))
        }
    }

    fun toggleMemoryFact(factId: Long, isEnabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            memoryDao.toggleFactEnabled(factId, isEnabled)
        }
    }

    fun clearAllMemoryFacts() {
        viewModelScope.launch(Dispatchers.IO) {
            memoryDao.clearAllFacts()
            _snackbarMessage.emit("All memories cleared.")
        }
    }

    fun deleteMemoryFact(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            memoryDao.deleteFact(id)
            _snackbarMessage.emit("Fact deleted.")
        }
    }

    fun setMemoryInjectionEnabled(enabled: Boolean) {
        _isMemoryInjectionEnabled.value = enabled
        prefs.edit().putBoolean("memory_injection_enabled", enabled).apply()
    }

    // File attachments
    fun attachFilesFromUris(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val resolver = context.contentResolver
            val maxContext = _maxContextTokens.value
            val currentAttached = _attachedFiles.value.toMutableList()

            for (uri in uris) {
                try {
                    val name = getFileName(resolver, uri) ?: "file_${System.currentTimeMillis()}"
                    val stream = resolver.openInputStream(uri) ?: continue
                    val content = stream.bufferedReader().use { it.readText() }
                    val estTokens = estimateTokenCount(content)

                    if (estTokens > (maxContext * 0.8)) {
                        _snackbarMessage.emit("File '$name' refused: estimated $estTokens tokens exceeds 80% of context budget ($maxContext tokens).")
                        continue
                    }

                    val attached = AttachedFile(
                        uriString = uri.toString(),
                        name = name,
                        sizeBytes = content.toByteArray().size.toLong(),
                        lineCount = content.lines().size,
                        estimatedTokens = estTokens,
                        content = content
                    )
                    currentAttached.add(attached)
                    _snackbarMessage.emit("Attached '$name' (~$estTokens tokens)")
                } catch (t: Throwable) {
                    _snackbarMessage.emit("Failed to attach file: ${t.message}")
                }
            }
            _attachedFiles.value = currentAttached
        }
    }

    fun removeAttachedFile(uriString: String) {
        _attachedFiles.value = _attachedFiles.value.filter { it.uriString != uriString }
    }

    fun clearAttachedFiles() {
        _attachedFiles.value = emptyList()
    }

    // Project indexing
    fun selectAndIndexProjectFolder(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (t: Throwable) {
                // Ignore if not supported
            }

            val folderName = treeUri.lastPathSegment?.substringAfterLast(':') ?: "Project"
            val folderEntity = ProjectFolderEntity(
                uriString = treeUri.toString(),
                displayName = folderName,
                fileCount = 0,
                chunkCount = 0,
                lastIndexedTimestamp = System.currentTimeMillis()
            )
            projectDao.insertProject(folderEntity)
            _activeProjectUri.value = treeUri.toString()
            prefs.edit().putString("active_project_uri", treeUri.toString()).apply()

            reindexActiveProject()
        }
    }

    fun reindexActiveProject() {
        val active = activeProject.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val treeUri = Uri.parse(active.uriString)

            val (updatedProject, chunks) = ProjectIndexer.indexFolder(
                context = context,
                treeUri = treeUri,
                onProgress = { progress ->
                    _indexingProgress.value = progress
                }
            )
            projectDao.replaceProjectChunks(updatedProject, chunks)

            _indexingProgress.value = null
            _snackbarMessage.emit("Indexing complete for ${active.displayName} (${updatedProject.fileCount} files, ${chunks.size} chunks)")
        }
    }

    fun clearActiveProject() {
        val active = activeProject.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            projectDao.deleteProject(active.uriString)
            _activeProjectUri.value = null
            prefs.edit().remove("active_project_uri").apply()
            _snackbarMessage.emit("Project removed.")
        }
    }

    fun prefillInput(text: String) {
        viewModelScope.launch {
            _inputPrefill.emit(text)
        }
    }

    fun prefillInputForEdit(text: String) {
        prefillInput(text)
    }

    fun setSystemPrompt(prompt: String) {
        _systemPrompt.value = prompt
        prefs.edit().putString("system_prompt", prompt).apply()
    }

    fun applySystemPromptPreset(preset: String) {
        setSystemPrompt(preset)
    }

    fun setMaxContextTokens(tokens: Int) {
        _maxContextTokens.value = tokens
        prefs.edit().putInt("max_context_tokens", tokens).apply()
    }

    fun setUseGpu(enabled: Boolean) {
        engineManager.setUseGpu(enabled)
    }

    fun selectModel(filePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            modelDao.clearLastUsed()
            modelDao.setLastUsed(filePath)
            _snackbarMessage.emit("Model selected as active default.")
        }
    }

    fun loadModel(filePath: String, useGpu: Boolean = useGpuSetting.value) {
        viewModelScope.launch(Dispatchers.IO) {
            modelDao.clearLastUsed()
            modelDao.setLastUsed(filePath)
            modelDao.clearLoaded()
            modelDao.setLoaded(filePath)
            engineManager.loadModel(filePath, useGpu)
        }
    }

    fun loadLastUsedModel() {
        val lastUsed = lastUsedModel.value ?: return
        loadModel(lastUsed.filePath, useGpuSetting.value)
    }

    fun unloadModel() {
        viewModelScope.launch(Dispatchers.IO) {
            modelDao.clearLoaded()
            engineManager.unloadModel()
        }
    }

    fun deleteModel(model: ModelEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val status = engineStatus.value
            if (model.isLoaded || (status is EngineStatus.Ready && status.modelName == model.name)) {
                modelDao.clearLoaded()
                engineManager.unloadModel()
            }
            val file = File(model.filePath)
            if (file.exists()) {
                file.delete()
            }
            val partFile = File("${model.filePath}.part")
            if (partFile.exists()) {
                partFile.delete()
            }
            modelDao.deleteModel(model.id)
            _snackbarMessage.emit("Model '${model.name}' deleted.")
        }
    }

    fun importModelFromFile(uri: Uri) {
        viewModelScope.launch {
            val result = ModelFileImporter.importFile(
                context = getApplication(),
                uri = uri,
                onProgress = { progress ->
                    _importProgress.value = progress
                }
            )
            _importProgress.value = null
            result.onSuccess { file ->
                _snackbarMessage.emit("Imported '${file.name}' into models folder.")
            }.onFailure { error ->
                _snackbarMessage.emit("Import failed: ${error.message}")
            }
        }
    }

    fun importModelFromUri(uri: Uri) = importModelFromFile(uri)

    fun setWifiOnlyDownload(enabled: Boolean) {
        downloadManager.setWifiOnly(enabled)
    }

    fun installStarterModel(catalogModel: StarterCatalogModel) {
        downloadManager.startDownload(
            url = catalogModel.downloadUrl,
            customFileName = catalogModel.fileName,
            metadata = catalogModel.toDownloadMetadata()
        )
    }

    fun startDownload(
        url: String,
        customFileName: String? = null,
        hfToken: String? = null,
        metadata: DownloadMetadata = DownloadMetadata()
    ) {
        viewModelScope.launch {
            if (!hfToken.isNullOrBlank()) {
                EncryptedTokenStorage.saveToken(getApplication(), hfToken)
                _hasHfToken.value = true
            }
            downloadManager.startDownload(url, customFileName, metadata)
        }
    }

    fun pauseDownload() {
        downloadManager.pauseDownload()
    }

    fun resumeDownload() {
        downloadManager.resumeDownload()
    }

    fun retryDownload() {
        downloadManager.retryDownload()
    }

    fun cancelDownload(deletePartial: Boolean = false) {
        downloadManager.cancelDownload(deletePartial)
    }

    fun saveHfToken(token: String) {
        EncryptedTokenStorage.saveToken(getApplication(), token)
        _hasHfToken.value = token.isNotBlank()
        showToast(if (token.isNotBlank()) "HuggingFace token saved." else "HuggingFace token cleared.")
    }

    // Feedback rating on message
    fun setFeedback(messageId: Long, feedback: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.updateFeedback(messageId, feedback)
            val text = when (feedback) {
                1 -> "Thumbs up recorded 👍"
                -1 -> "Thumbs down recorded 👎"
                else -> "Feedback cleared"
            }
            _snackbarMessage.emit(text)
        }
    }

    // Save as training example to Room
    fun saveTrainingExample(
        systemPrompt: String,
        userPrompt: String,
        assistantAnswer: String,
        feedback: Int
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentModel = lastUsedModel.value?.name
            val example = DatasetExampleEntity(
                systemPrompt = systemPrompt.trim(),
                userPrompt = userPrompt.trim(),
                assistantAnswer = assistantAnswer.trim(),
                feedback = feedback,
                timestamp = System.currentTimeMillis(),
                modelName = currentModel
            )
            datasetDao.insertExample(example)
            _snackbarMessage.emit("Saved training example to local dataset.")
        }
    }

    fun updateTrainingExample(example: DatasetExampleEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            datasetDao.updateExample(example)
            _snackbarMessage.emit("Training example updated.")
        }
    }

    fun deleteTrainingExample(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            datasetDao.deleteExample(id)
            _snackbarMessage.emit("Training example deleted.")
        }
    }

    fun clearAllTrainingExamples() {
        viewModelScope.launch(Dispatchers.IO) {
            datasetDao.clearAllExamples()
            _snackbarMessage.emit("All training examples deleted.")
        }
    }

    // Export dataset to JSONL (one JSON object per line in chat format)
    fun exportDatasetToJsonl(context: Context, destinationUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val examples = datasetDao.getAllExamplesList()
                if (examples.isEmpty()) {
                    _snackbarMessage.emit("Dataset is empty. Save examples first!")
                    return@launch
                }

                context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                    output.bufferedWriter(Charsets.UTF_8).use { writer ->
                        for (example in examples) {
                            val messagesArray = JSONArray()
                            if (example.systemPrompt.isNotBlank()) {
                                val sysObj = JSONObject()
                                sysObj.put("role", "system")
                                sysObj.put("content", example.systemPrompt)
                                messagesArray.put(sysObj)
                            }

                            val userObj = JSONObject()
                            userObj.put("role", "user")
                            userObj.put("content", example.userPrompt)
                            messagesArray.put(userObj)

                            val assistantObj = JSONObject()
                            assistantObj.put("role", "assistant")
                            assistantObj.put("content", example.assistantAnswer)
                            messagesArray.put(assistantObj)

                            val lineObj = JSONObject()
                            lineObj.put("messages", messagesArray)
                            if (example.feedback != 0) {
                                lineObj.put("feedback", if (example.feedback > 0) "thumbs_up" else "thumbs_down")
                            }
                            if (!example.modelName.isNullOrBlank()) {
                                lineObj.put("model", example.modelName)
                            }

                            writer.write(lineObj.toString())
                            writer.newLine()
                        }
                        writer.flush()
                    }
                }
                _snackbarMessage.emit("Exported ${examples.size} training example(s) to JSONL successfully!")
            } catch (t: Throwable) {
                _snackbarMessage.emit("Export failed: ${t.message}")
            }
        }
    }

    // Import dataset from JSONL
    fun importDatasetFromJsonl(context: Context, sourceUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val importedList = mutableListOf<DatasetExampleEntity>()
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    input.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty()) {
                            try {
                                val obj = JSONObject(trimmed)
                                var systemContent = ""
                                var userContent = ""
                                var assistantContent = ""

                                if (obj.has("messages")) {
                                    val msgs = obj.getJSONArray("messages")
                                    for (i in 0 until msgs.length()) {
                                        val m = msgs.getJSONObject(i)
                                        val role = m.optString("role")
                                        val content = m.optString("content")
                                        when (role) {
                                            "system" -> systemContent = content
                                            "user" -> userContent = content
                                            "assistant" -> assistantContent = content
                                        }
                                    }
                                } else {
                                    systemContent = obj.optString("system", "")
                                    userContent = obj.optString("prompt", obj.optString("instruction", ""))
                                    assistantContent = obj.optString("response", obj.optString("output", ""))
                                }

                                val feedbackStr = obj.optString("feedback", "")
                                val fb = when (feedbackStr) {
                                    "thumbs_up" -> 1
                                    "thumbs_down" -> -1
                                    else -> 0
                                }
                                val model = obj.optString("model", null)

                                if (userContent.isNotBlank() || assistantContent.isNotBlank()) {
                                    importedList.add(
                                        DatasetExampleEntity(
                                            systemPrompt = systemContent,
                                            userPrompt = userContent,
                                            assistantAnswer = assistantContent,
                                            feedback = fb,
                                            timestamp = System.currentTimeMillis(),
                                            modelName = model
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                // Skip malformed lines
                            }
                        }
                    }
                }

                if (importedList.isNotEmpty()) {
                    datasetDao.insertExamples(importedList)
                    _snackbarMessage.emit("Imported ${importedList.size} training example(s) from JSONL.")
                } else {
                    _snackbarMessage.emit("No valid chat training examples found in JSONL file.")
                }
            } catch (t: Throwable) {
                _snackbarMessage.emit("Import failed: ${t.message}")
            }
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank() && _attachedFiles.value.isEmpty()) return
        if (_isGenerating.value) return

        val engine = engineManager.getEngine()
        if (!engine.isModelLoaded) {
            viewModelScope.launch {
                _snackbarMessage.emit("Select a model first")
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val currentAttached = _attachedFiles.value
            _attachedFiles.value = emptyList()

            // 1. Check for Project context retrieval
            var promptPrefix = ""
            var sourcesString: String? = null
            val active = activeProject.value
            if (active != null) {
                val chunks = projectDao.getChunksForProject(active.uriString)
                if (chunks.isNotEmpty()) {
                    val ranked = ProjectIndexer.searchAndRank(chunks = chunks, query = userText, topK = 3)
                    if (ranked.isNotEmpty()) {
                        promptPrefix += buildString {
                            appendLine("[Project Code Context: ${active.displayName}]")
                            ranked.forEach { src ->
                                appendLine("File: ${src.filePath} (lines ${src.startLine}-${src.endLine})")
                                appendLine(src.content)
                                appendLine()
                            }
                            appendLine("--- End of Context ---")
                            appendLine()
                        }
                        sourcesString = ranked.joinToString(";") { "${it.filePath}:${it.startLine}-${it.endLine}" }
                    }
                }
            }

            // 2. Attach any uploaded files to prompt
            if (currentAttached.isNotEmpty()) {
                promptPrefix += buildString {
                    appendLine("[Attached User Files]")
                    currentAttached.forEach { f ->
                        appendLine("File: ${f.name}")
                        appendLine("```")
                        appendLine(f.content)
                        appendLine("```")
                        appendLine()
                    }
                    appendLine("--- End of Attached Files ---")
                    appendLine()
                }
            }

            val finalUserPrompt = if (promptPrefix.isNotEmpty()) "$promptPrefix$userText" else userText

            val userMsg = ChatMessageEntity(
                role = "user",
                content = userText
            )
            chatDao.insertMessage(userMsg)

            _isGenerating.value = true
            _streamingText.value = ""

            // Combine personal memory into system prompt if enabled
            val baseSysPrompt = _systemPrompt.value
            val memorySnippet = if (_isMemoryInjectionEnabled.value) injectedMemoryText.value else ""
            val fullSystemInstruction = if (memorySnippet.isNotBlank()) {
                "$baseSysPrompt\n\n$memorySnippet"
            } else {
                baseSysPrompt
            }

            val allHistory = chatDao.getAllMessagesList()
            val (guardedHistory, droppedCount) = applyContextGuard(
                systemPrompt = fullSystemInstruction,
                newPrompt = finalUserPrompt,
                history = allHistory,
                maxTokens = _maxContextTokens.value
            )

            if (droppedCount > 0) {
                _snackbarMessage.emit("Context guard: dropped $droppedCount older message(s) to preserve context limits.")
            }

            activeGenerationJob = viewModelScope.launch(Dispatchers.IO) {
                var collectedText = ""
                var statsToSave: GenerationStats? = null

                try {
                    val flow = engine.streamGenerate(
                        prompt = finalUserPrompt,
                        systemInstruction = fullSystemInstruction,
                        history = guardedHistory
                    )

                    flow.collect { event ->
                        when (event) {
                            is GenerationEvent.Token -> {
                                collectedText += event.text
                                _streamingText.value = collectedText
                            }
                            is GenerationEvent.Complete -> {
                                statsToSave = event.stats
                            }
                            is GenerationEvent.Error -> {
                                _snackbarMessage.emit(event.message)
                            }
                        }
                    }

                    if (collectedText.isNotEmpty()) {
                        val (thinkingContent, answerContent) = MarkdownHelper.parseThinking(collectedText)
                        val tps = statsToSave?.tokensPerSecond ?: 0.0
                        val assistantMsg = ChatMessageEntity(
                            role = "assistant",
                            content = answerContent.ifEmpty { collectedText },
                            thinking = thinkingContent,
                            tokensPerSecond = tps,
                            modelLoadTimeMs = statsToSave?.loadTimeMs ?: 0L,
                            timeToFirstTokenMs = statsToSave?.timeToFirstTokenMs ?: 0L,
                            backendUsed = statsToSave?.backend,
                            sources = sourcesString
                        )
                        chatDao.insertMessage(assistantMsg)
                        if (tps > 0.0) {
                            lastUsedModel.value?.filePath?.let { path ->
                                modelDao.updateMeasuredSpeed(path, tps)
                            }
                        }
                    }

                } catch (t: Throwable) {
                    _snackbarMessage.emit("Generation stopped: ${t.message}")
                } finally {
                    _streamingText.value = null
                    _isGenerating.value = false
                    activeGenerationJob = null
                }
            }
        }
    }

    private fun applyContextGuard(
        systemPrompt: String,
        newPrompt: String,
        history: List<ChatMessageEntity>,
        maxTokens: Int
    ): Pair<List<ChatMessageEntity>, Int> {
        val sysTokens = estimateTokenCount(systemPrompt)
        val newPromptTokens = estimateTokenCount(newPrompt)
        val safetyBuffer = 200 // reserve tokens for generation headroom
        val availableHistoryTokens = maxTokens - sysTokens - newPromptTokens - safetyBuffer

        if (availableHistoryTokens <= 0 || history.isEmpty()) {
            return Pair(emptyList(), history.size)
        }

        val included = mutableListOf<ChatMessageEntity>()
        var accumulated = 0
        var dropped = 0

        // Walk backwards from newest to oldest
        for (i in history.indices.reversed()) {
            val msg = history[i]
            val msgTokens = estimateTokenCount(msg.content) + estimateTokenCount(msg.thinking ?: "")
            if (accumulated + msgTokens <= availableHistoryTokens) {
                included.add(0, msg)
                accumulated += msgTokens
            } else {
                dropped++
            }
        }

        return Pair(included, dropped)
    }

    fun estimateTokenCount(text: String): Int {
        if (text.isEmpty()) return 0
        return (text.length / 4).coerceAtLeast(1)
    }

    fun stopGeneration() {
        if (_isGenerating.value) {
            engineManager.getEngine().stopGeneration()
            activeGenerationJob?.cancel()

            val rawPartial = _streamingText.value?.trim() ?: ""
            if (rawPartial.isNotEmpty()) {
                val (thinkingContent, answerContent) = MarkdownHelper.parseThinking(rawPartial)
                viewModelScope.launch(Dispatchers.IO) {
                    val stoppedMsg = ChatMessageEntity(
                        role = "assistant",
                        content = "${answerContent.ifEmpty { rawPartial }}\n\n[Generation stopped by user]",
                        thinking = thinkingContent
                    )
                    chatDao.insertMessage(stoppedMsg)
                }
            }

            _streamingText.value = null
            _isGenerating.value = false
            activeGenerationJob = null
        }
    }

    fun regenerateLastAnswer() {
        viewModelScope.launch(Dispatchers.IO) {
            val lastUserMsg = chatDao.getAllMessagesList().lastOrNull { it.role == "user" }
            if (lastUserMsg != null) {
                // Remove the last assistant message if it exists
                chatDao.getAllMessagesList().lastOrNull { it.role == "assistant" }?.let {
                    chatDao.deleteMessage(it.id)
                }
                sendMessage(lastUserMsg.content)
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.clearAllMessages()
        }
    }

    private fun getFileName(resolver: ContentResolver, uri: Uri): String? {
        var name: String? = null
        val cursor = resolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx != -1) {
                    name = it.getString(nameIdx)
                }
            }
        }
        return name ?: uri.lastPathSegment
    }
}
