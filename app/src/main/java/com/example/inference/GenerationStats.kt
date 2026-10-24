package com.example.inference

data class GenerationStats(
    val loadTimeMs: Long,
    val timeToFirstTokenMs: Long,
    val tokensPerSecond: Double,
    val totalTokens: Int,
    val durationMs: Long,
    val backend: String,
    val peakMemoryMb: Double = 0.0
) {
    fun toDisplayString(): String {
        return buildString {
            append("Load: ${loadTimeMs}ms")
            append(" | TTFT: ${timeToFirstTokenMs}ms")
            append(" | Speed: ${"%.1f".format(tokensPerSecond)} tps")
            if (peakMemoryMb > 0.0) {
                append(" | RAM: ${"%.0f".format(peakMemoryMb)}MB")
            }
            append(" [$backend]")
        }
    }
}
