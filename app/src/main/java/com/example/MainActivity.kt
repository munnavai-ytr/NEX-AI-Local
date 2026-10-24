package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.BenchmarkScreen
import com.example.ui.ChatScreen
import com.example.ui.DatasetScreen
import com.example.ui.MainViewModel
import com.example.ui.MemoryScreen
import com.example.ui.ModelsScreen
import com.example.ui.SettingsScreen
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentMint
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

enum class ScreenTab {
    CHAT, DATASET, MEMORY, BENCHMARK, MODELS, SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    var currentTab by remember { mutableStateOf(ScreenTab.CHAT) }
    val snackbarHostState = remember { SnackbarHostState() }

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val lastUsedModel by viewModel.lastUsedModel.collectAsStateWithLifecycle()
    val engineStatus by viewModel.engineStatus.collectAsStateWithLifecycle()
    val useGpu by viewModel.useGpuSetting.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val hasHfToken by viewModel.hasHfToken.collectAsStateWithLifecycle()
    val systemPrompt by viewModel.systemPrompt.collectAsStateWithLifecycle()
    val maxContextTokens by viewModel.maxContextTokens.collectAsStateWithLifecycle()

    // Memory & Project state
    val memoryFacts by viewModel.memoryFacts.collectAsStateWithLifecycle()
    val isMemoryInjectionEnabled by viewModel.isMemoryInjectionEnabled.collectAsStateWithLifecycle()
    val injectedMemoryText by viewModel.injectedMemoryText.collectAsStateWithLifecycle()

    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val indexingProgress by viewModel.indexingProgress.collectAsStateWithLifecycle()
    val attachedFiles by viewModel.attachedFiles.collectAsStateWithLifecycle()

    // Dataset state for fine-tuning
    val datasetExamples by viewModel.datasetExamples.collectAsStateWithLifecycle()
    val totalDatasetCount by viewModel.totalDatasetCount.collectAsStateWithLifecycle()
    val thumbsUpDatasetCount by viewModel.thumbsUpDatasetCount.collectAsStateWithLifecycle()
    val thumbsDownDatasetCount by viewModel.thumbsDownDatasetCount.collectAsStateWithLifecycle()
    val datasetSearchQuery by viewModel.datasetSearchQuery.collectAsStateWithLifecycle()

    // Benchmark & Protection state
    val benchmarkHistory by viewModel.benchmarkHistory.collectAsStateWithLifecycle()
    val benchmarkState by viewModel.benchmarkState.collectAsStateWithLifecycle()
    val batteryTemp by viewModel.batteryTemp.collectAsStateWithLifecycle()
    val memorySnapshot by viewModel.memorySnapshot.collectAsStateWithLifecycle()
    val activeProtectionAlert by viewModel.activeProtectionAlert.collectAsStateWithLifecycle()

    // Handle snackbar messages
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Handle back button: return to Chat if on other screens
    BackHandler(enabled = currentTab != ScreenTab.CHAT) {
        currentTab = ScreenTab.CHAT
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .background(DarkSurface)
                    .testTag("bottom_navigation_bar")
            ) {
                NavigationBarItem(
                    selected = currentTab == ScreenTab.CHAT,
                    onClick = { currentTab = ScreenTab.CHAT },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat") },
                    label = { Text("Chat", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = AccentBlue,
                        indicatorColor = AccentBlue,
                        unselectedIconColor = TextTertiary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_tab_chat")
                )

                NavigationBarItem(
                    selected = currentTab == ScreenTab.DATASET,
                    onClick = { currentTab = ScreenTab.DATASET },
                    icon = { Icon(Icons.Default.DataObject, contentDescription = "Dataset") },
                    label = { Text("Dataset", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = AccentMint,
                        indicatorColor = AccentMint,
                        unselectedIconColor = TextTertiary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_tab_dataset")
                )

                NavigationBarItem(
                    selected = currentTab == ScreenTab.MEMORY,
                    onClick = { currentTab = ScreenTab.MEMORY },
                    icon = { Icon(Icons.Default.Psychology, contentDescription = "Memory") },
                    label = { Text("Memory", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = AccentBlue,
                        indicatorColor = AccentBlue,
                        unselectedIconColor = TextTertiary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_tab_memory")
                )

                NavigationBarItem(
                    selected = currentTab == ScreenTab.BENCHMARK,
                    onClick = { currentTab = ScreenTab.BENCHMARK },
                    icon = { Icon(Icons.Default.Speed, contentDescription = "Benchmark") },
                    label = { Text("Benchmark", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = AccentBlue,
                        indicatorColor = AccentBlue,
                        unselectedIconColor = TextTertiary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_tab_benchmark")
                )

                NavigationBarItem(
                    selected = currentTab == ScreenTab.MODELS,
                    onClick = { currentTab = ScreenTab.MODELS },
                    icon = { Icon(Icons.Default.Memory, contentDescription = "Models") },
                    label = { Text("Models", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = AccentBlue,
                        indicatorColor = AccentBlue,
                        unselectedIconColor = TextTertiary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_tab_models")
                )

                NavigationBarItem(
                    selected = currentTab == ScreenTab.SETTINGS,
                    onClick = { currentTab = ScreenTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = AccentBlue,
                        indicatorColor = AccentBlue,
                        unselectedIconColor = TextTertiary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        }
    ) { innerPadding ->
        when (currentTab) {
            ScreenTab.CHAT -> {
                ChatScreen(
                    viewModel = viewModel,
                    messages = messages,
                    engineStatus = engineStatus,
                    lastUsedModel = lastUsedModel,
                    systemPrompt = systemPrompt,
                    maxContextTokens = maxContextTokens,
                    streamingText = streamingText,
                    isGenerating = isGenerating,
                    activeProject = activeProject,
                    indexingProgress = indexingProgress,
                    attachedFiles = attachedFiles,
                    isMemoryInjectionEnabled = isMemoryInjectionEnabled,
                    activeProtectionAlert = activeProtectionAlert,
                    onNavigateToModels = { currentTab = ScreenTab.MODELS },
                    onNavigateToMemory = { currentTab = ScreenTab.MEMORY },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            ScreenTab.DATASET -> {
                DatasetScreen(
                    viewModel = viewModel,
                    examples = datasetExamples,
                    totalCount = totalDatasetCount,
                    thumbsUpCount = thumbsUpDatasetCount,
                    thumbsDownCount = thumbsDownDatasetCount,
                    searchQuery = datasetSearchQuery,
                    onNavigateToChat = { currentTab = ScreenTab.CHAT },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            ScreenTab.MEMORY -> {
                MemoryScreen(
                    viewModel = viewModel,
                    facts = memoryFacts,
                    isMemoryInjectionEnabled = isMemoryInjectionEnabled,
                    injectedPromptText = injectedMemoryText,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            ScreenTab.BENCHMARK -> {
                BenchmarkScreen(
                    viewModel = viewModel,
                    models = models,
                    engineStatus = engineStatus,
                    benchmarkState = benchmarkState,
                    history = benchmarkHistory,
                    batteryTemp = batteryTemp,
                    memorySnapshot = memorySnapshot,
                    activeProtectionAlert = activeProtectionAlert,
                    onNavigateToModels = { currentTab = ScreenTab.MODELS },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            ScreenTab.MODELS -> {
                ModelsScreen(
                    viewModel = viewModel,
                    models = models,
                    engineStatus = engineStatus,
                    downloadState = downloadState,
                    importProgress = importProgress,
                    hasHfToken = hasHfToken,
                    onNavigateToChat = { currentTab = ScreenTab.CHAT },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            ScreenTab.SETTINGS -> {
                SettingsScreen(
                    viewModel = viewModel,
                    useGpu = useGpu,
                    systemPrompt = systemPrompt,
                    maxContextTokens = maxContextTokens,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
