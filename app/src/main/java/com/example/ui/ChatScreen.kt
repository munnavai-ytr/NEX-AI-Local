package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AttachedFile
import com.example.data.ChatMessageEntity
import com.example.data.ModelEntity
import com.example.data.ProjectFolderEntity
import com.example.inference.EngineStatus
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentMint
import com.example.ui.theme.AccentRed
import com.example.ui.theme.AssistantBubbleBg
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.util.ProtectionAlert
import com.example.ui.theme.UserBubbleBg
import com.example.util.FitLevel
import com.example.util.IndexingProgress
import com.example.util.RamFitChecker

data class QuickAction(
    val title: String,
    val icon: ImageVector,
    val templateGenerator: (input: String) -> String
)

private val QUICK_ACTIONS = listOf(
    QuickAction("Explain", Icons.Default.HelpOutline) { input ->
        if (input.isNotBlank()) "Please explain the following code clearly and concisely, highlighting key concepts:\n\n```\n$input\n```"
        else "Please explain this code:\n\n```\n\n```"
    },
    QuickAction("Fix bug", Icons.Default.BugReport) { input ->
        if (input.isNotBlank()) "Analyze this code, identify any bugs, edge cases, or logical flaws, and provide the corrected code:\n\n```\n$input\n```"
        else "Identify and fix any bugs in this code:\n\n```\n\n```"
    },
    QuickAction("Refactor", Icons.Default.AutoAwesome) { input ->
        if (input.isNotBlank()) "Refactor the following code for better readability, performance, and clean architecture principles:\n\n```\n$input\n```"
        else "Refactor this code for readability and performance:\n\n```\n\n```"
    },
    QuickAction("Write tests", Icons.Default.Check) { input ->
        if (input.isNotBlank()) "Write comprehensive unit tests covering happy paths and edge cases for this code:\n\n```\n$input\n```"
        else "Write unit tests for this code:\n\n```\n\n```"
    },
    QuickAction("Add comments", Icons.Default.Comment) { input ->
        if (input.isNotBlank()) "Add clear, professional documentation and comments to the following code:\n\n```\n$input\n```"
        else "Add comments and documentation to this code:\n\n```\n\n```"
    },
    QuickAction("Convert language", Icons.Default.Code) { input ->
        if (input.isNotBlank()) "Convert the following code into idiomatic Kotlin:\n\n```\n$input\n```"
        else "Convert this code to Kotlin:\n\n```\n\n```"
    }
)

@Composable
fun ChatScreen(
    viewModel: MainViewModel,
    messages: List<ChatMessageEntity>,
    engineStatus: EngineStatus,
    lastUsedModel: ModelEntity?,
    systemPrompt: String,
    maxContextTokens: Int,
    streamingText: String?,
    isGenerating: Boolean,
    activeProject: ProjectFolderEntity?,
    indexingProgress: IndexingProgress?,
    attachedFiles: List<AttachedFile>,
    isMemoryInjectionEnabled: Boolean,
    onNavigateToModels: () -> Unit,
    onNavigateToMemory: () -> Unit,
    activeProtectionAlert: ProtectionAlert? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showPromptDialog by remember { mutableStateOf(false) }
    var trainingCandidate by remember { mutableStateOf<Triple<ChatMessageEntity, String, Int>?>(null) }

    // SAF Document tree picker for Project folders
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.selectAndIndexProjectFolder(treeUri)
        }
    }

    // SAF File picker for text/code attachments
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.attachFilesFromUris(uris)
        }
    }

    // Listen for Edit-and-resend prefill events
    LaunchedEffect(Unit) {
        viewModel.inputPrefill.collect { prefilledText ->
            inputText = prefilledText
        }
    }

    // Auto-scroll when messages update or streaming text arrives
    LaunchedEffect(messages.size, streamingText) {
        val totalCount = messages.size + if (streamingText != null) 1 else 0
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    // Calculate estimated total tokens in conversation
    val estimatedTokens = remember(messages, systemPrompt, inputText, attachedFiles) {
        val msgsTokens = messages.sumOf {
            viewModel.estimateTokenCount(it.content) + viewModel.estimateTokenCount(it.thinking ?: "")
        }
        val sysTokens = viewModel.estimateTokenCount(systemPrompt)
        val inputTokens = viewModel.estimateTokenCount(inputText)
        val attachedTokens = attachedFiles.sumOf { it.estimatedTokens }
        msgsTokens + sysTokens + inputTokens + attachedTokens
    }

    val contextUsagePercent = (estimatedTokens.toFloat() / maxContextTokens.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Status header & Model info
        EngineStatusBanner(
            status = engineStatus,
            lastUsedModel = lastUsedModel,
            systemPrompt = systemPrompt,
            onOpenPromptDialog = { showPromptDialog = true },
            onLoadLastModel = { lastUsedModel?.let { viewModel.loadModel(it.filePath) } },
            onNavigateToModels = onNavigateToModels
        )

        if (activeProtectionAlert != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = activeProtectionAlert.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Context Guard Meter Banner
        ContextGuardMeter(
            estimatedTokens = estimatedTokens,
            maxTokens = maxContextTokens,
            percent = contextUsagePercent
        )

        // Project Awareness & Personal Memory Bar
        ProjectAndMemoryBar(
            activeProject = activeProject,
            isMemoryEnabled = isMemoryInjectionEnabled,
            onPickFolder = { folderPickerLauncher.launch(null) },
            onReindex = { viewModel.reindexActiveProject() },
            onNavigateToMemory = onNavigateToMemory
        )

        // Indexing Progress Indicator
        if (indexingProgress != null) {
            IndexingProgressBanner(progress = indexingProgress)
        }

        // Messages list
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty() && streamingText == null) {
                EmptyStateView(
                    engineStatus = engineStatus,
                    lastUsedModel = lastUsedModel,
                    activeProject = activeProject,
                    onPickProject = { folderPickerLauncher.launch(null) },
                    onLoadLastModel = { lastUsedModel?.let { viewModel.loadModel(it.filePath) } },
                    onNavigateToModels = onNavigateToModels,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatMessageItem(
                            message = msg,
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("assistant_answer", msg.content)
                                clipboard.setPrimaryClip(clip)
                            },
                            onRegenerate = { viewModel.regenerateLastAnswer() },
                            onEditAndResend = { viewModel.prefillInputForEdit(msg.content) },
                            onFeedback = { fb -> viewModel.setFeedback(msg.id, fb) },
                            onSaveTrainingExample = {
                                val precedingUserPrompt = messages.takeWhile { it.id != msg.id }
                                    .lastOrNull { it.role == "user" }?.content ?: ""
                                trainingCandidate = Triple(msg, precedingUserPrompt, msg.feedback)
                            }
                        )
                    }

                    if (streamingText != null) {
                        item(key = "streaming_bubble") {
                            StreamingBubble(
                                rawStreamingText = streamingText,
                                backend = if (engineStatus is EngineStatus.Ready) engineStatus.backend else "CPU"
                            )
                        }
                    }
                }
            }
        }

        // Attached Files Row (if user picked text/code files)
        if (attachedFiles.isNotEmpty()) {
            AttachedFilesChipsRow(
                attachedFiles = attachedFiles,
                onRemove = { viewModel.removeAttachedFile(it.uriString) }
            )
        }

        // Quick Action Chips Row
        QuickActionChipsRow(
            onActionSelected = { action ->
                inputText = action.templateGenerator(inputText.trim())
            }
        )

        // Input row with Attachment Button
        ChatInputBar(
            inputText = inputText,
            onInputChanged = { inputText = it },
            isGenerating = isGenerating,
            onAttachFile = { filePickerLauncher.launch(arrayOf("text/*", "application/*", "*/*")) },
            onSend = {
                val toSend = inputText
                inputText = ""
                viewModel.sendMessage(toSend)
            },
            onStop = {
                viewModel.stopGeneration()
            }
        )
    }

    if (showPromptDialog) {
        SystemPromptDialog(
            currentPrompt = systemPrompt,
            onSave = {
                viewModel.setSystemPrompt(it)
                showPromptDialog = false
            },
            onApplyPreset = { preset ->
                viewModel.applySystemPromptPreset(preset)
                showPromptDialog = false
            },
            onDismiss = { showPromptDialog = false }
        )
    }

    if (trainingCandidate != null) {
        val (assistantMsg, userPrompt, initialFeedback) = trainingCandidate!!
        EditDatasetExampleDialog(
            dialogTitle = "Save as Training Example",
            initialSystem = systemPrompt,
            initialUser = userPrompt,
            initialAssistant = assistantMsg.content,
            initialFeedback = if (initialFeedback != 0) initialFeedback else 1,
            onSave = { sys, user, ans, fb ->
                viewModel.saveTrainingExample(
                    systemPrompt = sys,
                    userPrompt = user,
                    assistantAnswer = ans,
                    feedback = fb
                )
                trainingCandidate = null
            },
            onDismiss = { trainingCandidate = null }
        )
    }
}

@Composable
private fun ProjectAndMemoryBar(
    activeProject: ProjectFolderEntity?,
    isMemoryEnabled: Boolean,
    onPickFolder: () -> Unit,
    onReindex: () -> Unit,
    onNavigateToMemory: () -> Unit
) {
    Surface(
        color = Color(0xFF0F131D),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1C2233)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Project folder selector
            if (activeProject != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = AccentMint,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = activeProject.displayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = "${activeProject.fileCount} files, ${activeProject.chunkCount} chunks indexed",
                            fontSize = 9.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onReindex,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Re-index project",
                            tint = AccentBlue,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onPickFolder() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Switch", fontSize = 9.sp, color = TextSecondary)
                    }
                }
            } else {
                Surface(
                    color = DarkSurfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier
                        .clickable { onPickFolder() }
                        .testTag("btn_pick_project_folder")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Index Project Folder",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Memory Indicator Badge
            Surface(
                color = if (isMemoryEnabled) Color(0xFF14241E) else DarkSurfaceVariant,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isMemoryEnabled) AccentMint.copy(alpha = 0.5f) else DarkCardBorder
                ),
                modifier = Modifier
                    .clickable { onNavigateToMemory() }
                    .testTag("badge_memory_status")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = if (isMemoryEnabled) AccentMint else TextTertiary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isMemoryEnabled) "Memory ON" else "Memory OFF",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isMemoryEnabled) AccentMint else TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun IndexingProgressBanner(progress: IndexingProgress) {
    Surface(
        color = Color(0xFF191C28),
        border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = AccentBlue
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (progress.currentFile.isNotBlank())
                            "Indexing: ${progress.currentFile}"
                        else
                            "Finalizing index...",
                        fontSize = 11.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = "${progress.filesProcessed} files | ${progress.chunksCreated} chunks",
                    fontSize = 10.sp,
                    color = AccentMint,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = AccentBlue,
                trackColor = DarkSurfaceVariant
            )
        }
    }
}

@Composable
private fun AttachedFilesChipsRow(
    attachedFiles: List<AttachedFile>,
    onRemove: (AttachedFile) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0C101A))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (file in attachedFiles) {
            Surface(
                color = DarkSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${file.name} (~${file.estimatedTokens} tok)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(
                        onClick = { onRemove(file) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove file",
                            tint = AccentRed,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextGuardMeter(
    estimatedTokens: Int,
    maxTokens: Int,
    percent: Float
) {
    val barColor = when {
        percent < 0.60f -> AccentMint
        percent < 0.85f -> AccentAmber
        else -> AccentRed
    }

    Surface(
        color = Color(0xFF10121A),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1B202D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Context Guard: $estimatedTokens / $maxTokens tokens (${(percent * 100).toInt()}%)",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
                if (percent >= 0.85f) {
                    Text(
                        text = "Near Limit (Oldest messages pruned)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentAmber
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            LinearProgressIndicator(
                progress = { percent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = barColor,
                trackColor = DarkSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickActionChipsRow(
    onActionSelected: (QuickAction) -> Unit
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (action in QUICK_ACTIONS) {
            Surface(
                color = DarkSurfaceVariant,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onActionSelected(action) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = action.title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun EngineStatusBanner(
    status: EngineStatus,
    lastUsedModel: ModelEntity?,
    systemPrompt: String,
    onOpenPromptDialog: () -> Unit,
    onLoadLastModel: () -> Unit,
    onNavigateToModels: () -> Unit
) {
    Surface(
        color = DarkSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Engine state indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (dotColor, statusTitle) = when (status) {
                        is EngineStatus.Ready -> Pair(AccentMint, "Ready [${status.backend}]")
                        is EngineStatus.Initializing -> Pair(AccentAmber, "Initializing...")
                        is EngineStatus.Unloaded -> Pair(AccentRed, "No Model Loaded")
                        is EngineStatus.Error -> Pair(AccentRed, "Engine Error")
                    }

                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(dotColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Right: System Prompt Button
                Surface(
                    color = DarkSurfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier
                        .clickable { onOpenPromptDialog() }
                        .testTag("system_prompt_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (systemPrompt.contains("senior software engineer")) "Pro Dev" else "Prompt",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentBlue
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            when (status) {
                is EngineStatus.Ready -> {
                    Text(
                        text = "${status.modelName} • Loaded in ${status.loadTimeMs}ms",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
                is EngineStatus.Initializing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = AccentAmber
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = status.message,
                            fontSize = 11.sp,
                            color = AccentAmber
                        )
                    }
                }
                is EngineStatus.Unloaded -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (lastUsedModel != null) "No model loaded • Last: ${lastUsedModel.name}" else "No model loaded — Select a model first",
                            fontSize = 11.sp,
                            color = TextTertiary,
                            modifier = Modifier.weight(1f)
                        )
                        if (lastUsedModel != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onLoadLastModel,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentMint),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .testTag("banner_load_last_model_button")
                            ) {
                                Text(
                                    text = "Load last model",
                                    color = Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                is EngineStatus.Error -> {
                    Text(
                        text = status.message,
                        fontSize = 11.sp,
                        color = AccentRed
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateView(
    engineStatus: EngineStatus,
    lastUsedModel: ModelEntity?,
    activeProject: ProjectFolderEntity?,
    onPickProject: () -> Unit,
    onLoadLastModel: () -> Unit,
    onNavigateToModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
        modifier = modifier
            .fillMaxWidth(0.92f)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Code,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(44.dp)
            )

            Text(
                text = "NEX Senior AI Developer",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = "Fully offline, on-device intelligence powered by Google LiteRT-LM. Project-aware with persistent personal memory.",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            if (engineStatus !is EngineStatus.Ready) {
                Spacer(modifier = Modifier.height(6.dp))

                if (lastUsedModel != null) {
                    val fit = RamFitChecker.evaluate(context, lastUsedModel.sizeBytes)
                    val fitColor = when (fit.level) {
                        FitLevel.OPTIMAL -> AccentMint
                        FitLevel.TIGHT -> AccentAmber
                        FitLevel.CRITICAL -> AccentRed
                    }

                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Last Used: ${lastUsedModel.name}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "[${fit.badgeLabel}]",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = fitColor
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = fit.explanation,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = onLoadLastModel,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentMint),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("load_last_model_button")
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Load Last Model (${lastUsedModel.name})",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                OutlinedButton(
                    onClick = onNavigateToModels,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("empty_state_select_model_button")
                ) {
                    Text("Go to Models Screen", color = AccentBlue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            } else {
                // If model is ready, show quick project attach hint if none attached
                if (activeProject == null) {
                    OutlinedButton(
                        onClick = onPickProject,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentMint),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = AccentMint, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Index a Project Folder", color = AccentMint, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessageEntity,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onEditAndResend: () -> Unit,
    onFeedback: (Int) -> Unit = {},
    onSaveTrainingExample: () -> Unit = {}
) {
    val isUser = message.role == "user"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) UserBubbleBg else AssistantBubbleBg,
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 12.dp
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
            modifier = Modifier.fillMaxWidth(if (isUser) 0.88f else 0.98f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "You" else "NEX Senior Assistant",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) AccentBlue else AccentMint,
                        fontFamily = FontFamily.Monospace
                    )

                    // Action buttons
                    if (isUser) {
                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onEditAndResend() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary, modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit & Resend", fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = DarkSurfaceVariant,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onCopy() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextSecondary, modifier = Modifier.size(11.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Copy", fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                                }
                            }

                            Surface(
                                color = DarkSurfaceVariant,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onRegenerate() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Regenerate", tint = AccentBlue, modifier = Modifier.size(11.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Regenerate", fontSize = 10.sp, color = AccentBlue, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Render collapsed thinking if present
                if (!isUser && !message.thinking.isNullOrBlank()) {
                    ThinkingSection(thinkingText = message.thinking)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Render Markdown content
                MarkdownContent(content = message.content)

                // Sources used row if present
                if (!isUser && !message.sources.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    SourcesUsedSection(sourcesString = message.sources)
                }

                // Stats Line
                if (!isUser && message.modelLoadTimeMs != null && message.timeToFirstTokenMs != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    StatsLine(
                        loadTimeMs = message.modelLoadTimeMs,
                        ttftMs = message.timeToFirstTokenMs,
                        tokensPerSec = message.tokensPerSecond ?: 0.0,
                        backend = message.backendUsed ?: "CPU"
                    )
                }

                // Feedback and Training Example Toolbar (Under every assistant answer)
                if (!isUser) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = DarkCardBorder, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Thumbs Up / Thumbs Down Ratings
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Thumbs Up Button
                            Surface(
                                color = if (message.feedback == 1) AccentMint.copy(alpha = 0.2f) else DarkSurfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                border = if (message.feedback == 1) androidx.compose.foundation.BorderStroke(1.dp, AccentMint) else null,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onFeedback(if (message.feedback == 1) 0 else 1) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("thumbs_up_button_${message.id}")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (message.feedback == 1) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                        contentDescription = "Thumbs Up",
                                        tint = if (message.feedback == 1) AccentMint else TextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Good",
                                        fontSize = 11.sp,
                                        fontWeight = if (message.feedback == 1) FontWeight.Bold else FontWeight.Normal,
                                        color = if (message.feedback == 1) AccentMint else TextSecondary
                                    )
                                }
                            }

                            // Thumbs Down Button
                            Surface(
                                color = if (message.feedback == -1) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else DarkSurfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                border = if (message.feedback == -1) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onFeedback(if (message.feedback == -1) 0 else -1) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("thumbs_down_button_${message.id}")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (message.feedback == -1) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                                        contentDescription = "Thumbs Down",
                                        tint = if (message.feedback == -1) MaterialTheme.colorScheme.error else TextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Bad",
                                        fontSize = 11.sp,
                                        fontWeight = if (message.feedback == -1) FontWeight.Bold else FontWeight.Normal,
                                        color = if (message.feedback == -1) MaterialTheme.colorScheme.error else TextSecondary
                                    )
                                }
                            }
                        }

                        // Save as training example Button
                        Surface(
                            color = Color(0xFF13222A),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSaveTrainingExample() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("save_training_example_button_${message.id}")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.BookmarkAdd,
                                    contentDescription = "Save as training example",
                                    tint = AccentBlue,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Save as training example",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentBlue
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcesUsedSection(sourcesString: String) {
    val sourceItems = remember(sourcesString) {
        sourcesString.split(";").map { it.trim() }.filter { it.isNotEmpty() }
    }

    Surface(
        color = Color(0xFF0D121B),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1B2433)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Source,
                    contentDescription = null,
                    tint = AccentMint,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Sources used (${sourceItems.size} project chunks):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentMint,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (src in sourceItems) {
                    val parts = src.split(":")
                    val fileName = parts.getOrNull(0) ?: src
                    val lineRange = if (parts.size > 1) "lines ${parts[1]}" else ""

                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📄 $fileName",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                            if (lineRange.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "($lineRange)",
                                    fontSize = 10.sp,
                                    color = AccentBlue,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingSection(thinkingText: String) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xFF11141E),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2433)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AccentAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Model Reasoning (${thinkingText.length} chars)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentAmber,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = if (expanded) "Collapse ▲" else "Expand ▼",
                    fontSize = 10.sp,
                    color = TextTertiary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = thinkingText,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingBubble(
    rawStreamingText: String,
    backend: String
) {
    val (thinking, content) = MarkdownHelper.parseThinking(rawStreamingText)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            color = AssistantBubbleBg,
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = 2.dp,
                bottomEnd = 12.dp
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
            modifier = Modifier.fillMaxWidth(0.98f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        strokeWidth = 2.dp,
                        color = AccentMint
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Generating...",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentMint,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (thinking != null) {
                    ThinkingSection(thinkingText = thinking)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (content.isNotEmpty()) {
                    MarkdownContent(content = content)
                } else if (thinking == null) {
                    Text(text = "...", fontSize = 14.sp, color = TextTertiary)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Streaming on $backend...",
                    fontSize = 10.sp,
                    color = TextTertiary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun StatsLine(
    loadTimeMs: Long,
    ttftMs: Long,
    tokensPerSec: Double,
    backend: String
) {
    Surface(
        color = Color(0xFF0D0F14),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E222D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Load: ${loadTimeMs}ms | TTFT: ${ttftMs}ms | Speed: ${"%.1f".format(tokensPerSec)} tps [$backend]",
            fontSize = 11.sp,
            color = TextTertiary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ChatInputBar(
    inputText: String,
    onInputChanged: (String) -> Unit,
    isGenerating: Boolean,
    onAttachFile: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        color = DarkSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attach text/code file button
            IconButton(
                onClick = onAttachFile,
                modifier = Modifier
                    .size(40.dp)
                    .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                    .testTag("btn_attach_file")
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Attach code/text file",
                    tint = AccentBlue,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChanged,
                placeholder = {
                    Text(
                        "Ask coding assistant or paste code...",
                        color = TextTertiary,
                        fontSize = 14.sp
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = DarkCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AccentBlue
                ),
                maxLines = 6,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field")
            )

            Spacer(modifier = Modifier.width(8.dp))

            if (isGenerating) {
                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .size(44.dp)
                        .background(AccentRed, RoundedCornerShape(10.dp))
                        .testTag("stop_generation_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop generation",
                        tint = Color.White
                    )
                }
            } else {
                val sendEnabled = inputText.isNotBlank()
                IconButton(
                    onClick = onSend,
                    enabled = sendEnabled,
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (sendEnabled) AccentBlue else DarkSurfaceVariant,
                            RoundedCornerShape(10.dp)
                        )
                        .testTag("send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = if (sendEnabled) Color.Black else TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemPromptDialog(
    currentPrompt: String,
    onSave: (String) -> Unit,
    onApplyPreset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var editedPrompt by remember { mutableStateOf(currentPrompt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text("System Prompt & Persona", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            Column {
                Text(
                    text = "Presets:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onApplyPreset(SystemPromptPresets.PRO_DEVELOPER) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Pro Developer", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { onApplyPreset(SystemPromptPresets.CHAT) },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("General Chat", color = TextPrimary, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Custom Instruction:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = editedPrompt,
                    onValueChange = { editedPrompt = it },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(editedPrompt) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentMint),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
