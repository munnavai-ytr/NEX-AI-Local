package com.example.util

import android.app.ActivityManager
import android.content.Context

enum class FitLevel {
    OPTIMAL,  // Green
    TIGHT,    // Yellow
    CRITICAL  // Red
}

data class RamFitStatus(
    val level: FitLevel,
    val badgeLabel: String,
    val explanation: String,
    val modelSizeMb: Long,
    val availableRamMb: Long,
    val totalRamMb: Long
)

object RamFitChecker {
    fun evaluate(context: Context, fileSizeBytes: Long): RamFitStatus {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val availBytes = if (memInfo.availMem > 0L) {
            memInfo.availMem
        } else {
            // Fallback for test or headless environments (target device has 6GB total, ~2.5GB free)
            2560L * 1024 * 1024
        }
        val totalBytes = if (memInfo.totalMem > 0L) {
            memInfo.totalMem
        } else {
            6144L * 1024 * 1024
        }

        val modelMb = fileSizeBytes / (1024 * 1024)
        val availMb = availBytes / (1024 * 1024)
        val totalMb = totalBytes / (1024 * 1024)

        // Calculate load footprint ratio against available RAM
        val ratio = fileSizeBytes.toDouble() / availBytes.coerceAtLeast(1)

        return when {
            ratio <= 0.50 -> RamFitStatus(
                level = FitLevel.OPTIMAL,
                badgeLabel = "Optimal Fit",
                explanation = "Model size (${modelMb} MB) comfortably fits in available RAM (${availMb} MB / ${totalMb} MB total).",
                modelSizeMb = modelMb,
                availableRamMb = availMb,
                totalRamMb = totalMb
            )
            ratio <= 0.80 -> RamFitStatus(
                level = FitLevel.TIGHT,
                badgeLabel = "Tight Fit",
                explanation = "Model (${modelMb} MB) takes over half of free RAM (${availMb} MB). Close background apps for best performance.",
                modelSizeMb = modelMb,
                availableRamMb = availMb,
                totalRamMb = totalMb
            )
            else -> RamFitStatus(
                level = FitLevel.CRITICAL,
                badgeLabel = "High OOM Risk",
                explanation = "Model (${modelMb} MB) closely approaches or exceeds available RAM (${availMb} MB). Android may terminate the app under memory pressure.",
                modelSizeMb = modelMb,
                availableRamMb = availMb,
                totalRamMb = totalMb
            )
        }
    }

    data class InstallFitVerdict(
        val level: FitLevel,
        val badge: String,
        val ratioOfTotalRam: Double,
        val modelSizeBytes: Long,
        val totalRamBytes: Long,
        val freeStorageBytes: Long,
        val hasEnoughStorage: Boolean,
        val requiredStorageBytes: Long,
        val summary: String,
        val refusalReason: String?
    )

    const val STORAGE_SAFETY_BUFFER_BYTES = 500L * 1024L * 1024L // 500 MB

    /**
     * Pure function to evaluate model installation safety based on TOTAL device RAM and free storage.
     * - GREEN (OPTIMAL): model size <= 40% of total RAM
     * - YELLOW (TIGHT): model size <= 60% of total RAM
     * - RED (CRITICAL): model size > 60% of total RAM
     * Also checks that freeStorageBytes >= modelSizeBytes + 500 MB.
     */
    fun evaluateInstallFit(
        modelSizeBytes: Long,
        totalRamBytes: Long,
        freeStorageBytes: Long
    ): InstallFitVerdict {
        val safeTotalRam = totalRamBytes.coerceAtLeast(1L)
        val ratio = modelSizeBytes.toDouble() / safeTotalRam.toDouble()
        val requiredStorage = modelSizeBytes + STORAGE_SAFETY_BUFFER_BYTES
        val hasEnoughStorage = freeStorageBytes >= requiredStorage

        val modelGb = modelSizeBytes / (1024.0 * 1024.0 * 1024.0)
        val totalRamGb = safeTotalRam / (1024.0 * 1024.0 * 1024.0)
        val neededRamGb = (modelSizeBytes / 0.60) / (1024.0 * 1024.0 * 1024.0)

        val level = when {
            ratio <= 0.40 -> FitLevel.OPTIMAL
            ratio <= 0.60 -> FitLevel.TIGHT
            else -> FitLevel.CRITICAL
        }

        val badge = when (level) {
            FitLevel.OPTIMAL -> "GREEN"
            FitLevel.TIGHT -> "YELLOW"
            FitLevel.CRITICAL -> "RED"
        }

        val percentInt = (ratio * 100.0).toInt()
        val summary = when (level) {
            FitLevel.OPTIMAL -> "GREEN (${percentInt}% of ${"%.1f".format( java.util.Locale.US, totalRamGb )} GB total RAM - safe fit)"
            FitLevel.TIGHT -> "YELLOW (${percentInt}% of ${"%.1f".format( java.util.Locale.US, totalRamGb )} GB total RAM - tight fit, close other apps)"
            FitLevel.CRITICAL -> "RED (${percentInt}% of ${"%.1f".format( java.util.Locale.US, totalRamGb )} GB total RAM - too large)"
        }

        val refusalReason = when {
            !hasEnoughStorage -> {
                val reqGb = requiredStorage / (1024.0 * 1024.0 * 1024.0)
                val freeGb = freeStorageBytes / (1024.0 * 1024.0 * 1024.0)
                "Not enough free storage: needs ${"%.2f".format(java.util.Locale.US, reqGb)} GB (model + 500 MB buffer), you have ${"%.2f".format(java.util.Locale.US, freeGb)} GB free."
            }
            level == FitLevel.CRITICAL -> {
                "This model is too big for your phone (needs ${"%.1f".format(java.util.Locale.US, neededRamGb.coerceAtLeast(modelGb))} GB, you have ${"%.1f".format(java.util.Locale.US, totalRamGb)} GB total)"
            }
            else -> null
        }

        return InstallFitVerdict(
            level = level,
            badge = badge,
            ratioOfTotalRam = ratio,
            modelSizeBytes = modelSizeBytes,
            totalRamBytes = safeTotalRam,
            freeStorageBytes = freeStorageBytes,
            hasEnoughStorage = hasEnoughStorage,
            requiredStorageBytes = requiredStorage,
            summary = summary,
            refusalReason = refusalReason
        )
    }

    fun evaluateInstallFitForDevice(context: Context, modelSizeBytes: Long): InstallFitVerdict {
        val memSnapshot = DeviceProtectionManager.getMemorySnapshot(context)
        val totalRam = if (memSnapshot.totalMemBytes > 0L) memSnapshot.totalMemBytes else 6144L * 1024 * 1024
        val modelsDir = ModelDownloadManager.getModelsDir(context)
        val freeStorage = try {
            android.os.StatFs(modelsDir.path).availableBytes
        } catch (_: Throwable) {
            10L * 1024 * 1024 * 1024
        }
        return evaluateInstallFit(
            modelSizeBytes = modelSizeBytes,
            totalRamBytes = totalRam,
            freeStorageBytes = freeStorage
        )
    }
}
