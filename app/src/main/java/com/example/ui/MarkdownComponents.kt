package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentMint
import com.example.ui.theme.CodeBlockBg
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface MarkdownBlock {
    data class TextBlock(val text: String) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
}

object MarkdownHelper {

    /**
     * Extracts <think>...</think> tags for separate collapsed reasoning view.
     */
    fun parseThinking(rawText: String): Pair<String?, String> {
        val thinkStart = rawText.indexOf("<think>")
        if (thinkStart == -1) {
            return Pair(null, rawText)
        }
        val thinkEnd = rawText.indexOf("</think>", thinkStart + 7)
        return if (thinkEnd != -1) {
            val thinking = rawText.substring(thinkStart + 7, thinkEnd).trim()
            val before = rawText.substring(0, thinkStart).trim()
            val after = rawText.substring(thinkEnd + 8).trim()
            val mainContent = listOf(before, after).filter { it.isNotEmpty() }.joinToString("\n\n")
            Pair(thinking.ifEmpty { null }, mainContent)
        } else {
            // Still streaming thinking block
            val thinking = rawText.substring(thinkStart + 7).trim()
            val before = rawText.substring(0, thinkStart).trim()
            Pair(thinking.ifEmpty { null }, before)
        }
    }

    /**
     * Splits text into text segments and fenced code blocks ```language ... ```
     */
    fun parseBlocks(text: String): List<MarkdownBlock> {
        if (text.isEmpty()) return emptyList()

        val blocks = mutableListOf<MarkdownBlock>()
        var currentIndex = 0
        val fence = "```"

        while (currentIndex < text.length) {
            val fenceStart = text.indexOf(fence, currentIndex)
            if (fenceStart == -1) {
                val remainingText = text.substring(currentIndex).trim()
                if (remainingText.isNotEmpty()) {
                    blocks.add(MarkdownBlock.TextBlock(remainingText))
                }
                break
            }

            // Text before the code block
            if (fenceStart > currentIndex) {
                val preText = text.substring(currentIndex, fenceStart).trim()
                if (preText.isNotEmpty()) {
                    blocks.add(MarkdownBlock.TextBlock(preText))
                }
            }

            val afterFence = fenceStart + fence.length
            val newlineAfterFence = text.indexOf('\n', afterFence)

            val language: String
            val codeStart: Int
            if (newlineAfterFence != -1 && newlineAfterFence - afterFence < 30) {
                language = text.substring(afterFence, newlineAfterFence).trim()
                codeStart = newlineAfterFence + 1
            } else {
                language = ""
                codeStart = afterFence
            }

            val fenceEnd = text.indexOf(fence, codeStart)
            if (fenceEnd != -1) {
                val code = text.substring(codeStart, fenceEnd).trimEnd()
                blocks.add(MarkdownBlock.CodeBlock(language.ifEmpty { "code" }, code))
                currentIndex = fenceEnd + fence.length
            } else {
                // Unclosed code block (e.g. streaming in progress)
                val code = text.substring(codeStart).trimEnd()
                blocks.add(MarkdownBlock.CodeBlock(language.ifEmpty { "code" }, code))
                break
            }
        }

        return blocks
    }
}

@Composable
fun ThinkingSection(
    thinkingText: String,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xFF13161F),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF252A38)),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "Thinking",
                        tint = AccentMint,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Thinking Process",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentMint,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = thinkingText,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0C0E14), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(content) { MarkdownHelper.parseBlocks(content) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.TextBlock -> {
                    RenderFormattedText(block.text)
                }
                is MarkdownBlock.CodeBlock -> {
                    RenderCodeBlock(block.language, block.code)
                }
            }
        }
    }
}

@Composable
private fun RenderFormattedText(text: String) {
    // Quick formatting for bold **text**, inline `code`, and bullet points
    val annotated = remember(text) {
        buildAnnotatedString {
            var i = 0
            while (i < text.length) {
                if (text.startsWith("**", i)) {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextPrimary))
                        append(text.substring(i + 2, end))
                        pop()
                        i = end + 2
                        continue
                    }
                } else if (text[i] == '`') {
                    val end = text.indexOf('`', i + 1)
                    if (end != -1) {
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = DarkSurfaceVariant,
                                color = AccentBlue,
                                fontSize = 13.sp
                            )
                        )
                        append(" ${text.substring(i + 1, end)} ")
                        pop()
                        i = end + 1
                        continue
                    }
                }
                append(text[i])
                i++
            }
        }
    }

    Text(
        text = annotated,
        fontSize = 14.sp,
        color = TextPrimary,
        lineHeight = 21.sp
    )
}

@Composable
private fun RenderCodeBlock(
    language: String,
    code: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }
    val horizontalScrollState = rememberScrollState()

    Surface(
        color = CodeBlockBg,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141720))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = AccentBlue
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("code", code)
                            clipboard.setPrimaryClip(clip)
                            isCopied = true
                            scope.launch {
                                delay(2000)
                                isCopied = false
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (isCopied) AccentMint else TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCopied) "Copied!" else "Copy",
                        fontSize = 11.sp,
                        color = if (isCopied) AccentMint else TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Code Content
            Text(
                text = code,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = TextPrimary,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScrollState)
                    .padding(12.dp)
            )
        }
    }
}
