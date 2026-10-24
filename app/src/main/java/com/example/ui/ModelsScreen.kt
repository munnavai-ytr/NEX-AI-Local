package com.example.ui

import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.util.DownloadState
import com.example.util.FitLevel
import com.example.util.ImportProgress
import com.example.util.RamFitChecker

data class CuratedModel(
    val name: String,
    val approximateSize: String,
    val description: String
)

private val CURATED_MODELS = listOf(
    CuratedModel(
        name = "Gemma 3 1B",
        approximateSize = "~1.2 GB",
        description = "Lightweight mobile reasoning model developed by Google DeepMind with high token throughput."
    ),
    CuratedModel(
        name = "Qwen 2.5 1.5B",
        approximateSize = "~1.6 GB",
        description = "Advanced instruction-tuned coding and conversational reasoning edge model."
    ),
    CuratedModel(
        name = "Gemma 4 E2B",
        approximateSize = "~2.1 GB",
        description = "High-efficiency edge architecture balancing developer assistance and low memory footprint."
    )
)

@Composable
fun ModelsScreen(
    viewModel: MainViewModel,
    models: List<ModelEntity>,
    engineStatus: EngineStatus,
    downloadState: DownloadState,
    importProgress: ImportProgress?,
    hasHfToken: Boolean,
    onNavigateToChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedSectionTab by remember { mutableIntStateOf(0) } // 0: Local Models, 1: Download & Curated

    // Storage calculation
    val totalModelsSizeBytes = models.sumOf { it.sizeBytes }
    val statFs = remember { StatFs(context.filesDir.path) }
    val freeStorageBytes = statFs.availableBytes

    // SAF File Picker Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importModelFromUri(uri)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title & Header
        item {
            Text(
                text = "Model Management",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Google LiteRT-LM runtime (.litertlm files)",
                fontSize = 12.sp,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace
            )
        }

        // Storage Usage Banner
        item {
            StorageOverviewCard(
                totalModelsSizeBytes = totalModelsSizeBytes,
                freeStorageBytes = freeStorageBytes
            )
        }

        // Active Loaded Model Card
        item {
            ActiveLoadedModelCard(
                engineStatus = engineStatus,
                onUnload = { viewModel.unloadModel() },
                onNavigateToChat = onNavigateToChat
            )
        }

        // Import Progress Bar (if importing via SAF)
        if (importProgress != null) {
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Importing ${importProgress.fileName}...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { importProgress.percent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = AccentBlue,
                            trackColor = DarkSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = importProgress.formattedProgress,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Download Progress Bar / Active Card (if downloading)
        if (downloadState !is DownloadState.Idle) {
            item {
                DownloadStatusCard(
                    downloadState = downloadState,
                    onPause = { viewModel.pauseDownload() },
                    onResume = { viewModel.resumeDownload() },
                    onCancel = { viewModel.cancelDownload() }
                )
            }
        }

        // Navigation Tabs for Subsections
        item {
            TabRow(
                selectedTabIndex = selectedSectionTab,
                containerColor = DarkSurface,
                contentColor = AccentBlue,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedSectionTab]),
                        color = AccentBlue
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(8.dp))
            ) {
                Tab(
                    selected = selectedSectionTab == 0,
                    onClick = { selectedSectionTab = 0 },
                    text = {
                        Text(
                            "My Models (${models.size})",
                            fontSize = 13.sp,
                            fontWeight = if (selectedSectionTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedSectionTab == 0) AccentBlue else TextSecondary
                        )
                    }
                )
                Tab(
                    selected = selectedSectionTab == 1,
                    onClick = { selectedSectionTab = 1 },
                    text = {
                        Text(
                            "Download & Curated",
                            fontSize = 13.sp,
                            fontWeight = if (selectedSectionTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedSectionTab == 1) AccentBlue else TextSecondary
                        )
                    }
                )
            }
        }

        if (selectedSectionTab == 0) {
            // SECTION: Local Models & SAF Import
            item {
                Button(
                    onClick = {
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("import_file_button")
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Import .litertlm File (Storage Access Framework)",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            if (models.isEmpty()) {
                item {
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No models in app storage yet",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap 'Import .litertlm File' to select a local model, or switch to the 'Download & Curated' tab to download one via direct URL.",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(models, key = { it.id }) { model ->
                    ModelItemCard(
                        model = model,
                        isCurrentlyLoaded = engineStatus is EngineStatus.Ready && (engineStatus as EngineStatus.Ready).modelName == model.name,
                        isEngineBusy = engineStatus is EngineStatus.Initializing,
                        onLoad = { viewModel.loadModel(model.filePath) },
                        onUnload = { viewModel.unloadModel() },
                        onDelete = { viewModel.deleteModel(model) }
                    )
                }
            }
        } else {
            // SECTION: Direct URL Downloader & Curated Models
            item {
                UrlDownloaderCard(
                    hasHfToken = hasHfToken,
                    onSaveToken = { viewModel.saveHfToken(it) },
                    onStartDownload = { url, name -> viewModel.startDownload(url, name) }
                )
            }

            item {
                Text(
                    text = "Recommended Curated Models",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Fill in your direct .litertlm download link for each model.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            item {
                CuratedCommunityNoteCard()
            }

            items(CURATED_MODELS) { curated ->
                CuratedModelCard(
                    curated = curated,
                    onStartDownload = { url -> viewModel.startDownload(url, "${curated.name.lowercase().replace(" ", "_")}.litertlm") }
                )
            }
        }
    }
}

@Composable
private fun StorageOverviewCard(
    totalModelsSizeBytes: Long,
    freeStorageBytes: Long
) {
    val totalMb = totalModelsSizeBytes / (1024 * 1024.0)
    val freeGb = freeStorageBytes / (1024 * 1024 * 1024.0)
    val totalDisplay = if (totalMb >= 1024) "%.2f GB".format(totalMb / 1024.0) else "%.1f MB".format(totalMb)

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Storage, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Total Models Storage: $totalDisplay",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "Free Device Storage: %.1f GB available".format(freeGb),
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun ActiveLoadedModelCard(
    engineStatus: EngineStatus,
    onUnload: () -> Unit,
    onNavigateToChat: () -> Unit
) {
    val borderColor = when (engineStatus) {
        is EngineStatus.Ready -> AccentMint.copy(alpha = 0.6f)
        is EngineStatus.Initializing -> AccentAmber.copy(alpha = 0.6f)
        is EngineStatus.Error -> AccentRed.copy(alpha = 0.6f)
        is EngineStatus.Unloaded -> DarkCardBorder
    }

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val (color, title) = when (engineStatus) {
                    is EngineStatus.Ready -> Pair(AccentMint, "Model Loaded & Active")
                    is EngineStatus.Initializing -> Pair(AccentAmber, "Initializing Engine...")
                    is EngineStatus.Error -> Pair(AccentRed, "Engine Initialization Error")
                    is EngineStatus.Unloaded -> Pair(TextTertiary, "No Model Loaded")
                }

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = color
                )

                Spacer(modifier = Modifier.weight(1f))

                if (engineStatus is EngineStatus.Ready) {
                    OutlinedButton(
                        onClick = onUnload,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("unload_active_model_button")
                    ) {
                        Text("Unload", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (engineStatus) {
                is EngineStatus.Ready -> {
                    Text(
                        text = "Active: ${engineStatus.modelName}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Backend: ${engineStatus.backend} | Load Time: ${engineStatus.loadTimeMs}ms",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    if (engineStatus.fallbackNotice != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = engineStatus.fallbackNotice,
                            fontSize = 11.sp,
                            color = AccentAmber,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onNavigateToChat,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentMint),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    ) {
                        Text("Open Chat", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                is EngineStatus.Initializing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = AccentAmber,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = engineStatus.message,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
                is EngineStatus.Error -> {
                    Text(
                        text = engineStatus.message,
                        fontSize = 12.sp,
                        color = AccentRed,
                        lineHeight = 16.sp
                    )
                }
                is EngineStatus.Unloaded -> {
                    Text(
                        text = "Offline inference engine is unloaded. Zero RAM consumed.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelItemCard(
    model: ModelEntity,
    isCurrentlyLoaded: Boolean,
    isEngineBusy: Boolean,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val fit = RamFitChecker.evaluate(context, model.sizeBytes)
    val fitColor = when (fit.level) {
        FitLevel.OPTIMAL -> AccentMint
        FitLevel.TIGHT -> AccentAmber
        FitLevel.CRITICAL -> AccentRed
    }

    val sizeMb = model.sizeBytes / (1024 * 1024.0)
    val sizeDisplay = if (sizeMb >= 1024) "%.2f GB".format(sizeMb / 1024.0) else "%.1f MB".format(sizeMb)

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCurrentlyLoaded) AccentMint.copy(alpha = 0.6f) else DarkCardBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$sizeDisplay • ${model.filePath}",
                        fontSize = 11.sp,
                        color = TextTertiary,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }

                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AccentRed, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Fit Indicator Badge & Plain Explanation
            Surface(
                color = DarkSurfaceVariant,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(fitColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "[${fit.badgeLabel}]",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = fitColor
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = fit.explanation,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCurrentlyLoaded) {
                    Text(
                        text = "Active in RAM",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentMint,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    OutlinedButton(
                        onClick = onUnload,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Unload", fontSize = 11.sp)
                    }
                } else {
                    Button(
                        onClick = onLoad,
                        enabled = !isEngineBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentMint),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("load_model_${model.id}")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Load Model", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadStatusCard(
    downloadState: DownloadState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            when (downloadState) {
                is DownloadState.Checking -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = AccentBlue, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(downloadState.message, fontSize = 12.sp, color = TextSecondary)
                    }
                }
                is DownloadState.Progress -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = downloadState.fileName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${downloadState.percent}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentBlue
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { downloadState.percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (downloadState.isPaused) AccentAmber else AccentBlue,
                        trackColor = DarkSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val downloadedMb = downloadState.downloadedBytes / (1024 * 1024.0)
                        val totalMb = downloadState.totalBytes / (1024 * 1024.0)
                        val sizeText = if (downloadState.totalBytes > 0) {
                            "%.1f / %.1f MB".format(downloadedMb, totalMb)
                        } else {
                            "%.1f MB".format(downloadedMb)
                        }

                        Text(
                            text = sizeText,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )

                        Text(
                            text = if (downloadState.isPaused) "Paused" else "${downloadState.formattedSpeed} • ETA ${downloadState.formattedEta}",
                            fontSize = 11.sp,
                            color = if (downloadState.isPaused) AccentAmber else TextTertiary,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (downloadState.isPaused) {
                            Button(
                                onClick = onResume,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentMint),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resume", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onPause,
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pause", color = AccentAmber, fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = onCancel,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = AccentRed, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel", fontSize = 11.sp)
                        }
                    }
                }
                is DownloadState.Completed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = AccentMint, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Download finished: ${downloadState.file.name}. Registered in local models.",
                            fontSize = 12.sp,
                            color = AccentMint
                        )
                    }
                }
                is DownloadState.Error -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = downloadState.message,
                            fontSize = 12.sp,
                            color = AccentRed,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Dismiss", fontSize = 11.sp)
                    }
                }
                DownloadState.Idle -> {}
            }
        }
    }
}

@Composable
private fun UrlDownloaderCard(
    hasHfToken: Boolean,
    onSaveToken: (String) -> Unit,
    onStartDownload: (url: String, fileName: String?) -> Unit
) {
    var urlInput by remember { mutableStateOf("") }
    var fileNameInput by remember { mutableStateOf("") }
    var tokenInput by remember { mutableStateOf("") }
    var showTokenField by remember { mutableStateOf(false) }

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Download Model from URL",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Paste a direct download URL to a .litertlm file. Resumable via HTTP Range headers.",
                fontSize = 12.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                placeholder = {
                    Text("https://.../model.litertlm", color = TextTertiary, fontSize = 12.sp)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = DarkCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("download_url_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = fileNameInput,
                onValueChange = { fileNameInput = it },
                placeholder = {
                    Text("Optional file name (e.g. gemma-3-1b.litertlm)", color = TextTertiary, fontSize = 12.sp)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = DarkCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Hugging Face Access Token Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = if (hasHfToken) AccentMint else TextTertiary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasHfToken) "Hugging Face Token: Saved Encrypted" else "Hugging Face Token: Optional",
                        fontSize = 11.sp,
                        color = if (hasHfToken) AccentMint else TextSecondary
                    )
                }

                Button(
                    onClick = { showTokenField = !showTokenField },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text(if (showTokenField) "Hide" else "Set Token", fontSize = 11.sp, color = TextPrimary)
                }
            }

            if (showTokenField) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    placeholder = {
                        Text("hf_...", color = TextTertiary, fontSize = 12.sp)
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onSaveToken(tokenInput)
                            tokenInput = ""
                            showTokenField = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Save Token Encrypted", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (hasHfToken) {
                        OutlinedButton(
                            onClick = {
                                onSaveToken("")
                                tokenInput = ""
                            },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Clear Token", fontSize = 11.sp, color = AccentRed)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (urlInput.isNotBlank()) {
                        onStartDownload(urlInput.trim(), fileNameInput.trim().ifBlank { null })
                        urlInput = ""
                        fileNameInput = ""
                    }
                },
                enabled = urlInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("start_download_button")
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start Resumable Download", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun CuratedCommunityNoteCard() {
    Surface(
        color = DarkSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Where to find LiteRT-LM models:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AccentBlue
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Visit the LiteRT Community on Hugging Face (search 'litert-lm' or explore google-ai-edge repositories) to find official quantized .litertlm model files. Copy the 'download' or 'resolve' URL and paste it below.",
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun CuratedModelCard(
    curated: CuratedModel,
    onStartDownload: (url: String) -> Unit
) {
    var userUrl by remember { mutableStateOf("") }

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = curated.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = curated.approximateSize,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentMint,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = curated.description,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = userUrl,
                onValueChange = { userUrl = it },
                placeholder = {
                    Text("Paste direct .litertlm URL for ${curated.name}...", color = TextTertiary, fontSize = 11.sp)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = DarkCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (userUrl.isNotBlank()) {
                        onStartDownload(userUrl.trim())
                        userUrl = ""
                    }
                },
                enabled = userUrl.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentMint),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.End)
                    .height(34.dp)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Download this model", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
