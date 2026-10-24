package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "benchmark_results")
data class BenchmarkResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val modelName: String,
    val modelPath: String,
    val backend: String, // "CPU" or "GPU"
    val promptType: String, // "Short Prompt" or "Long Prompt"
    val promptText: String,
    val loadTimeMs: Long,
    val timeToFirstTokenMs: Long,
    val tokensPerSecond: Double,
    val totalTokens: Int,
    val durationMs: Long,
    val peakMemoryMb: Double,
    val verdict: String, // "Comfortable", "Slow but usable", "Too slow"
    val batteryTempCelsius: Float,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun computeVerdict(tokensPerSecond: Double): String {
            return when {
                tokensPerSecond >= 15.0 -> "Comfortable"
                tokensPerSecond >= 5.0 -> "Slow but usable"
                else -> "Too slow"
            }
        }
    }
}

@Dao
interface BenchmarkDao {
    @Query("SELECT * FROM benchmark_results ORDER BY timestamp DESC")
    fun getAllResults(): Flow<List<BenchmarkResultEntity>>

    @Query("SELECT * FROM benchmark_results WHERE modelName = :modelName ORDER BY timestamp DESC")
    fun getResultsForModel(modelName: String): Flow<List<BenchmarkResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: BenchmarkResultEntity): Long

    @Query("DELETE FROM benchmark_results WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM benchmark_results")
    suspend fun clearAll()
}
