package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_models")
data class ModelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val filePath: String,
    val sizeBytes: Long,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val isLastUsed: Boolean = false,
    val isLoaded: Boolean = false,
    val downloadUrl: String = "",
    val isCurated: Boolean = false,
    val tier: String = "Small",
    val minRamGb: Double = 2.0,
    val recommendedRamGb: Double = 4.0,
    val task: String = "chat",
    val licenseNote: String = "",
    val requiresLicenseAcceptance: Boolean = false,
    val sourceLink: String = "",
    val measuredTokensPerSecond: Double = 0.0
) {
    companion object {
        fun inferTier(sizeBytes: Long): String {
            val sizeGb = sizeBytes / (1024.0 * 1024.0 * 1024.0)
            return when {
                sizeGb <= 1.2 -> "Tiny"
                sizeGb <= 2.0 -> "Small"
                sizeGb <= 3.5 -> "Medium"
                else -> "Large"
            }
        }

        fun inferMinRamGb(sizeBytes: Long): Double {
            val sizeGb = sizeBytes / (1024.0 * 1024.0 * 1024.0)
            return when {
                sizeGb <= 1.2 -> 2.0
                sizeGb <= 2.0 -> 3.0
                sizeGb <= 3.5 -> 4.0
                else -> 6.0
            }
        }

        fun inferRecommendedRamGb(sizeBytes: Long): Double {
            val sizeGb = sizeBytes / (1024.0 * 1024.0 * 1024.0)
            return when {
                sizeGb <= 1.2 -> 3.0
                sizeGb <= 2.0 -> 4.0
                sizeGb <= 3.5 -> 6.0
                else -> 8.0
            }
        }
    }
}
