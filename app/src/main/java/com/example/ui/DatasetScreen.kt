package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DatasetExampleEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetScreen(
    viewModel: MainViewModel,
    examples: List<DatasetExampleEntity>,
    totalCount: Int,
    thumbsUpCount: Int,
    thumbsDownCount: Int,
    searchQuery: String,
    onNavigateToChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var editingExample by remember { mutableStateOf<DatasetExampleEntity?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var exampleToDelete by remember { mutableStateOf<DatasetExampleEntity?>(null) }

    // Launcher for exporting JSONL
    val exportJsonlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-jsonlines")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportDatasetToJsonl(context, uri)
        }
    }

    // Launcher for importing JSONL
    val importJsonlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importDatasetFromJsonl(context, uri)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Fine-Tuning Dataset",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Local offline instruction dataset ($totalCount pairs)",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface),
                actions = {
                    IconButton(
                        onClick = {
                            val defaultFileName = "finetuning_dataset_${System.currentTimeMillis()}.jsonl"
                            exportJsonlLauncher.launch(defaultFileName)
                        },
                        modifier = Modifier.testTag("export_jsonl_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Export JSONL",
                            tint = AccentBlue
                        )
                    }
                    IconButton(
                        onClick = {
                            importJsonlLauncher.launch(arrayOf("application/*", "text/*", "*/*"))
                        },
                        modifier = Modifier.testTag("import_jsonl_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Import JSONL",
                            tint = AccentMint
                        )
                    }
                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("add_example_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Example",
                            tint = TextPrimary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Mandatory Privacy Banner
            item {
                DevicePrivacyCard()
            }

            // 2. Metrics summary cards (Total, Thumbs Up, Thumbs Down)
            item {
                DatasetMetricsRow(
                    totalCount = totalCount,
                    thumbsUpCount = thumbsUpCount,
                    thumbsDownCount = thumbsDownCount
                )
            }

            // 3. Action bar for Quick Actions (Export, Import, Seed 10 test examples)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val defaultFileName = "chat_dataset_${System.currentTimeMillis()}.jsonl"
                            exportJsonlLauncher.launch(defaultFileName)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_export_button")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export JSONL", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            importJsonlLauncher.launch(arrayOf("application/*", "text/*", "*/*"))
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_import_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentMint)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import JSONL", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }

            // 4. Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setDatasetSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dataset_search_field"),
                    placeholder = { Text("Search examples by question, answer, prompt...", fontSize = 13.sp, color = TextTertiary) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setDatasetSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true
                )
            }

            // 5. Empty State or List of items
            if (examples.isEmpty()) {
                item {
                    EmptyDatasetView(
                        isSearching = searchQuery.isNotEmpty(),
                        onClearSearch = { viewModel.setDatasetSearchQuery("") },
                        onNavigateToChat = onNavigateToChat,
                        onAddSampleExamples = {
                            // Seed 10 high-quality coding examples to enable immediate acceptance testing
                            seedTenSampleExamples(viewModel)
                        }
                    )
                }
            } else {
                items(examples, key = { it.id }) { item ->
                    DatasetExampleCard(
                        example = item,
                        onEdit = { editingExample = item },
                        onDelete = { exampleToDelete = item },
                        onCopyLine = {
                            val line = buildJsonlLine(item)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("training_jsonl", line))
                            viewModel.showToast("Copied single JSONL line to clipboard")
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Edit Example Dialog
    if (editingExample != null) {
        val ex = editingExample!!
        EditDatasetExampleDialog(
            initialSystem = ex.systemPrompt,
            initialUser = ex.userPrompt,
            initialAssistant = ex.assistantAnswer,
            initialFeedback = ex.feedback,
            onSave = { sys, user, ans, fb ->
                viewModel.updateTrainingExample(
                    ex.copy(
                        systemPrompt = sys,
                        userPrompt = user,
                        assistantAnswer = ans,
                        feedback = fb
                    )
                )
                editingExample = null
            },
            onDismiss = { editingExample = null }
        )
    }

    // Add New Example Dialog
    if (showCreateDialog) {
        EditDatasetExampleDialog(
            dialogTitle = "Add Training Example",
            initialSystem = viewModel.systemPrompt.value,
            initialUser = "",
            initialAssistant = "",
            initialFeedback = 1,
            onSave = { sys, user, ans, fb ->
                viewModel.saveTrainingExample(
                    systemPrompt = sys,
                    userPrompt = user,
                    assistantAnswer = ans,
                    feedback = fb
                )
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    // Delete Confirmation Dialog
    if (exampleToDelete != null) {
        val toDel = exampleToDelete!!
        AlertDialog(
            onDismissRequest = { exampleToDelete = null },
            title = { Text("Delete Training Example?", color = TextPrimary) },
            text = { Text("This will remove the example from your local dataset on device.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTrainingExample(toDel.id)
                        exampleToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { exampleToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun DevicePrivacyCard() {
    Surface(
        color = Color(0xFF0F1A15),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A2B)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("privacy_guarantee_card")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = AccentMint,
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Strict Device-Only Privacy",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentMint
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Nothing leaves your device unless you explicitly export it. All user prompts, system prompts, and ideal answers are saved locally in Room database on this phone.",
                    fontSize = 11.sp,
                    color = Color(0xFFB4D2C3),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun DatasetMetricsRow(
    totalCount: Int,
    thumbsUpCount: Int,
    thumbsDownCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MetricBox(
            title = "TOTAL",
            value = totalCount.toString(),
            color = AccentBlue,
            icon = Icons.Default.DataObject,
            modifier = Modifier.weight(1f)
        )
        MetricBox(
            title = "THUMBS UP",
            value = thumbsUpCount.toString(),
            color = AccentMint,
            icon = Icons.Default.ThumbUp,
            modifier = Modifier.weight(1f)
        )
        MetricBox(
            title = "THUMBS DOWN",
            value = thumbsDownCount.toString(),
            color = Color(0xFFFF7043),
            icon = Icons.Default.ThumbDown,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricBox(
    title: String,
    value: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextTertiary, fontFamily = FontFamily.Monospace)
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@Composable
private fun DatasetExampleCard(
    example: DatasetExampleEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopyLine: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val dateString = remember(example.timestamp) { dateFormat.format(Date(example.timestamp)) }

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dataset_example_card_${example.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Model & Feedback tag + Date + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        example.feedback > 0 -> {
                            Surface(
                                color = Color(0xFF143322),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ThumbUp, contentDescription = null, tint = AccentMint, modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Ideal / SFT", color = AccentMint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        example.feedback < 0 -> {
                            Surface(
                                color = Color(0xFF3B1F17),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ThumbDown, contentDescription = null, tint = Color(0xFFFF7043), modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Negative / Contrast", color = Color(0xFFFF7043), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        else -> {
                            Surface(
                                color = DarkSurfaceVariant,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text("Neutral", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }

                    if (!example.modelName.isNullOrBlank()) {
                        Text(
                            text = example.modelName,
                            fontSize = 11.sp,
                            color = TextTertiary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dateString, fontSize = 10.sp, color = TextTertiary)
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onCopyLine, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy JSONL line", tint = TextSecondary, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = AccentBlue, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // User Prompt section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141923), RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "USER PROMPT",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentBlue,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = example.userPrompt,
                    fontSize = 13.sp,
                    color = TextPrimary,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Ideal Assistant Answer section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F1E19), RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "IDEAL ASSISTANT ANSWER",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentMint,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = example.assistantAnswer,
                    fontSize = 12.sp,
                    color = Color(0xFFD6F0E4),
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Optional System Prompt section (if expanded and present)
            if (expanded && example.systemPrompt.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "SYSTEM PROMPT",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextTertiary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = example.systemPrompt,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Expand / Collapse toggle button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "Show Less ▲" else "View Full Prompt & Answer ▼",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentBlue
                )
            }
        }
    }
}

@Composable
fun EditDatasetExampleDialog(
    dialogTitle: String = "Edit Training Example",
    initialSystem: String,
    initialUser: String,
    initialAssistant: String,
    initialFeedback: Int,
    onSave: (systemPrompt: String, userPrompt: String, assistantAnswer: String, feedback: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var systemPrompt by remember { mutableStateOf(initialSystem) }
    var userPrompt by remember { mutableStateOf(initialUser) }
    var assistantAnswer by remember { mutableStateOf(initialAssistant) }
    var feedback by remember { mutableStateOf(initialFeedback) }
    var showSystemPromptField by remember { mutableStateOf(initialSystem.isNotBlank()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = dialogTitle, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Refine the question and ideal assistant answer. Changes are saved directly to Room.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                // Feedback Toggle Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Rating:", fontSize = 11.sp, color = TextTertiary, fontWeight = FontWeight.Bold)

                    FilterChip(
                        selected = feedback == 1,
                        onClick = { feedback = if (feedback == 1) 0 else 1 },
                        label = { Text("👍 Thumbs Up", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentMint.copy(alpha = 0.2f),
                            selectedLabelColor = AccentMint
                        )
                    )

                    FilterChip(
                        selected = feedback == -1,
                        onClick = { feedback = if (feedback == -1) 0 else -1 },
                        label = { Text("👎 Thumbs Down", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.error
                        )
                    )
                }

                // System Prompt Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSystemPromptField = !showSystemPromptField },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("System Prompt", fontSize = 11.sp, color = AccentBlue, fontWeight = FontWeight.Bold)
                    Text(if (showSystemPromptField) "Hide ▲" else "Edit ▼", fontSize = 10.sp, color = AccentBlue)
                }

                if (showSystemPromptField) {
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 100.dp),
                        label = { Text("System Instruction", fontSize = 11.sp) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant
                        )
                    )
                }

                // User Prompt
                OutlinedTextField(
                    value = userPrompt,
                    onValueChange = { userPrompt = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 110.dp),
                    label = { Text("User Prompt", fontSize = 11.sp) },
                    placeholder = { Text("What question was asked?", fontSize = 12.sp) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant
                    )
                )

                // Ideal Assistant Answer (Editable into perfection!)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Ideal Answer (Target Output)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentMint
                        )
                        Text(
                            text = "Edit into ideal version",
                            fontSize = 10.sp,
                            color = TextTertiary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = assistantAnswer,
                        onValueChange = { assistantAnswer = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 200.dp)
                            .testTag("ideal_answer_input"),
                        placeholder = { Text("Write the ideal response for fine-tuning...", fontSize = 12.sp) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedBorderColor = AccentMint
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (userPrompt.isNotBlank() || assistantAnswer.isNotBlank()) {
                        onSave(systemPrompt, userPrompt, assistantAnswer, feedback)
                    }
                },
                enabled = userPrompt.isNotBlank() && assistantAnswer.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentMint),
                modifier = Modifier.testTag("save_dialog_confirm_button")
            ) {
                Text("Save to Dataset", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = DarkSurface
    )
}

@Composable
private fun EmptyDatasetView(
    isSearching: Boolean,
    onClearSearch: () -> Unit,
    onNavigateToChat: () -> Unit,
    onAddSampleExamples: () -> Unit
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isSearching) Icons.Default.SearchOff else Icons.Default.DataObject,
                contentDescription = null,
                tint = if (isSearching) TextSecondary else AccentBlue,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isSearching) "No matching examples found" else "No Training Examples Yet",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isSearching) {
                    "Try different keywords or clear your search filter."
                } else {
                    "Tap 'Save as training example' under any assistant response in Chat, edit it into the ideal response, or generate 10 seed examples below."
                },
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (isSearching) {
                Button(
                    onClick = onClearSearch,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
                ) {
                    Text("Clear Search")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onNavigateToChat,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Go to Chat", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onAddSampleExamples,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("seed_examples_button")
                    ) {
                        Text("Seed 10 Examples", color = AccentMint, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun buildJsonlLine(example: DatasetExampleEntity): String {
    val msgs = org.json.JSONArray()
    if (example.systemPrompt.isNotBlank()) {
        val s = org.json.JSONObject()
        s.put("role", "system")
        s.put("content", example.systemPrompt)
        msgs.put(s)
    }
    val u = org.json.JSONObject()
    u.put("role", "user")
    u.put("content", example.userPrompt)
    msgs.put(u)

    val a = org.json.JSONObject()
    a.put("role", "assistant")
    a.put("content", example.assistantAnswer)
    msgs.put(a)

    val root = org.json.JSONObject()
    root.put("messages", msgs)
    if (example.feedback != 0) {
        root.put("feedback", if (example.feedback > 0) "thumbs_up" else "thumbs_down")
    }
    if (!example.modelName.isNullOrBlank()) {
        root.put("model", example.modelName)
    }
    return root.toString()
}

/**
 * Seeds 10 realistic coding/software engineering instructions and answers
 * to allow immediate verification of the 10-example acceptance requirement.
 */
private fun seedTenSampleExamples(viewModel: MainViewModel) {
    val samples = listOf(
        Pair(
            "Write a Kotlin extension function to convert any Long timestamp to ISO-8601 string.",
            "fun Long.toIso8601(): String {\n    val instant = java.time.Instant.ofEpochMilli(this)\n    return java.time.format.DateTimeFormatter.ISO_INSTANT.format(instant)\n}"
        ),
        Pair(
            "How do I prevent memory leaks when collecting StateFlow in Jetpack Compose?",
            "Use `collectAsStateWithLifecycle()` from `androidx.lifecycle.compose`. It automatically starts and stops collecting flow emissions based on the Composable Lifecycle State (defaulting to Lifecycle.State.STARTED)."
        ),
        Pair(
            "Write a thread-safe singleton in Kotlin.",
            "In Kotlin, using an `object` declaration is thread-safe and lazily initialized by default:\n\n```kotlin\nobject AppConfig {\n    var isDebug: Boolean = false\n}\n```\n\nFor parameterized singletons, use double-checked locking with `@Volatile`."
        ),
        Pair(
            "What is the difference between launch and async in Kotlin coroutines?",
            "`launch` is fire-and-forget; it returns a `Job` and does not carry a return value.\n`async` starts a coroutine that returns a `Deferred<T>`, allowing you to await its result via `deferred.await()`."
        ),
        Pair(
            "How do you implement debounce in Kotlin coroutines?",
            "```kotlin\nfun <T> Flow<T>.debounce(waitMs: Long): Flow<T> = flow {\n    coroutineScope {\n        var job: Job? = null\n        collect { value ->\n            job?.cancel()\n            job = launch {\n                delay(waitMs)\n                emit(value)\n            }\n        }\n    }\n}\n```"
        ),
        Pair(
            "Explain Room database destructive migration vs manual migration.",
            "`fallbackToDestructiveMigration()` drops and recreates tables, wiping existing records when schemas change without a migration strategy.\nManual `Migration(start, end)` preserves existing user records by running ALTER TABLE statements."
        ),
        Pair(
            "Show how to safely parse JSON strings using org.json without throwing uncaught exceptions.",
            "```kotlin\nfun safeParseJson(raw: String): org.json.JSONObject? {\n    return try {\n        org.json.JSONObject(raw)\n    } catch (e: org.json.JSONException) {\n        null\n    }\n}\n```"
        ),
        Pair(
            "How do you configure an Android Room DAO method to stream data reactively?",
            "Declare the query with a return type of `kotlinx.coroutines.flow.Flow<T>`. Room will automatically trigger new emissions whenever the underlying table undergoes write or delete operations."
        ),
        Pair(
            "What does enableEdgeToEdge() do in Android 15?",
            "`enableEdgeToEdge()` instructs the Android Window Manager to render content behind system navigation bars and status bars. You then apply `WindowInsets` padding inside Scaffold to avoid overlap."
        ),
        Pair(
            "How to create a custom adaptive app launcher icon in Android?",
            "Create `res/mipmap-anydpi-v26/ic_launcher.xml` with `<adaptive-icon>` referencing a background drawable and a foreground vector or bitmap asset, maintaining 108dp canvas with safe zone 72dp."
        )
    )

    for (sample in samples) {
        viewModel.saveTrainingExample(
            systemPrompt = viewModel.systemPrompt.value,
            userPrompt = sample.first,
            assistantAnswer = sample.second,
            feedback = 1
        )
    }
}
