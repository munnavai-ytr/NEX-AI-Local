package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ModelEntity
import com.example.data.StarterCatalogModel
import com.example.data.StarterModelCatalog
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
    val wifiOnly by viewModel.wifiOnlyDownload.collectAsStateWithLifecycle()

    // Storage calculation
    val totalModelsSizeBytes = models.sumOf { it.sizeBytes }
    val statFs = remember(models.size, downloadState) { StatFs(context.filesDir.path) }
    val freeStorageBytes = statFs.availableBytes

    // SAF File Picker Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importModelFromUri(uri)
        }
    }

    // Optional notification permission launcher for Android 13+ background download notification
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Foreground download service proceeds regardless of whether notification permission was granted
    }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
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

        // Wi-Fi Only Switch & Background Download Notice
        item {
            WifiOnlySettingCard(
                wifiOnly = wifiOnly,
                onWifiOnlyChanged = { viewModel.setWifiOnlyDownload(it) }
            )
        }

        // Import Progress Bar (if importing via SAF)
        if (importProgress != null) {
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AccentBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.FileUpload,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Copying ${importProgress.fileName} to private models folder...",
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

        // Active / Failed / Completed Download Status Card
        if (downloadState !is DownloadState.Idle) {
            item {
                DownloadStatusCard(
                    downloadState = downloadState,
                    onPause = { viewModel.pauseDownload() },
                    onResume = {
                        requestNotificationPermissionIfNeeded()
                        viewModel.resumeDownload()
                    },
                    onRetry = {
                        requestNotificationPermissionIfNeeded()
                        viewModel.retryDownload()
                    },
                    onCancel = { viewModel.cancelDownload(deletePartial = false) },
                    onDiscardPartial = { viewModel.cancelDownload(deletePartial = true) }
                )
            }
        }

        // SECTION 1: STARTER CATALOG WITH ONE-TAP INSTALL
        item {
            Column {
                Text(
                    text = "Starter Catalog (One-Tap Install)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Tap Install to automatically download and register the model. No Hugging Face token is required for these models, and downloads continue in the background if you close the app.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        items(StarterModelCatalog.MODELS, key = { it.id }) { catalogModel ->
            val installedModel = models.firstOrNull {
                it.filePath.endsWith("/${catalogModel.fileName}", ignoreCase = true) ||
                    it.downloadUrl.equals(catalogModel.downloadUrl, ignoreCase = true) ||
                    it.name.equals(catalogModel.name, ignoreCase = true) ||
                    it.name.equals(catalogModel.fileName, ignoreCase = true)
            }
            val isLoaded = installedModel != null && (
                installedModel.isLoaded ||
                    (engineStatus is EngineStatus.Ready &&
                        (engineStatus.modelName == installedModel.name || engineStatus.modelName == catalogModel.fileName))
                )

            StarterCatalogModelCard(
                catalogModel = catalogModel,
                installedModel = installedModel,
                isCurrentlyLoaded = isLoaded,
                isEngineBusy = engineStatus is EngineStatus.Initializing,
                downloadState = downloadState,
                onInstall = {
                    requestNotificationPermissionIfNeeded()
                    viewModel.installStarterModel(catalogModel)
                },
                onPause = { viewModel.pauseDownload() },
                onResume = {
                    requestNotificationPermissionIfNeeded()
                    viewModel.resumeDownload()
                },
                onRetry = {
                    requestNotificationPermissionIfNeeded()
                    viewModel.retryDownload()
                },
                onCancel = { viewModel.cancelDownload(deletePartial = false) },
                onLoadInstalled = {
                    if (installedModel != null) {
                        viewModel.loadModel(installedModel.filePath)
                    }
                },
                onUnloadInstalled = { viewModel.unloadModel() }
            )
        }

        // SECTION 2: INSTALLED MODELS & SAF FILE IMPORT
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "My Installed Models (${models.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Stored in private app storage (/files/models)",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }

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
                Icon(
                    Icons.Default.FileUpload,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Import .litertlm File (System File Picker)",
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
                        Icon(
                            Icons.Default.Storage,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No models installed yet",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap 'Install' on any model in the Starter Catalog above, import a .litertlm file from your device, or download from a direct URL below.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(models, key = { it.id }) { model ->
                ModelItemCard(
                    model = model,
                    isCurrentlyLoaded = engineStatus is EngineStatus.Ready &&
                        (engineStatus.modelName == model.name || model.filePath.endsWith("/${engineStatus.modelName}")),
                    isEngineBusy = engineStatus is EngineStatus.Initializing,
                    onSelect = { viewModel.selectModel(model.filePath) },
                    onLoad = { viewModel.loadModel(model.filePath) },
                    onUnload = { viewModel.unloadModel() },
                    onDelete = { viewModel.deleteModel(model) }
                )
            }
        }

        // SECTION 3: CUSTOM URL DOWNLOAD & OPTIONAL TOKEN FOR FUTURE GATED MODELS
        item {
            Spacer(modifier = Modifier.height(4.dp))
            UrlDownloaderCard(
                hasHfToken = hasHfToken,
                onSaveToken = { viewModel.saveHfToken(it) },
                onStartDownload = { url, name ->
                    requestNotificationPermissionIfNeeded()
                    viewModel.startDownload(url, name)
                }
            )
        }
    }
}

@Composable
private fun WifiOnlySettingCard(
    wifiOnly: Boolean,
    onWifiOnlyChanged: (Boolean) -> Unit
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, DarkCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.Wifi,
                    contentDescription = null,
                    tint = if (wifiOnly) AccentMint else TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Wi-Fi only downloads",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Prevents large model downloads over mobile data. Downloads continue in the background when the app is closed.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = wifiOnly,
                onCheckedChange = onWifiOnlyChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = AccentMint,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = DarkSurfaceVariant
                ),
                modifier = Modifier.testTag("wifi_only_switch")
            )
        }
    }
}

@Composable
private fun StarterCatalogModelCard(
    catalogModel: StarterCatalogModel,
    installedModel: ModelEntity?,
    isCurrentlyLoaded: Boolean,
    isEngineBusy: Boolean,
    downloadState: DownloadState,
    onInstall: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onLoadInstalled: () -> Unit,
    onUnloadInstalled: () -> Unit
) {
    val context = LocalContext.current
    val fit = RamFitChecker.evaluate(context, catalogModel.approximateSizeBytes)
    val fitColor = when (fit.level) {
        FitLevel.OPTIMAL -> AccentMint
        FitLevel.TIGHT -> AccentAmber
        FitLevel.CRITICAL -> AccentRed
    }

    val isThisModelChecking = downloadState is DownloadState.Checking &&
        (downloadState.fileName.equals(catalogModel.fileName, ignoreCase = true) ||
            downloadState.url.equals(catalogModel.downloadUrl, ignoreCase = true))

    val activeProgress = (downloadState as? DownloadState.Progress)?.takeIf {
        it.fileName.equals(catalogModel.fileName, ignoreCase = true) ||
            it.url.equals(catalogModel.downloadUrl, ignoreCase = true)
    }

    val activeError = (downloadState as? DownloadState.Error)?.takeIf {
        it.fileName.equals(catalogModel.fileName, ignoreCase = true) ||
            it.url.equals(catalogModel.downloadUrl, ignoreCase = true)
    }

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            when {
                installedModel != null -> AccentMint.copy(alpha = 0.6f)
                activeError != null -> AccentRed.copy(alpha = 0.7f)
                activeProgress != null || isThisModelChecking -> AccentBlue
                else -> DarkCardBorder
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("starter_card_${catalogModel.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = catalogModel.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = catalogModel.tier,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentBlue,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = catalogModel.approximateSize,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentMint,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = catalogModel.description,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Min RAM: ${"%.0f".format(catalogModel.minRamGb)} GB • Task: ${catalogModel.task} • License: ${catalogModel.license}",
                fontSize = 11.sp,
                color = TextTertiary,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(8.dp))

            // RAM Fit Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(fitColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "[${fit.badgeLabel}] ${fit.explanation}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            when {
                // Case 1: Already installed in Room
                installedModel != null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = AccentMint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Installed in local storage",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentMint
                            )
                        }

                        if (isCurrentlyLoaded) {
                            OutlinedButton(
                                onClick = onUnloadInstalled,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                                border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Unload", fontSize = 11.sp)
                            }
                        } else {
                            Button(
                                onClick = onLoadInstalled,
                                enabled = !isEngineBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentMint),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Load Model",
                                    color = Color.Black,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Case 2: Checking server / storage for this model
                isThisModelChecking -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = AccentBlue,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = (downloadState as DownloadState.Checking).message,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                // Case 3: Actively downloading or paused for this model
                activeProgress != null -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { activeProgress.percent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (activeProgress.isPaused) AccentAmber else AccentBlue,
                            trackColor = DarkSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val dlMb = activeProgress.downloadedBytes / (1024.0 * 1024.0)
                            val totMb = activeProgress.totalBytes / (1024.0 * 1024.0)
                            val progressLabel = if (activeProgress.totalBytes > 0) {
                                "%.1f / %.1f MB (%d%%)".format(dlMb, totMb, activeProgress.percent)
                            } else {
                                "%.1f MB".format(dlMb)
                            }
                            Text(
                                text = if (activeProgress.isPaused) {
                                    "$progressLabel • Paused"
                                } else {
                                    "$progressLabel • ${activeProgress.formattedSpeed} • ETA ${activeProgress.formattedEta}"
                                },
                                fontSize = 11.sp,
                                color = if (activeProgress.isPaused) AccentAmber else TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (activeProgress.isPaused) {
                                    Button(
                                        onClick = onResume,
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentMint),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text(
                                            "Resume",
                                            color = Color.Black,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = onPause,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, AccentAmber),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Pause", color = AccentAmber, fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = onCancel,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Cancel", color = AccentRed, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Case 4: Download failed for this model — show reason and Retry button that resumes
                activeError != null -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = AccentRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = activeError.message,
                                    fontSize = 11.sp,
                                    color = AccentRed,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("retry_starter_${catalogModel.id}")
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Retry (Resume)",
                                    color = Color.Black,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Case 5: Not installed — show one-tap Install button
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = onInstall,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentMint),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("install_starter_${catalogModel.id}")
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Install",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
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
        border = BorderStroke(1.dp, DarkCardBorder),
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
                    text = "Total Storage Used by Models: $totalDisplay",
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
        border = BorderStroke(1.dp, borderColor),
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
                        border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
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
    onSelect: () -> Unit,
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
        border = BorderStroke(
            1.dp,
            when {
                isCurrentlyLoaded -> AccentMint.copy(alpha = 0.6f)
                model.isLastUsed -> AccentBlue.copy(alpha = 0.6f)
                else -> DarkCardBorder
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = model.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = model.tier,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentBlue,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = model.task.uppercase(),
                                fontSize = 10.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = buildString {
                            append("$sizeDisplay • Min ${"%.1f".format(model.minRamGb)} GB / Rec ${"%.1f".format(model.recommendedRamGb)} GB RAM")
                            if (model.measuredTokensPerSecond > 0.0) {
                                append(" • Measured: ${"%.1f".format(model.measuredTokensPerSecond)} tok/s")
                            }
                        },
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    if (model.licenseNote.isNotBlank()) {
                        Text(
                            text = "License: ${model.licenseNote}${if (model.requiresLicenseAcceptance) " (Requires acceptance)" else ""}",
                            fontSize = 10.sp,
                            color = TextTertiary
                        )
                    }
                }

                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("delete_model_${model.id}")
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Select (set as default/last-used) button
                if (model.isLastUsed) {
                    Text(
                        text = "✓ Selected (Last Used)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentBlue
                    )
                } else {
                    OutlinedButton(
                        onClick = onSelect,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, DarkCardBorder),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("select_model_${model.id}")
                    ) {
                        Text("Select", fontSize = 11.sp, color = TextSecondary)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("unload_model_${model.id}")
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
}

@Composable
private fun DownloadStatusCard(
    downloadState: DownloadState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDiscardPartial: () -> Unit
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (downloadState is DownloadState.Error) AccentRed else AccentBlue
        ),
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

                        val statusLabel = when {
                            downloadState.isCancelledResumable -> "Cancelled (Partial saved — Resumable)"
                            downloadState.isPaused -> "Paused (Resumable)"
                            else -> "${downloadState.formattedSpeed} • ETA ${downloadState.formattedEta}"
                        }

                        Text(
                            text = statusLabel,
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
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("resume_download_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resume", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            OutlinedButton(
                                onClick = onDiscardPartial,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                                border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = AccentRed, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Discard", fontSize = 11.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onPause,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, AccentAmber),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("pause_download_button")
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pause", color = AccentAmber, fontSize = 11.sp)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            OutlinedButton(
                                onClick = onCancel,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                                border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("cancel_download_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = AccentRed, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel", fontSize = 11.sp)
                            }
                        }
                    }
                }
                is DownloadState.Completed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = AccentMint, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Download verified & finished: ${downloadState.file.name}. Registered in Room.",
                            fontSize = 12.sp,
                            color = AccentMint
                        )
                    }
                }
                is DownloadState.Error -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (!downloadState.fileName.isNullOrBlank()) {
                                Text(
                                    text = "Download failed: ${downloadState.fileName}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentRed
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                            Text(
                                text = downloadState.message,
                                fontSize = 12.sp,
                                color = AccentRed,
                                lineHeight = 16.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("retry_download_button")
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Retry",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onDiscardPartial,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Dismiss", fontSize = 11.sp)
                        }
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
        border = BorderStroke(1.dp, DarkCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Download Custom Model from URL",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Paste a direct download URL to any other .litertlm file. Supports HTTP Range resume, free-space check, and end-of-download size verification.",
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
                    Text("Optional file name (e.g. custom-model.litertlm)", color = TextTertiary, fontSize = 12.sp)
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

            // Hugging Face Access Token Toggle (Optional, only for future gated models)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Key,
                        contentDescription = null,
                        tint = if (hasHfToken) AccentMint else TextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasHfToken) {
                            "Hugging Face Token: Stored Encrypted (Optional, for future gated models)"
                        } else {
                            "Hugging Face Token (Optional — only for future gated models)"
                        },
                        fontSize = 11.sp,
                        color = if (hasHfToken) AccentMint else TextSecondary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { showTokenField = !showTokenField },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
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
                        Text("hf_... (stored with AES-256-GCM, never logged)", color = TextTertiary, fontSize = 12.sp)
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
                Text("Start / Resume Download", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
