package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BenchmarkResultEntity
import com.example.data.ModelEntity
import com.example.inference.EngineStatus
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentMint
import com.example.ui.theme.AccentRed
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.util.DeviceProtectionManager
import com.example.util.ProtectionAlert
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BenchmarkScreen(
    viewModel: MainViewModel,
    models: List<ModelEntity>,
    engineStatus: EngineStatus,
    benchmarkState: BenchmarkState,
    history: List<BenchmarkResultEntity>,
    batteryTemp: Float,
    memorySnapshot: DeviceProtectionManager.MemorySnapshot,
    activeProtectionAlert: ProtectionAlert?,
    onNavigateToModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedPromptType by remember { mutableStateOf(BenchmarkPrompts.SHORT_NAME) }
    var selectedModelFilter by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshHardwareStats()
    }

    val activeModel = models.firstOrNull { it.isLoaded }
        ?: models.firstOrNull { it.isLastUsed }
        ?: models.firstOrNull()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("benchmark_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        item {
            BenchmarkHeader(
                onShare = {
                    if (history.isNotEmpty() || benchmarkState is BenchmarkState.Completed) {
                        val latest = if (benchmarkState is BenchmarkState.Completed) benchmarkState.lastResult else history.firstOrNull()
                        val sideCpu = (benchmarkState as? BenchmarkState.Completed)?.sideBySideCpu
                        val sideGpu = (benchmarkState as? BenchmarkState.Completed)?.sideBySideGpu
                        viewModel.shareBenchmarkReport(
                            context = context,
                            modelName = activeModel?.name ?: "LiteRT-LM Model",
                            cpuResult = sideCpu,
                            gpuResult = sideGpu,
                            singleResult = latest
                        )
                    } else {
                        viewModel.showToast("No benchmark data to share yet. Run a test first!")
                    }
                }
            )
        }

        // Device Protection & Hardware Health Banner
        item {
            DeviceProtectionCard(
                batteryTemp = batteryTemp,
                memorySnapshot = memorySnapshot,
                activeAlert = activeProtectionAlert,
                onDismissAlert = { viewModel.dismissProtectionAlert() },
                onRefreshStats = { viewModel.refreshHardwareStats() }
            )
        }

        // Active Model Status Card
        item {
            ActiveModelBanner(
                activeModel = activeModel,
                engineStatus = engineStatus,
                onLoadLastModel = { viewModel.loadLastUsedModel() },
                onGoToModels = onNavigateToModels
            )
        }

        // Benchmark Runner Controls
        item {
            BenchmarkControlsCard(
                selectedPromptType = selectedPromptType,
                onSelectPromptType = { selectedPromptType = it },
                benchmarkState = benchmarkState,
                isModelReady = engineStatus is EngineStatus.Ready || activeModel != null,
                onRunCpu = { viewModel.runBenchmark(promptType = selectedPromptType, useGpu = false) },
                onRunGpu = { viewModel.runBenchmark(promptType = selectedPromptType, useGpu = true) },
                onRunComparison = { viewModel.runComparisonBenchmark(promptType = selectedPromptType) },
                onStop = { viewModel.stopBenchmark() }
            )
        }

        // Benchmark Results Display (Active completed or Side-by-Side)
        if (benchmarkState is BenchmarkState.Completed) {
            item {
                BenchmarkResultsView(
                    completedState = benchmarkState,
                    onShare = {
                        viewModel.shareBenchmarkReport(
                            context = context,
                            modelName = activeModel?.name ?: "LiteRT-LM Model",
                            cpuResult = benchmarkState.sideBySideCpu,
                            gpuResult = benchmarkState.sideBySideGpu,
                            singleResult = benchmarkState.lastResult
                        )
                    }
                )
            }
        }

        // Plain-Language Verdict Scale Legend
        item {
            VerdictScaleLegend()
        }

        // History Section Header & Filters
        item {
            HistoryHeaderSection(
                historyCount = history.size,
                selectedFilter = selectedModelFilter,
                availableModels = history.map { it.modelName }.distinct(),
                onSelectFilter = { selectedModelFilter = it },
                onClearHistory = { viewModel.clearBenchmarkHistory() }
            )
        }

        // History List
        val filteredHistory = if (selectedModelFilter != null) {
            history.filter { it.modelName == selectedModelFilter }
        } else {
            history
        }

        if (filteredHistory.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    color = DarkSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No saved benchmark runs yet. Run a prompt above to record numbers.",
                            color = TextTertiary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            items(filteredHistory, key = { it.id }) { result ->
                HistoryItemCard(
                    result = result,
                    onShare = {
                        viewModel.shareBenchmarkReport(
                            context = context,
                            modelName = result.modelName,
                            singleResult = result
                        )
                    },
                    onDelete = { viewModel.deleteBenchmarkResult(result.id) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BenchmarkHeader(
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Benchmark & Protection",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Measure real on-device throughput & hardware limits",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        IconButton(
            onClick = onShare,
            modifier = Modifier.testTag("benchmark_share_top_button")
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share Report",
                tint = AccentBlue
            )
        }
    }
}

@Composable
private fun DeviceProtectionCard(
    batteryTemp: Float,
    memorySnapshot: DeviceProtectionManager.MemorySnapshot,
    activeAlert: ProtectionAlert?,
    onDismissAlert: () -> Unit,
    onRefreshStats: () -> Unit
) {
    val isOverheating = batteryTemp >= 42.0f
    val isWarm = batteryTemp >= 38.0f && !isOverheating
    val tempColor = when {
        isOverheating -> AccentRed
        isWarm -> AccentAmber
        else -> AccentMint
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("device_protection_card"),
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (activeAlert != null || isOverheating) AccentRed else DarkCardBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = if (isOverheating) AccentRed else AccentMint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Device Protection Guard",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Threshold: 42°C",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onRefreshStats,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Hardware Stats",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Battery Temp
                Surface(
                    modifier = Modifier.weight(1f),
                    color = DarkSurfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Thermostat,
                            contentDescription = null,
                            tint = tempColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Battery Temp",
                                color = TextTertiary,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "${"%.1f".format(batteryTemp)}°C",
                                color = tempColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isOverheating) "Overheating (>42°C)" else if (isWarm) "Warm" else "Normal",
                                color = tempColor,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // RAM Snapshot
                Surface(
                    modifier = Modifier.weight(1f),
                    color = DarkSurfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = if (memorySnapshot.isLowMemory) AccentRed else AccentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Available RAM",
                                color = TextTertiary,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "${"%.1f".format(memorySnapshot.availMemMb / 1024.0)} GB",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "of ${"%.1f".format(memorySnapshot.totalMemMb / 1024.0)} GB total",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Protection Active Policy
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AccentMint)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Active safety: Auto-pauses at >42°C | Auto-unloads on OS memory pressure (onTrimMemory)",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            // Active Warning Alert Banner
            if (activeAlert != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = AccentRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = AccentRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = activeAlert.message,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "Dismiss",
                            color = AccentRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onDismissAlert() }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveModelBanner(
    activeModel: ModelEntity?,
    engineStatus: EngineStatus,
    onLoadLastModel: () -> Unit,
    onGoToModels: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TARGET MODEL",
                    color = TextTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = activeModel?.name ?: "No model selected",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                when (engineStatus) {
                    is EngineStatus.Ready -> {
                        Text(
                            text = "Ready on ${engineStatus.backend} (Load: ${engineStatus.loadTimeMs}ms)",
                            color = AccentMint,
                            fontSize = 12.sp
                        )
                    }
                    is EngineStatus.Initializing -> {
                        Text(
                            text = engineStatus.message,
                            color = AccentAmber,
                            fontSize = 12.sp
                        )
                    }
                    else -> {
                        Text(
                            text = "Status: Unloaded (will initialize before run)",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (engineStatus !is EngineStatus.Ready && activeModel != null) {
                Button(
                    onClick = onLoadLastModel,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Load Model", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else if (activeModel == null) {
                OutlinedButton(
                    onClick = onGoToModels,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Select Model", color = AccentBlue, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun BenchmarkControlsCard(
    selectedPromptType: String,
    onSelectPromptType: (String) -> Unit,
    benchmarkState: BenchmarkState,
    isModelReady: Boolean,
    onRunCpu: () -> Unit,
    onRunGpu: () -> Unit,
    onRunComparison: () -> Unit,
    onStop: () -> Unit
) {
    val isRunning = benchmarkState is BenchmarkState.Running

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "BENCHMARK CONFIGURATION",
                color = TextTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Prompt Type Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedPromptType == BenchmarkPrompts.SHORT_NAME,
                    onClick = { if (!isRunning) onSelectPromptType(BenchmarkPrompts.SHORT_NAME) },
                    label = { Text("Short Prompt (Vowels)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentBlue,
                        selectedLabelColor = Color.Black,
                        containerColor = DarkSurfaceVariant,
                        labelColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("benchmark_chip_short")
                )

                FilterChip(
                    selected = selectedPromptType == BenchmarkPrompts.LONG_NAME,
                    onClick = { if (!isRunning) onSelectPromptType(BenchmarkPrompts.LONG_NAME) },
                    label = { Text("Long Prompt (LRU Cache)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentBlue,
                        selectedLabelColor = Color.Black,
                        containerColor = DarkSurfaceVariant,
                        labelColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("benchmark_chip_long")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Prompt preview
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DarkSurfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Fixed Prompt Content:",
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                    Text(
                        text = if (selectedPromptType == BenchmarkPrompts.SHORT_NAME) {
                            "\"${BenchmarkPrompts.SHORT_TEXT}\""
                        } else {
                            "\"${BenchmarkPrompts.LONG_TEXT}\""
                        },
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 3
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Running State Progress
            if (isRunning) {
                val running = benchmarkState as BenchmarkState.Running
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = running.stage,
                            color = AccentBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        running.progressText?.let {
                            Text(
                                text = it,
                                color = AccentMint,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = AccentBlue,
                        trackColor = DarkSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop Benchmark", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Action Buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Featured Run Comparison Button (CPU vs GPU)
                    Button(
                        onClick = onRunComparison,
                        enabled = isModelReady,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("benchmark_run_comparison_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CompareArrows,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Run Side-by-Side Comparison (CPU vs GPU)",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRunCpu,
                            enabled = isModelReady,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("benchmark_run_cpu_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AccentMint, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run CPU Only", color = TextPrimary, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onRunGpu,
                            enabled = isModelReady,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("benchmark_run_gpu_button")
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run GPU Only", color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BenchmarkResultsView(
    completedState: BenchmarkState.Completed,
    onShare: () -> Unit
) {
    val cpu = completedState.sideBySideCpu
    val gpu = completedState.sideBySideGpu
    val single = completedState.lastResult

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("benchmark_results_card"),
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentMint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (cpu != null && gpu != null) "CPU vs GPU Comparison" else "Latest Benchmark Result",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onShare,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", color = AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (cpu != null && gpu != null) {
                // Side-by-side view
                SideBySideTable(cpu = cpu, gpu = gpu)
            } else {
                // Single result card
                SingleResultMetrics(result = single)
            }
        }
    }
}

@Composable
private fun SideBySideTable(
    cpu: BenchmarkResultEntity,
    gpu: BenchmarkResultEntity
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurfaceVariant, RoundedCornerShape(6.dp))
                .padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("METRIC", color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f))
            Text("CPU", color = AccentMint, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("GPU", color = AccentAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(6.dp))

        MetricComparisonRow(
            label = "Decode Speed",
            cpuValue = "${"%.1f".format(cpu.tokensPerSecond)} tps",
            gpuValue = "${"%.1f".format(gpu.tokensPerSecond)} tps",
            highlight = true
        )

        MetricComparisonRow(
            label = "Load Time",
            cpuValue = "${cpu.loadTimeMs} ms",
            gpuValue = "${gpu.loadTimeMs} ms"
        )

        MetricComparisonRow(
            label = "Time to 1st Token",
            cpuValue = "${cpu.timeToFirstTokenMs} ms",
            gpuValue = "${gpu.timeToFirstTokenMs} ms"
        )

        MetricComparisonRow(
            label = "Peak Memory",
            cpuValue = "${"%.0f".format(cpu.peakMemoryMb)} MB",
            gpuValue = "${"%.0f".format(gpu.peakMemoryMb)} MB"
        )

        MetricComparisonRow(
            label = "Output Tokens",
            cpuValue = "${cpu.totalTokens} tok",
            gpuValue = "${gpu.totalTokens} tok"
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Verdict Badges Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Verdict", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.3f))
            Box(modifier = Modifier.weight(1f)) {
                VerdictBadge(verdict = cpu.verdict)
            }
            Box(modifier = Modifier.weight(1f)) {
                VerdictBadge(verdict = gpu.verdict)
            }
        }

        // Speedup callout if valid
        if (cpu.tokensPerSecond > 0 && gpu.tokensPerSecond > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            val speedup = gpu.tokensPerSecond / cpu.tokensPerSecond
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AccentBlue.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GPU Acceleration: ${"%.2f".format(speedup)}x faster token throughput than CPU on this device.",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricComparisonRow(
    label: String,
    cpuValue: String,
    gpuValue: String,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (highlight) TextPrimary else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1.3f)
        )
        Text(
            text = cpuValue,
            color = if (highlight) AccentMint else TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = gpuValue,
            color = if (highlight) AccentAmber else TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SingleResultMetrics(result: BenchmarkResultEntity) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${result.modelName} [${result.backend}]",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = result.promptType,
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }
            VerdictBadge(verdict = result.verdict)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricPill(
                title = "Speed",
                value = "${"%.1f".format(result.tokensPerSecond)}",
                unit = "tok/s",
                modifier = Modifier.weight(1f),
                color = AccentMint
            )
            MetricPill(
                title = "TTFT",
                value = "${result.timeToFirstTokenMs}",
                unit = "ms",
                modifier = Modifier.weight(1f),
                color = AccentBlue
            )
            MetricPill(
                title = "Load",
                value = "${result.loadTimeMs}",
                unit = "ms",
                modifier = Modifier.weight(1f),
                color = TextPrimary
            )
            MetricPill(
                title = "Peak RAM",
                value = "${"%.0f".format(result.peakMemoryMb)}",
                unit = "MB",
                modifier = Modifier.weight(1f),
                color = AccentAmber
            )
        }
    }
}

@Composable
private fun MetricPill(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary
) {
    Surface(
        modifier = modifier,
        color = DarkSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = TextTertiary, fontSize = 10.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(unit, color = TextSecondary, fontSize = 9.sp)
        }
    }
}

@Composable
fun VerdictBadge(verdict: String) {
    val (bgColor, textColor) = when (verdict) {
        "Comfortable" -> Pair(AccentMint.copy(alpha = 0.2f), AccentMint)
        "Slow but usable" -> Pair(AccentAmber.copy(alpha = 0.2f), AccentAmber)
        else -> Pair(AccentRed.copy(alpha = 0.2f), AccentRed)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, textColor.copy(alpha = 0.4f))
    ) {
        Text(
            text = verdict,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun VerdictScaleLegend() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "PERFORMANCE VERDICT CRITERIA",
                color = TextTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(AccentMint))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Comfortable (≥15 tps): ", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Smooth streaming, responsive interactive coding", color = TextSecondary, fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(AccentAmber))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Slow but usable (5–15 tps): ", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Readable pace, acceptable generation delay", color = TextSecondary, fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(AccentRed))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Too slow (<5 tps): ", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Noticeable waiting, sluggish on complex code", color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun HistoryHeaderSection(
    historyCount: Int,
    selectedFilter: String?,
    availableModels: List<String>,
    onSelectFilter: (String?) -> Unit,
    onClearHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Benchmark History",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = DarkSurfaceVariant,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "$historyCount",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (historyCount > 0) {
                Text(
                    text = "Clear History",
                    color = AccentRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { onClearHistory() }
                        .padding(4.dp)
                )
            }
        }

        // Model Filters
        if (availableModels.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { onSelectFilter(null) },
                    label = { Text("All Models", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentBlue,
                        selectedLabelColor = Color.Black
                    )
                )

                availableModels.forEach { name ->
                    FilterChip(
                        selected = selectedFilter == name,
                        onClick = { onSelectFilter(name) },
                        label = { Text(name, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentBlue,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryItemCard(
    result: BenchmarkResultEntity,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateStr = remember(result.timestamp) {
        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(result.timestamp))
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("history_item_${result.id}"),
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = result.modelName,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = if (result.backend == "GPU") AccentAmber.copy(alpha = 0.2f) else AccentMint.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = result.backend,
                            color = if (result.backend == "GPU") AccentAmber else AccentMint,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = result.promptType,
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                }

                VerdictBadge(verdict = result.verdict)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${"%.1f".format(result.tokensPerSecond)}",
                            color = AccentMint,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = " tok/s",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    Text(
                        text = "TTFT: ${result.timeToFirstTokenMs}ms | Load: ${result.loadTimeMs}ms | RAM: ${"%.0f".format(result.peakMemoryMb)}MB",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Recorded: $dateStr",
                    color = TextTertiary,
                    fontSize = 10.sp
                )
                Text(
                    text = "Temp: ${"%.1f".format(result.batteryTempCelsius)}°C",
                    color = TextTertiary,
                    fontSize = 10.sp
                )
            }
        }
    }
}
