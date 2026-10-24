package com.example.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MemoryFactEntity
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

private val PRESET_SUGGESTIONS = listOf(
    "I use Kotlin",
    "Answer in Bangla unless I write code",
    "Prefer concise code without explanation",
    "Target Android 12, minSdk 26",
    "Always write unit tests with Robolectric",
    "Explain edge cases and failure modes"
)

@Composable
fun MemoryScreen(
    viewModel: MainViewModel,
    facts: List<MemoryFactEntity>,
    isMemoryInjectionEnabled: Boolean,
    injectedPromptText: String,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var newFactInput by remember { mutableStateOf("") }
    var editingFact by remember { mutableStateOf<MemoryFactEntity?>(null) }
    var editTextValue by remember { mutableStateOf("") }
    var showPreviewDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title & description
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = AccentMint,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Personal Memory & Preferences",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Persistent on-device knowledge injected into assistant prompts",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // Global / Per-Chat Injection Master Switch
        item {
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Inject Memory into Chat",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isMemoryInjectionEnabled)
                                "Active: Enabled preferences are prepended to the system prompt"
                            else
                                "Paused: Chat runs with baseline system prompt only",
                            fontSize = 12.sp,
                            color = if (isMemoryInjectionEnabled) AccentMint else TextSecondary
                        )
                    }
                    Switch(
                        checked = isMemoryInjectionEnabled,
                        onCheckedChange = { viewModel.setMemoryInjectionEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentBlue,
                            uncheckedThumbColor = TextTertiary,
                            uncheckedTrackColor = DarkSurfaceVariant
                        ),
                        modifier = Modifier.testTag("switch_memory_injection")
                    )
                }
            }
        }

        // Injected Prompt Preview Box
        item {
            Surface(
                color = DarkSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Exact Injected Text Preview",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }

                        if (injectedPromptText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(injectedPromptText))
                                    viewModel.showToast("Copied injected memory text")
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy text",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (injectedPromptText.isBlank() || !isMemoryInjectionEnabled) {
                        Text(
                            text = if (!isMemoryInjectionEnabled)
                                "(Memory injection is toggled OFF. Toggle it ON above to inject active facts.)"
                            else
                                "(No active memory items enabled yet. Add preferences below.)",
                            fontSize = 12.sp,
                            color = TextTertiary,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Text(
                            text = injectedPromptText,
                            fontSize = 12.sp,
                            color = AccentMint,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurface, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        )
                    }
                }
            }
        }

        // Add New Fact Input
        item {
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Add Preference or Fact",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = newFactInput,
                        onValueChange = { newFactInput = it },
                        placeholder = {
                            Text(
                                "e.g. 'I use Kotlin' or 'Answer in Bangla unless I write code'",
                                fontSize = 12.sp,
                                color = TextTertiary
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = AccentBlue
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_memory_fact")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Suggestions:",
                            fontSize = 11.sp,
                            color = TextTertiary
                        )

                        Button(
                            onClick = {
                                if (newFactInput.isNotBlank()) {
                                    viewModel.addMemoryFact(newFactInput.trim())
                                    newFactInput = ""
                                }
                            },
                            enabled = newFactInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentBlue,
                                disabledContainerColor = DarkSurfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_save_memory_fact")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Fact", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Suggestion chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PRESET_SUGGESTIONS.forEach { preset ->
                            Surface(
                                color = DarkSurfaceVariant,
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                                modifier = Modifier.clickable { newFactInput = preset }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = AccentAmber,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = preset,
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // List of items
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved Memories (${facts.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                if (facts.isNotEmpty()) {
                    Text(
                        text = "Clear all",
                        fontSize = 12.sp,
                        color = AccentRed,
                        modifier = Modifier
                            .clickable { viewModel.clearAllMemoryFacts() }
                            .padding(4.dp)
                    )
                }
            }
        }

        if (facts.isEmpty()) {
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No personal memory entries yet",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add programming preferences, tech stack facts, or language choices above.",
                            fontSize = 12.sp,
                            color = TextTertiary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(facts, key = { it.id }) { fact ->
                MemoryFactItem(
                    fact = fact,
                    onToggle = { isEnabled -> viewModel.toggleMemoryFact(fact.id, isEnabled) },
                    onEdit = {
                        editingFact = fact
                        editTextValue = fact.fact
                    },
                    onDelete = { viewModel.deleteMemoryFact(fact.id) }
                )
            }
        }
    }

    // Edit Fact Dialog
    if (editingFact != null) {
        AlertDialog(
            onDismissRequest = { editingFact = null },
            title = {
                Text("Edit Memory Fact", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                OutlinedTextField(
                    value = editTextValue,
                    onValueChange = { editTextValue = it },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val current = editingFact
                        if (current != null && editTextValue.isNotBlank()) {
                            viewModel.updateMemoryFact(current.copy(fact = editTextValue.trim()))
                        }
                        editingFact = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { editingFact = null }) {
                    Text("Cancel")
                }
            },
            containerColor = DarkSurface,
            tonalElevation = 6.dp
        )
    }
}

@Composable
private fun MemoryFactItem(
    fact: MemoryFactEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = fact.isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentMint,
                    uncheckedThumbColor = TextTertiary,
                    uncheckedTrackColor = DarkSurfaceVariant
                ),
                modifier = Modifier.size(width = 44.dp, height = 24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fact.fact,
                    fontSize = 13.sp,
                    color = if (fact.isEnabled) TextPrimary else TextTertiary,
                    fontWeight = if (fact.isEnabled) FontWeight.Medium else FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (fact.isEnabled) "Status: Injected" else "Status: Disabled",
                    fontSize = 10.sp,
                    color = if (fact.isEnabled) AccentMint else TextTertiary
                )
            }

            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit fact",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete fact",
                    tint = AccentRed.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
