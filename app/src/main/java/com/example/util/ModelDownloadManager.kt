package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.os.SystemClock
import com.example.data.AppDatabase
import com.example.data.ModelEntity
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DownloadMetadata(
    val displayName: String? = null,
    val tier: String? = null,
    val minRamGb: Double? = null,
    val recommendedRamGb: Double? = null,
    val task: String = "chat",
    val licenseNote: String = "",
    val requiresLicenseAcceptance: Boolean = false,
    val isCurated: Boolean = false,
    val expectedSizeBytes: Long = -1L
)

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Checking(
        val message: String,
        val fileName: String? = null,
        val url: String? = null
    ) : DownloadState
    data class Progress(
        val fileName: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long,
        val remainingSeconds: Long,
        val isPaused: Boolean,
        val isCancelledResumable: Boolean = false,
        val url: String? = null
    ) : DownloadState {
        val percent: Int
            get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

        val formattedSpeed: String
            get() {
                val speedKb = speedBytesPerSec / 1024.0
                return if (speedKb >= 1024) {
                    "%.1f MB/s".format(speedKb / 1024.0)
                } else {
                    "%.0f KB/s".format(speedKb)
                }
            }

        val formattedEta: String
            get() = when {
                remainingSeconds <= 0 -> "--"
                remainingSeconds < 60 -> "${remainingSeconds}s"
                remainingSeconds < 3600 -> "${remainingSeconds / 60}m ${remainingSeconds % 60}s"
                else -> "${remainingSeconds / 3600}h ${(remainingSeconds % 3600) / 60}m"
            }
    }
    data class Completed(val file: File) : DownloadState
    data class Error(
        val message: String,
        val fileName: String? = null,
        val url: String? = null
    ) : DownloadState
}

class ModelDownloadManager(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _wifiOnly = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, true))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private var currentUrl: String? = null
    private var currentFileName: String? = null
    private var currentMetadata: DownloadMetadata = DownloadMetadata()
    private var isPauseRequested = false

    fun setWifiOnly(enabled: Boolean) {
        _wifiOnly.value = enabled
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

    fun startDownload(
        url: String,
        customFileName: String? = null,
        metadata: DownloadMetadata = DownloadMetadata()
    ) {
        val cleanUrl = url.trim()
        currentUrl = cleanUrl
        currentMetadata = metadata
        isPauseRequested = false
        val inferredName = customFileName?.takeIf { it.isNotBlank() }
            ?: extractFileNameFromUrl(cleanUrl)
        val normalizedName = if (inferredName.endsWith(".litertlm", ignoreCase = true)) {
            inferredName
        } else {
            "$inferredName.litertlm"
        }
        currentFileName = normalizedName

        ModelDownloadService.start(appContext)

        downloadJob?.cancel()
        downloadJob = scope.launch {
            runDownloadLoop(cleanUrl, normalizedName, metadata)
        }
    }

    fun pauseDownload() {
        isPauseRequested = true
        val current = _downloadState.value
        if (current is DownloadState.Progress) {
            _downloadState.value = current.copy(
                isPaused = true,
                isCancelledResumable = false,
                speedBytesPerSec = 0
            )
        }
        downloadJob?.cancel()
        downloadJob = null
    }

    fun resumeDownload() {
        val url = currentUrl
        val fileName = currentFileName
        if (url != null && fileName != null) {
            isPauseRequested = false
            ModelDownloadService.start(appContext)
            downloadJob?.cancel()
            downloadJob = scope.launch {
                runDownloadLoop(url, fileName, currentMetadata)
            }
        }
    }

    fun retryDownload() {
        resumeDownload()
    }

    /**
     * Cancels the active download. Keeps the partial .part file on disk when [deletePartial] is false
     * so that the user can resume the download later (either by tapping Resume/Retry or tapping Install again).
     */
    fun cancelDownload(deletePartial: Boolean = false) {
        isPauseRequested = true
        downloadJob?.cancel()
        downloadJob = null

        val fileName = currentFileName
        val current = _downloadState.value

        if (deletePartial || current !is DownloadState.Progress) {
            if (deletePartial && fileName != null) {
                val modelsDir = getModelsDir(appContext)
                val partFile = File(modelsDir, "$fileName.part")
                if (partFile.exists()) {
                    partFile.delete()
                }
            }
            _downloadState.value = DownloadState.Idle
        } else {
            // Keep partial file on disk so user can resume after cancel
            _downloadState.value = current.copy(
                isPaused = true,
                isCancelledResumable = true,
                speedBytesPerSec = 0,
                remainingSeconds = -1
            )
        }
    }

    private suspend fun runDownloadLoop(
        urlString: String,
        fileName: String,
        metadata: DownloadMetadata
    ) = withContext(Dispatchers.IO) {
        val modelsDir = getModelsDir(appContext)
        val finalFile = File(modelsDir, fileName)
        val partFile = File(modelsDir, "$fileName.part")

        _downloadState.value = DownloadState.Checking(
            message = "Checking network & verifying free storage...",
            fileName = fileName,
            url = urlString
        )

        // 0. Check Wi-Fi only & network state before starting
        val networkRestrictionError = checkNetworkRestriction(appContext, _wifiOnly.value)
        if (networkRestrictionError != null) {
            _downloadState.value = DownloadState.Error(
                message = networkRestrictionError,
                fileName = fileName,
                url = urlString
            )
            return@withContext
        }

        // Never log the Hugging Face token; starter catalog models do not require a token
        val token = EncryptedTokenStorage.getToken(appContext)
        var totalBytes = -1L
        var existingBytes = if (partFile.exists()) partFile.length() else 0L

        // 1. Probe file size & range support
        try {
            val checkUrl = URL(urlString)
            val headConn = (checkUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }

            val responseCode = headConn.responseCode
            if (responseCode in 200..299) {
                totalBytes = headConn.contentLengthLong
            }
            headConn.disconnect()

            if (totalBytes <= 0) {
                val probeConn = (checkUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Range", "bytes=0-0")
                    connectTimeout = 15000
                    readTimeout = 15000
                    instanceFollowRedirects = true
                    if (!token.isNullOrBlank()) {
                        setRequestProperty("Authorization", "Bearer $token")
                    }
                }
                val contentRange = probeConn.getHeaderField("Content-Range")
                if (contentRange != null && contentRange.contains("/")) {
                    val totalStr = contentRange.substringAfterLast("/")
                    totalBytes = totalStr.toLongOrNull() ?: -1L
                } else if (probeConn.contentLengthLong > 0) {
                    totalBytes = probeConn.contentLengthLong
                }
                probeConn.disconnect()
            }
        } catch (_: Exception) {
            // Will check during GET
        }

        if (totalBytes <= 0 && metadata.expectedSizeBytes > 0) {
            totalBytes = metadata.expectedSizeBytes
        }

        // If partial file is somehow larger than server file size, reset corrupted partial file
        if (totalBytes > 0 && existingBytes > totalBytes) {
            partFile.delete()
            existingBytes = 0L
        }

        // 2. Check free storage before starting (model size + 500 MB safety buffer)
        val statFs = StatFs(modelsDir.path)
        val availableDiskBytes = statFs.availableBytes
        val requiredBytes = if (totalBytes > 0) (totalBytes - existingBytes).coerceAtLeast(0L) else 100L * 1024L * 1024L
        val storageBufferBytes = 500L * 1024L * 1024L

        if (availableDiskBytes < requiredBytes + storageBufferBytes) {
            val reqMb = requiredBytes / (1024 * 1024)
            val availMb = availableDiskBytes / (1024 * 1024)
            _downloadState.value = DownloadState.Error(
                message = "Not enough storage space: download requires ${reqMb} MB (+500 MB buffer), but only ${availMb} MB is free.",
                fileName = fileName,
                url = urlString
            )
            return@withContext
        }

        // 3. Resumable GET connection (HTTP Range)
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        var connection: HttpURLConnection? = null

        try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20000
                readTimeout = 20000
                instanceFollowRedirects = true
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                if (existingBytes > 0) {
                    setRequestProperty("Range", "bytes=$existingBytes-")
                }
            }

            val code = connection.responseCode
            val isPartial = (code == HttpURLConnection.HTTP_PARTIAL)
            val isOk = (code == HttpURLConnection.HTTP_OK)

            // Handle 416 Range Not Satisfiable when partial file already equals complete file
            if (code == 416 && totalBytes > 0 && existingBytes == totalBytes) {
                finalizeCompletedDownload(
                    partFile = partFile,
                    finalFile = finalFile,
                    totalBytes = totalBytes,
                    urlString = urlString,
                    metadata = metadata
                )
                return@withContext
            }

            if (!isPartial && !isOk) {
                val msg = when (code) {
                    HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                        "HTTP $code: Access denied. Check your Hugging Face access token or repository license."
                    HttpURLConnection.HTTP_NOT_FOUND ->
                        "HTTP 404: Model file not found at the provided URL."
                    else -> "Server returned HTTP error code $code"
                }
                _downloadState.value = DownloadState.Error(
                    message = msg,
                    fileName = fileName,
                    url = urlString
                )
                return@withContext
            }

            if (isOk && existingBytes > 0) {
                // Server ignored Range header and sent full stream; restart partial file cleanly
                existingBytes = 0L
                partFile.delete()
            }

            if (totalBytes <= 0) {
                val cl = connection.contentLengthLong
                if (cl > 0) {
                    totalBytes = if (isPartial) existingBytes + cl else cl
                }
            }

            // Re-verify free storage if totalBytes was only discovered on GET
            if (totalBytes > 0) {
                val remainingNeeded = (totalBytes - existingBytes).coerceAtLeast(0L)
                if (statFs.availableBytes < remainingNeeded + storageBufferBytes) {
                    val reqMb = remainingNeeded / (1024 * 1024)
                    val availMb = statFs.availableBytes / (1024 * 1024)
                    _downloadState.value = DownloadState.Error(
                        message = "Not enough storage space: download requires ${reqMb} MB (+500 MB buffer), but only ${availMb} MB is free.",
                        fileName = fileName,
                        url = urlString
                    )
                    return@withContext
                }
            }

            val inStream = connection.inputStream
            inputStream = inStream
            val outStream = FileOutputStream(partFile, isPartial)
            outputStream = outStream

            val buffer = ByteArray(32 * 1024)
            var bytesDownloaded = existingBytes
            var lastUpdateTime = SystemClock.elapsedRealtime()
            var bytesSinceLastUpdate = 0L
            var currentSpeed: Long

            _downloadState.value = DownloadState.Progress(
                fileName = fileName,
                downloadedBytes = bytesDownloaded,
                totalBytes = totalBytes,
                speedBytesPerSec = 0L,
                remainingSeconds = -1L,
                isPaused = false,
                url = urlString
            )

            while (isActive && !isPauseRequested) {
                // Check if Wi-Fi dropped to cellular while Wi-Fi only is enabled
                if (_wifiOnly.value) {
                    val midCheckError = checkNetworkRestriction(appContext, true)
                    if (midCheckError != null) {
                        outStream.flush()
                        outStream.close()
                        outputStream = null
                        _downloadState.value = DownloadState.Error(
                            message = midCheckError,
                            fileName = fileName,
                            url = urlString
                        )
                        return@withContext
                    }
                }

                val read = inStream.read(buffer)
                if (read == -1) break

                outStream.write(buffer, 0, read)
                bytesDownloaded += read
                bytesSinceLastUpdate += read

                val now = SystemClock.elapsedRealtime()
                val elapsed = now - lastUpdateTime
                if (elapsed >= 400) {
                    currentSpeed = (bytesSinceLastUpdate * 1000) / elapsed
                    val remainingBytes = (totalBytes - bytesDownloaded).coerceAtLeast(0)
                    val etaSeconds = if (currentSpeed > 0 && totalBytes > 0) remainingBytes / currentSpeed else -1L

                    _downloadState.value = DownloadState.Progress(
                        fileName = fileName,
                        downloadedBytes = bytesDownloaded,
                        totalBytes = totalBytes,
                        speedBytesPerSec = currentSpeed,
                        remainingSeconds = etaSeconds,
                        isPaused = false,
                        url = urlString
                    )

                    lastUpdateTime = now
                    bytesSinceLastUpdate = 0L
                }
            }

            outStream.flush()
            outStream.close()
            outputStream = null

            if (isPauseRequested || !isActive) {
                _downloadState.value = DownloadState.Progress(
                    fileName = fileName,
                    downloadedBytes = bytesDownloaded,
                    totalBytes = totalBytes,
                    speedBytesPerSec = 0,
                    remainingSeconds = -1,
                    isPaused = true,
                    url = urlString
                )
                return@withContext
            }

            // 4. Verify the file size at the end and delete a corrupted partial file
            finalizeCompletedDownload(
                partFile = partFile,
                finalFile = finalFile,
                totalBytes = totalBytes,
                urlString = urlString,
                metadata = metadata
            )

        } catch (c: CancellationException) {
            // Cancelled or paused cleanly; partial file remains on disk for HTTP Range resume
        } catch (e: Exception) {
            // Network drop or read error: keep partial file so user can tap Retry to resume
            val detail = e.localizedMessage?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            _downloadState.value = DownloadState.Error(
                message = "Download interrupted ($detail). Tap Retry to resume from where it left off.",
                fileName = fileName,
                url = urlString
            )
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
                connection?.disconnect()
            } catch (_: Exception) {}
        }
    }

    private suspend fun finalizeCompletedDownload(
        partFile: File,
        finalFile: File,
        totalBytes: Long,
        urlString: String,
        metadata: DownloadMetadata
    ) {
        val actualSize = partFile.length()
        if (actualSize <= 0L || (totalBytes > 0L && actualSize != totalBytes)) {
            partFile.delete()
            _downloadState.value = DownloadState.Error(
                message = "Corrupted download: expected ${if (totalBytes > 0) "$totalBytes bytes" else "valid model file"}, but received $actualSize bytes. Corrupted partial file was deleted.",
                fileName = finalFile.name,
                url = urlString
            )
            return
        }

        if (finalFile.exists()) {
            finalFile.delete()
        }

        if (partFile.renameTo(finalFile)) {
            val finalBytes = finalFile.length()
            val db = AppDatabase.getInstance(appContext)
            val modelDao = db.modelDao()
            val existing = modelDao.getModelByPath(finalFile.absolutePath)

            val resolvedName = metadata.displayName?.takeIf { it.isNotBlank() } ?: finalFile.name
            val resolvedTier = metadata.tier ?: ModelEntity.inferTier(finalBytes)
            val resolvedMinRam = metadata.minRamGb ?: ModelEntity.inferMinRamGb(finalBytes)
            val resolvedRecRam = metadata.recommendedRamGb ?: ModelEntity.inferRecommendedRamGb(finalBytes)

            if (existing == null) {
                modelDao.insertModel(
                    ModelEntity(
                        name = resolvedName,
                        filePath = finalFile.absolutePath,
                        sizeBytes = finalBytes,
                        downloadUrl = urlString,
                        isCurated = metadata.isCurated,
                        tier = resolvedTier,
                        minRamGb = resolvedMinRam,
                        recommendedRamGb = resolvedRecRam,
                        task = metadata.task,
                        licenseNote = metadata.licenseNote,
                        requiresLicenseAcceptance = metadata.requiresLicenseAcceptance,
                        sourceLink = urlString
                    )
                )
            } else {
                modelDao.updateModel(
                    existing.copy(
                        name = resolvedName,
                        sizeBytes = finalBytes,
                        downloadUrl = urlString,
                        sourceLink = urlString,
                        isCurated = metadata.isCurated,
                        tier = resolvedTier,
                        minRamGb = resolvedMinRam,
                        recommendedRamGb = resolvedRecRam,
                        task = metadata.task,
                        licenseNote = metadata.licenseNote,
                        requiresLicenseAcceptance = metadata.requiresLicenseAcceptance
                    )
                )
            }

            _downloadState.value = DownloadState.Completed(finalFile)
        } else {
            _downloadState.value = DownloadState.Error(
                message = "Failed to finalize downloaded model file.",
                fileName = finalFile.name,
                url = urlString
            )
        }
    }

    companion object {
        private const val PREFS_NAME = "nex_settings"
        private const val KEY_WIFI_ONLY = "wifi_only_download"

        @Volatile
        private var instance: ModelDownloadManager? = null

        fun getInstance(context: Context): ModelDownloadManager {
            return instance ?: synchronized(this) {
                instance ?: ModelDownloadManager(context.applicationContext).also { instance = it }
            }
        }

        fun getModelsDir(context: Context): File {
            val dir = File(context.filesDir, "models")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

        fun extractFileNameFromUrl(url: String): String {
            val clean = url.substringBefore("?").substringBefore("#")
            val rawName = clean.substringAfterLast("/")
            return if (rawName.endsWith(".litertlm", ignoreCase = true)) {
                rawName
            } else {
                "${rawName.ifBlank { "model" }}.litertlm"
            }
        }

        /**
         * Returns an error message if the download should be blocked due to network state or Wi-Fi only setting,
         * or null if the download is allowed to proceed.
         */
        fun checkNetworkRestriction(context: Context, wifiOnly: Boolean): String? {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
            val activeNetwork = cm.activeNetwork
                ?: return "No internet connection available. Connect to a network and tap Retry."
            val capabilities = cm.getNetworkCapabilities(activeNetwork)
                ?: return "Unable to verify network connection. Check your connection and tap Retry."

            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return "No internet access on the current network. Check your connection and tap Retry."
            }

            if (wifiOnly) {
                val isWifiOrEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                val isUnmeteredNonCellular = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
                    !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

                if (!isWifiOrEthernet && !isUnmeteredNonCellular) {
                    return "Wi-Fi only mode is enabled. Connect to Wi-Fi or turn off 'Wi-Fi only' to download over mobile data."
                }
            }
            return null
        }
    }
}
