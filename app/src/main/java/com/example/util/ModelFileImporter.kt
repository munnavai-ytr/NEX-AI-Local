package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.AppDatabase
import com.example.data.ModelEntity
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportProgress(
    val fileName: String,
    val bytesCopied: Long,
    val totalBytes: Long
) {
    val percent: Int
        get() = if (totalBytes > 0) ((bytesCopied * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

    val formattedProgress: String
        get() {
            val copiedMb = bytesCopied / (1024.0 * 1024.0)
            val totalMb = totalBytes / (1024.0 * 1024.0)
            return "%.1f MB / %.1f MB (%d%%)".format(copiedMb, totalMb, percent)
        }
}

object ModelFileImporter {

    suspend fun importFile(
        context: Context,
        uri: Uri,
        onProgress: (ImportProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            var fileName = "imported_model.litertlm"
            var fileSize = -1L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            if (!fileName.endsWith(".litertlm", ignoreCase = true)) {
                fileName = "$fileName.litertlm"
            }

            val modelsDir = ModelDownloadManager.getModelsDir(context)
            val targetFile = File(modelsDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesCopied = 0L
                    var read: Int

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        onProgress(
                            ImportProgress(
                                fileName = fileName,
                                bytesCopied = bytesCopied,
                                totalBytes = if (fileSize > 0) fileSize else bytesCopied
                            )
                        )
                    }
                    output.flush()
                }
            } ?: return@withContext Result.failure(Exception("Unable to read selected file"))

            // Register in Room Database
            val db = AppDatabase.getInstance(context)
            val modelDao = db.modelDao()
            val existing = modelDao.getModelByPath(targetFile.absolutePath)
            if (existing == null) {
                modelDao.insertModel(
                    ModelEntity(
                        name = targetFile.name,
                        filePath = targetFile.absolutePath,
                        sizeBytes = targetFile.length()
                    )
                )
            } else {
                modelDao.updateModel(
                    existing.copy(sizeBytes = targetFile.length())
                )
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
