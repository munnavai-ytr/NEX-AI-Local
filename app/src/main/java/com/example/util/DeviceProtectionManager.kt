package com.example.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class ProtectionAlert {
    abstract val message: String
    abstract val timestamp: Long

    data class ThermalWarning(
        val tempCelsius: Float,
        override val message: String = "Thermal Protection: Battery reached ${"%.1f".format(tempCelsius)}°C (>42°C). Generation paused for device safety.",
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProtectionAlert()

    data class LowMemoryWarning(
        val availMb: Long,
        val reason: String,
        override val message: String = "Memory Protection: $reason. Model unloaded safely (${availMb}MB RAM available).",
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProtectionAlert()
}

class ThermalProtectionException(val tempCelsius: Float) : Exception(
    "Thermal limit reached: ${"%.1f".format(tempCelsius)}°C (>42°C)"
)

class LowMemoryProtectionException(val availMb: Long) : Exception(
    "Critically low RAM: ${availMb}MB remaining"
)

object DeviceProtectionManager {
    private const val TAG = "DeviceProtection"
    const val THERMAL_THRESHOLD_CELSIUS = 42.0f
    const val CRITICAL_RAM_THRESHOLD_BYTES = 150L * 1024 * 1024 // 150 MB

    private val _alerts = MutableSharedFlow<ProtectionAlert>(replay = 0, extraBufferCapacity = 10)
    val alerts: SharedFlow<ProtectionAlert> = _alerts.asSharedFlow()

    private val _lastAlert = MutableStateFlow<ProtectionAlert?>(null)
    val lastAlert: StateFlow<ProtectionAlert?> = _lastAlert.asStateFlow()

    fun postAlert(alert: ProtectionAlert) {
        Log.w(TAG, "Posting protection alert: ${alert.message}")
        _lastAlert.value = alert
        _alerts.tryEmit(alert)
    }

    fun clearAlert() {
        _lastAlert.value = null
    }

    /**
     * Reads battery temperature in degrees Celsius.
     * Android returns tenths of a degree Celsius in EXTRA_TEMPERATURE.
     */
    fun getBatteryTemperature(context: Context): Float {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            if (tempRaw > 0) tempRaw / 10.0f else 32.5f
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read battery temp: ${t.message}")
            32.5f
        }
    }

    data class MemorySnapshot(
        val availMemBytes: Long,
        val totalMemBytes: Long,
        val isLowMemory: Boolean,
        val usedMemBytes: Long
    ) {
        val availMemMb: Long get() = availMemBytes / (1024 * 1024)
        val totalMemMb: Long get() = totalMemBytes / (1024 * 1024)
        val usedMemMb: Long get() = usedMemBytes / (1024 * 1024)
        val usedPercent: Int get() = if (totalMemBytes > 0) ((usedMemBytes * 100) / totalMemBytes).toInt() else 0
    }

    fun getMemorySnapshot(context: Context): MemorySnapshot {
        return try {
            val actMan = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            if (actMan != null) {
                actMan.getMemoryInfo(memInfo)
                MemorySnapshot(
                    availMemBytes = memInfo.availMem,
                    totalMemBytes = memInfo.totalMem,
                    isLowMemory = memInfo.lowMemory,
                    usedMemBytes = (memInfo.totalMem - memInfo.availMem).coerceAtLeast(0)
                )
            } else {
                val runtime = Runtime.getRuntime()
                MemorySnapshot(
                    availMemBytes = runtime.freeMemory(),
                    totalMemBytes = runtime.totalMemory(),
                    isLowMemory = false,
                    usedMemBytes = runtime.totalMemory() - runtime.freeMemory()
                )
            }
        } catch (t: Throwable) {
            MemorySnapshot(
                availMemBytes = 1024L * 1024 * 1024,
                totalMemBytes = 4096L * 1024 * 1024,
                isLowMemory = false,
                usedMemBytes = 1024L * 1024 * 1024
            )
        }
    }

    /**
     * Estimates current process memory consumption in Megabytes (Native Heap + JVM Heap).
     */
    fun getCurrentProcessMemoryMb(): Double {
        return try {
            val nativeBytes = Debug.getNativeHeapAllocatedSize()
            val runtime = Runtime.getRuntime()
            val jvmBytes = runtime.totalMemory() - runtime.freeMemory()
            val totalBytes = nativeBytes + jvmBytes
            (totalBytes.toDouble() / (1024.0 * 1024.0)).coerceAtLeast(1.0)
        } catch (t: Throwable) {
            450.0
        }
    }

    sealed class SafetyStatus {
        data class Safe(val tempCelsius: Float, val memory: MemorySnapshot) : SafetyStatus()
        data class Overheating(val tempCelsius: Float) : SafetyStatus()
        data class LowMemory(val availMemMb: Long) : SafetyStatus()
    }

    fun checkSafety(context: Context): SafetyStatus {
        val temp = getBatteryTemperature(context)
        if (temp >= THERMAL_THRESHOLD_CELSIUS) {
            return SafetyStatus.Overheating(temp)
        }

        val mem = getMemorySnapshot(context)
        if (mem.isLowMemory || mem.availMemBytes < CRITICAL_RAM_THRESHOLD_BYTES) {
            return SafetyStatus.LowMemory(mem.availMemMb)
        }

        return SafetyStatus.Safe(temp, mem)
    }
}
