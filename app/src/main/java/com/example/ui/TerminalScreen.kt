package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardTab
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentMint
import com.example.ui.theme.AccentRed
import com.example.ui.theme.CodeBlockBg
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.util.DownloadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    terminalViewModel: TerminalViewModel,
    modifier: Modifier = Modifier
) {
    val lines by terminalViewModel.lines.collectAsStateWithLifecycle()
    val inputText by terminalViewModel.inputText.collectAsStateWithLifecycle()
    val downloadState by terminalViewModel.downloadState.collectAsStateWithLifecycle()

    var showHelpSheet by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(CodeBlockBg)
            .testTag("terminal_screen")
    ) {
        val isWideScreen = maxWidth >= 600.dp

        if (isWideScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                TerminalConsolePane(
                    lines = lines,
                    inputText = inputText,
                    downloadState = downloadState,
                    isWideScreen = true,
                    onInputChange = { terminalViewModel.updateInputText(it) },
                    onSubmit = { terminalViewModel.submitCommand() },
                    onHistoryUp = { terminalViewModel.navigateHistoryUp() },
                    onHistoryDown = { terminalViewModel.navigateHistoryDown() },
                    onTabAutocomplete = { terminalViewModel.handleTabAutocomplete() },
                    onClear = { terminalViewModel.clearTerminal() },
                    onOpenHelp = { showHelpSheet = !showHelpSheet },
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight()
                )

                Surface(
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxHeight()
                        .testTag("terminal_side_help_panel")
                ) {
                    TerminalHelpPanelContent(
                        onSelectExample = { example ->
                            terminalViewModel.insertExampleCommand(example)
                        }
                    )
                }
            }
        } else {
            TerminalConsolePane(
                lines = lines,
                inputText = inputText,
                downloadState = downloadState,
                isWideScreen = false,
                onInputChange = { terminalViewModel.updateInputText(it) },
                onSubmit = { terminalViewModel.submitCommand() },
                onHistoryUp = { terminalViewModel.navigateHistoryUp() },
                onHistoryDown = { terminalViewModel.navigateHistoryDown() },
                onTabAutocomplete = { terminalViewModel.handleTabAutocomplete() },
                onClear = { terminalViewModel.clearTerminal() },
                onOpenHelp = { showHelpSheet = true },
                modifier = Modifier.fillMaxSize()
            )

            if (showHelpSheet) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
                ModalBottomSheet(
                    onDismissRequest = { showHelpSheet = false },
                    sheetState = sheetState,
                    containerColor = DarkSurface,
                    contentColor = TextPrimary
                ) {
                    TerminalHelpPanelContent(
                        onSelectExample = { example ->
                            terminalViewModel.insertExampleCommand(example)
                            showHelpSheet = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalConsolePane(
    lines: List<TerminalLine>,
    inputText: String,
    downloadState: DownloadState,
    isWideScreen: Boolean,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onHistoryUp: () -> Unit,
    onHistoryDown: () -> Unit,
    onTabAutocomplete: () -> Unit,
    onClear: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .background(CodeBlockBg)
    ) {
        // Header bar with Clear and "?" Help buttons
        Surface(
            color = DarkSurface,
            border = BorderStroke(1.dp, DarkCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = AccentMint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NEX Terminal",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("terminal_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear terminal",
                            tint = TextSecondary
                        )
                    }

                    if (!isWideScreen) {
                        IconButton(
                            onClick = onOpenHelp,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("terminal_help_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = "Open Terminal Help (?)",
                                tint = AccentBlue
                            )
                        }
                    }
                }
            }
        }

        // Scrollable output log
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("terminal_output_list")
        ) {
            items(lines, key = { it.id }) { line ->
                val textColor = when (line.color) {
                    TerminalColor.GREEN -> AccentMint
                    TerminalColor.YELLOW -> AccentAmber
                    TerminalColor.RED -> AccentRed
                    TerminalColor.GREY -> TextSecondary
                }
                Text(
                    text = line.text,
                    color = textColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = if (line.isCommandEcho) FontWeight.Bold else FontWeight.Normal,
                    lineHeight = 17.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Live progress line for long operations (taken from ModelDownloadManager.downloadState)
        LiveDownloadProgressBanner(downloadState = downloadState)

        // Quick action toolbar: History Up, History Down, Tab Autocomplete
        Surface(
            color = DarkSurface,
            border = BorderStroke(1.dp, DarkCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onHistoryUp,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("terminal_history_up_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Previous command",
                                tint = TextSecondary
                            )
                        }

                        IconButton(
                            onClick = onHistoryDown,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("terminal_history_down_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Next command",
                                tint = TextSecondary
                            )
                        }

                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, DarkCardBorder),
                            modifier = Modifier
                                .clickable { onTabAutocomplete() }
                                .padding(horizontal = 4.dp)
                                .testTag("terminal_tab_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardTab,
                                    contentDescription = "Tab autocomplete",
                                    tint = AccentBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "TAB",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentBlue
                                )
                            }
                        }
                    }

                    Text(
                        text = "Tip: tap ? for tappable examples",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TextTertiary,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }

                HorizontalDivider(color = DarkCardBorder, thickness = 1.dp)

                // Input line with "nex> " prompt
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "nex> ",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = AccentMint
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (inputText.isEmpty()) {
                            Text(
                                text = "type a command (e.g. models, search qwen, device)...",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = TextTertiary
                            )
                        }
                        BasicTextField(
                            value = inputText,
                            onValueChange = onInputChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            ),
                            cursorBrush = SolidColor(AccentMint),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("terminal_input_field")
                        )
                    }

                    IconButton(
                        onClick = onSubmit,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("terminal_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Run command",
                            tint = AccentMint
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveDownloadProgressBanner(downloadState: DownloadState) {
    when (downloadState) {
        is DownloadState.Checking -> {
            Surface(
                color = DarkSurfaceVariant,
                border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("terminal_live_progress")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "[DL] ${downloadState.message}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = AccentBlue
                    )
                }
            }
        }
        is DownloadState.Progress -> {
            val statusLabel = when {
                downloadState.isCancelledResumable -> "CANCELLED (RESUMABLE)"
                downloadState.isPaused -> "PAUSED"
                else -> "DOWNLOADING"
            }
            val color = if (downloadState.isPaused) AccentAmber else AccentMint

            Surface(
                color = DarkSurfaceVariant,
                border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("terminal_live_progress")
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = "[$statusLabel] ${downloadState.fileName} — ${downloadState.percent}% | ${downloadState.formattedSpeed} | ETA: ${downloadState.formattedEta}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = color
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (downloadState.percent / 100f).coerceIn(0f, 1f) },
                        color = color,
                        trackColor = DarkSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                }
            }
        }
        else -> {}
    }
}

@Composable
private fun TerminalHelpPanelContent(
    onSelectExample: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Terminal Command Reference",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Tap any example to insert it directly into the nex> prompt.",
            fontSize = 12.sp,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            for (category in TerminalHelpCatalog.CATEGORIES) {
                item(key = "header_$category") {
                    Text(
                        text = category.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlue,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                val entries = TerminalHelpCatalog.ENTRIES.filter { it.category == category }
                items(entries, key = { "${it.category}_${it.command}" }) { entry ->
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, DarkCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectExample(entry.example) }
                            .testTag("help_example_${entry.command}")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = entry.usage,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = entry.summary,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Example (tap): nex> ${entry.example}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentMint
                            )
                        }
                    }
                }
            }
        }
    }
}
