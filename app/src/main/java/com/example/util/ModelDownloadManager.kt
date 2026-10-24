package com.example.util

import android.content.Context
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Checking(val message: String) : DownloadState
    data class Progress(
        val fileName: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long,
        val remainingSeconds: Long,
        val isPaused: Boolean
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
    data class Error(val message: String) : DownloadState
}

class ModelDownloadManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var downloadJob: Job? = null

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var currentUrl: String? = null
    private var currentFileName: String? = null
    private var isPauseRequested = false

    fun startDownload(url: String, customFileName: String? = null) {
        currentUrl = url
        isPauseRequested = false
        val inferredName = customFileName?.takeIf { it.isNotBlank() }
            ?: extractFileNameFromUrl(url)
        currentFileName = inferredName

        downloadJob?.cancel()
        downloadJob = scope.launch {
            runDownloadLoop(url, inferredName)
        }
    }

    fun pauseDownload() {
        isPauseRequested = true
        val current = _downloadState.value
        if (current is DownloadState.Progress) {
            _downloadState.value = current.copy(isPaused = true, speedBytesPerSec = 0)
        }
        downloadJob?.cancel()
        downloadJob = null
    }

    fun resumeDownload() {
        val url = currentUrl
        val fileName = currentFileName
        if (url != null && fileName != null) {
            isPauseRequested = false
            downloadJob?.cancel()
            downloadJob = scope.launch {
                runDownloadLoop(url, fileName)
            }
        }
    }

    fun cancelDownload() {
        isPauseRequested = false
        downloadJob?.cancel()
        downloadJob = null

        val fileName = currentFileName
        if (fileName != null) {
            val modelsDir = getModelsDir(context)
            val partFile = File(modelsDir, "$fileName.part")
            if (partFile.exists()) {
                partFile.delete()
            }
        }
        _downloadState.value = DownloadState.Idle
        currentUrl = null
        currentFileName = null
    }

    private suspend fun runDownloadLoop(urlString: String, fileName: String) = withContext(Dispatchers.IO) {
        val modelsDir = getModelsDir(context)
        val finalFile = File(modelsDir, fileName)
        val partFile = File(modelsDir, "$fileName.part")

        _downloadState.value = DownloadState.Checking("Connecting and verifying free storage...")

        val token = EncryptedTokenStorage.getToken(context)
        var totalBytes = -1L
        var existingBytes = if (partFile.exists()) partFile.length() else 0L

        // 1. Initial connection to query file size & server capabilities
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

            // Fallback to GET if HEAD failed to return content length
            if (totalBytes <= 0) {
                val probeConn = (checkUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Range", "bytes=0-0")
                    connectTimeout = 15000
                    readTimeout = 15000
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
        } catch (e: Exception) {
            // Non-fatal if server doesn't respond to probe, we'll check during GET
        }

        // 2. Storage Check
        val statFs = StatFs(modelsDir.path)
        val availableDiskBytes = statFs.availableBytes
        val requiredBytes = if (totalBytes > 0) totalBytes - existingBytes else 100 * 1024 * 1024

        if (totalBytes > 0 && availableDiskBytes < requiredBytes + (50 * 1024 * 1024)) { // 50MB safety margin
            val reqMb = requiredBytes / (1024 * 1024)
            val availMb = availableDiskBytes / (1024 * 1024)
            _downloadState.value = DownloadState.Error(
                "Insufficient storage: Model download needs ${reqMb} MB free space, but device has only ${availMb} MB available."
            )
            return@withContext
        }

        // 3. Resumable GET connection
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

            if (!isPartial && !isOk) {
                _downloadState.value = DownloadState.Error("Server returned HTTP error code $code")
                return@withContext
            }

            if (isOk && existingBytes > 0) {
                // Server does not support resume or returned whole file; start over
                existingBytes = 0L
                partFile.delete()
            }

            if (totalBytes <= 0) {
                val cl = connection.contentLengthLong
                totalBytes = if (isPartial) existingBytes + cl else cl
            }

            inputStream = connection.inputStream
            outputStream = FileOutputStream(partFile, isPartial)

            val buffer = ByteArray(32 * 1024)
            var bytesDownloaded = existingBytes
            var lastUpdateTime = SystemClock.elapsedRealtime()
            var bytesSinceLastUpdate = 0L
            var currentSpeed = 0L

            while (isActive && !isPauseRequested) {
                val read = inputStream.read(buffer)
                if (read == -1) break

                outputStream.write(buffer, 0, read)
                bytesDownloaded += read
                bytesSinceLastUpdate += read

                val now = SystemClock.elapsedRealtime()
                val elapsed = now - lastUpdateTime
                if (elapsed >= 500) {
                    currentSpeed = (bytesSinceLastUpdate * 1000) / elapsed
                    val remainingBytes = (totalBytes - bytesDownloaded).coerceAtLeast(0)
                    val etaSeconds = if (currentSpeed > 0) remainingBytes / currentSpeed else -1L

                    _downloadState.value = DownloadState.Progress(
                        fileName = fileName,
                        downloadedBytes = bytesDownloaded,
                        totalBytes = totalBytes,
                        speedBytesPerSec = currentSpeed,
                        remainingSeconds = etaSeconds,
                        isPaused = false
                    )

                    lastUpdateTime = now
                    bytesSinceLastUpdate = 0L
                }
            }

            outputStream.flush()

            if (isPauseRequested) {
                _downloadState.value = DownloadState.Progress(
                    fileName = fileName,
                    downloadedBytes = bytesDownloaded,
                    totalBytes = totalBytes,
                    speedBytesPerSec = 0,
                    remainingSeconds = -1,
                    isPaused = true
                )
                return@withContext
            }

            // Download completed successfully
            if (partFile.renameTo(finalFile)) {
                // Register in Room Database
                val db = AppDatabase.getInstance(context)
                val modelDao = db.modelDao()
                val existing = modelDao.getModelByPath(finalFile.absolutePath)
                if (existing == null) {
                    modelDao.insertModel(
                        ModelEntity(
                            name = finalFile.name,
                            filePath = finalFile.absolutePath,
                            sizeBytes = finalFile.length()
                        )
                    )
                }

                _downloadState.value = DownloadState.Completed(finalFile)
            } else {
                _downloadState.value = DownloadState.Error("Failed to rename temporary download file.")
            }

        } catch (c: CancellationException) {
            // Cancelled or paused cleanly
        } catch (e: Exception) {
            _downloadState.value = DownloadState.Error(e.message ?: "Network download failed")
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
                connection?.disconnect()
            } catch (_: Exception) {}
        }
    }

    companion object {
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
    }
}
