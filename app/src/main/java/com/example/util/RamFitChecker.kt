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
}
