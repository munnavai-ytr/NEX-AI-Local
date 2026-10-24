package com.example.ui

import android.app.Application
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.BenchmarkResultEntity
import com.example.data.ModelEntity
import com.example.data.StarterCatalogModel
import com.example.data.StarterModelCatalog
import com.example.inference.EngineManager
import com.example.inference.EngineStatus
import com.example.inference.GenerationEvent
import com.example.util.DeviceProtectionManager
import com.example.util.DownloadMetadata
import com.example.util.DownloadState
import com.example.util.EncryptedTokenStorage
import com.example.util.FitLevel
import com.example.util.ModelDownloadManager
import com.example.util.RamFitChecker
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class TerminalColor {
    GREEN,
    YELLOW,
    RED,
    GREY
}

data class TerminalLine(
    val id: Long,
    val text: String,
    val color: TerminalColor = TerminalColor.GREY,
    val isCommandEcho: Boolean = false
)

data class HelpCommandEntry(
    val category: String,
    val command: String,
    val summary: String,
    val usage: String,
    val example: String
)

object TerminalHelpCatalog {
    val CATEGORIES = listOf("Discover", "Install", "Manage", "Device", "Server")

    val ENTRIES: List<HelpCommandEntry> = listOf(
        // Discover
        HelpCommandEntry(
            category = "Discover",
            command = "help",
            summary = "Show all commands or details for one command.",
            usage = "help [command]",
            example = "help install"
        ),
        HelpCommandEntry(
            category = "Discover",
            command = "models",
            summary = "Show built-in starter catalog with size and RAM fit badge.",
            usage = "models",
            example = "models"
        ),
        HelpCommandEntry(
            category = "Discover",
            command = "search",
            summary = "Search LiteRT-LM models on Hugging Face.",
            usage = "search <text> [--max-size 3gb]",
            example = "search qwen --max-size 2gb"
        ),
        HelpCommandEntry(
            category = "Discover",
            command = "info",
            summary = "Inspect size, license, .litertlm files, and phone fit verdict.",
            usage = "info <id|repo>",
            example = "info qwen3_0_6b"
        ),
        // Install
        HelpCommandEntry(
            category = "Install",
            command = "install",
            summary = "Download and verify a .litertlm model from catalog or Hugging Face.",
            usage = "install <catalog-id | hf-repo[/file]> [--yes]",
            example = "install qwen3_0_6b --yes"
        ),
        HelpCommandEntry(
            category = "Install",
            command = "pause",
            summary = "Pause the active model download.",
            usage = "pause",
            example = "pause"
        ),
        HelpCommandEntry(
            category = "Install",
            command = "resume",
            summary = "Resume a paused or interrupted model download.",
            usage = "resume",
            example = "resume"
        ),
        HelpCommandEntry(
            category = "Install",
            command = "cancel",
            summary = "Cancel the active download (keeps partial file for resume).",
            usage = "cancel",
            example = "cancel"
        ),
        // Manage
        HelpCommandEntry(
            category = "Manage",
            command = "list",
            summary = "List all installed models stored on this phone.",
            usage = "list",
            example = "list"
        ),
        HelpCommandEntry(
            category = "Manage",
            command = "load",
            summary = "Load an installed model into LiteRT-LM memory.",
            usage = "load <name>",
            example = "load Qwen3 0.6B"
        ),
        HelpCommandEntry(
            category = "Manage",
            command = "unload",
            summary = "Unload the active model and free RAM immediately.",
            usage = "unload",
            example = "unload"
        ),
        HelpCommandEntry(
            category = "Manage",
            command = "delete",
            summary = "Delete an installed model file and database record.",
            usage = "delete <name> [--yes]",
            example = "delete Qwen3 0.6B --yes"
        ),
        HelpCommandEntry(
            category = "Manage",
            command = "rename",
            summary = "Rename an installed model in the local catalog.",
            usage = "rename <old-name> <new-name>",
            example = "rename \"Qwen3 0.6B\" \"Qwen3 Fast\""
        ),
        HelpCommandEntry(
            category = "Manage",
            command = "storage",
            summary = "Show storage used by models and remaining free space.",
            usage = "storage",
            example = "storage"
        ),
        // Device
        HelpCommandEntry(
            category = "Device",
            command = "device",
            summary = "Show RAM, storage, CPU, ABI, Android version, thermal & GPU state.",
            usage = "device",
            example = "device"
        ),
        HelpCommandEntry(
            category = "Device",
            command = "bench",
            summary = "Run on-device benchmark and print tokens/sec and load time.",
            usage = "bench <name>",
            example = "bench Qwen3 0.6B"
        ),
        // Server
        HelpCommandEntry(
            category = "Server",
            command = "server",
            summary = "Check local API server status (next-phase preview).",
            usage = "server status",
            example = "server status"
        ),
        HelpCommandEntry(
            category = "Server",
            command = "key",
            summary = "List local API keys (next-phase preview).",
            usage = "key list",
            example = "key list"
        ),
        HelpCommandEntry(
            category = "Server",
            command = "clear",
            summary = "Clear the terminal screen output.",
            usage = "clear",
            example = "clear"
        ),
        HelpCommandEntry(
            category = "Server",
            command = "history",
            summary = "Print recent terminal commands.",
            usage = "history",
            example = "history"
        ),
        HelpCommandEntry(
            category = "Server",
            command = "about",
            summary = "Show NEX AI Local engine and offline architecture info.",
            usage = "about",
            example = "about"
        )
    )
}

data class ParsedCommand(
    val raw: String,
    val name: String,
    val args: List<String>,
    val flags: Set<String>,
    val options: Map<String, String>
)

object TerminalCommandParser {

    val KNOWN_COMMANDS = listOf(
        "help", "models", "search", "info",
        "install", "cancel", "pause", "resume",
        "list", "load", "unload", "delete", "rename", "storage",
        "device", "bench",
        "server", "key",
        "clear", "history", "about"
    )

    /**
     * Splits a command line into tokens while respecting single ('...') and double ("...") quotes.
     */
    fun tokenize(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inDoubleQuotes = false
        var inSingleQuotes = false
        var escaped = false

        for (ch in input.trim()) {
            when {
                escaped -> {
                    current.append(ch)
                    escaped = false
                }
                ch == '\\' && (inDoubleQuotes || inSingleQuotes) -> {
                    escaped = true
                }
                ch == '"' && !inSingleQuotes -> {
                    inDoubleQuotes = !inDoubleQuotes
                }
                ch == '\'' && !inDoubleQuotes -> {
                    inSingleQuotes = !inSingleQuotes
                }
                ch.isWhitespace() && !inDoubleQuotes && !inSingleQuotes -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.setLength(0)
                    }
                }
                else -> {
                    current.append(ch)
                }
            }
        }
        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }
        return tokens
    }

    fun parse(input: String): ParsedCommand? {
        val tokens = tokenize(input)
        if (tokens.isEmpty()) return null

        val name = tokens[0].lowercase(Locale.ROOT)
        val args = mutableListOf<String>()
        val flags = mutableSetOf<String>()
        val options = mutableMapOf<String, String>()

        var i = 1
        while (i < tokens.size) {
            val tok = tokens[i]
            if (tok.startsWith("--")) {
                val eqIndex = tok.indexOf('=')
                if (eqIndex > 2) {
                    val key = tok.substring(2, eqIndex).lowercase(Locale.ROOT)
                    val value = tok.substring(eqIndex + 1)
                    options[key] = value
                } else {
                    val key = tok.substring(2).lowercase(Locale.ROOT)
                    if (key == "max-size" && i + 1 < tokens.size && !tokens[i + 1].startsWith("--")) {
                        options[key] = tokens[i + 1]
                        i++
                    } else {
                        flags.add(key)
                    }
                }
            } else if (tok == "-y") {
                flags.add("yes")
            } else if (tok == "-f") {
                flags.add("force")
            } else {
                args.add(tok)
            }
            i++
        }

        return ParsedCommand(
            raw = input.trim(),
            name = name,
            args = args,
            flags = flags,
            options = options
        )
    }

    /**
     * Parses human-readable size strings like "3gb", "800mb", "1.5gb", "512kb" into bytes.
     * Returns null if invalid or non-positive.
     */
    fun parseSizeBytes(text: String): Long? {
        val cleaned = text.trim().lowercase(Locale.ROOT).replace(" ", "")
        if (cleaned.isEmpty()) return null

        val regex = Regex("""^(\d+(?:\.\d+)?)(tb|gb|g|mb|m|kb|k|b)?$""")
        val match = regex.matchEntire(cleaned) ?: return null
        val number = match.groupValues[1].toDoubleOrNull() ?: return null
        if (number <= 0.0) return null

        val unit = match.groupValues[2].ifEmpty { "b" }
        val multiplier = when (unit) {
            "tb" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
            "gb", "g" -> 1024.0 * 1024.0 * 1024.0
            "mb", "m" -> 1024.0 * 1024.0
            "kb", "k" -> 1024.0
            "b" -> 1.0
            else -> return null
        }
        return (number * multiplier).toLong()
    }

    /**
     * Suggests the closest command name when the user enters an unknown command.
     */
    fun suggestCommand(
        unknown: String,
        candidates: List<String> = KNOWN_COMMANDS
    ): String? {
        val target = unknown.trim().lowercase(Locale.ROOT)
        if (target.isEmpty()) return null

        // Check prefix match first
        val prefixMatch = candidates.firstOrNull { it.startsWith(target) || target.startsWith(it) }
        if (prefixMatch != null) return prefixMatch

        var bestCandidate: String? = null
        var bestDistance = Int.MAX_VALUE

        for (candidate in candidates) {
            val dist = levenshteinDistance(target, candidate.lowercase(Locale.ROOT))
            if (dist < bestDistance) {
                bestDistance = dist
                bestCandidate = candidate
            }
        }

        val threshold = when {
            target.length <= 3 -> 1
            target.length <= 6 -> 2
            else -> 3
        }
        return if (bestDistance <= threshold) bestCandidate else null
    }

    fun autocomplete(currentInput: String): List<String> {
        val trimmed = currentInput.trimStart()
        if (trimmed.contains(' ')) return emptyList()
        val prefix = trimmed.lowercase(Locale.ROOT)
        if (prefix.isEmpty()) return KNOWN_COMMANDS
        return KNOWN_COMMANDS.filter { it.startsWith(prefix) }
    }

    fun validateDownloadTarget(fileName: String, url: String): Result<Unit> {
        val cleanName = fileName.trim()
        if (cleanName.isEmpty() ||
            cleanName.contains('/') ||
            cleanName.contains('\\') ||
            cleanName.contains("..")
        ) {
            return Result.failure(
                IllegalArgumentException("Invalid file name '$fileName': path separators and '..' are not allowed.")
            )
        }
        if (!cleanName.endsWith(".litertlm", ignoreCase = true)) {
            return Result.failure(
                IllegalArgumentException(
                    "Unsupported file format '$cleanName'. Only .litertlm model files are supported by LiteRT-LM on this phone."
                )
            )
        }
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("https://huggingface.co/", ignoreCase = true)) {
            return Result.failure(
                IllegalArgumentException(
                    "Untrusted download URL. Only HTTPS downloads from https://huggingface.co/ are allowed."
                )
            )
        }
        return Result.success(Unit)
    }

    internal fun levenshteinDistance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    prev + cost
                )
                prev = temp
            }
        }
        return dp[b.length]
    }
}

private data class HfFileEntry(
    val path: String,
    val sizeBytes: Long
) {
    val fileName: String get() = path.substringAfterLast('/')
}

private data class HfRepoMetadata(
    val repoId: String,
    val downloads: Int,
    val likes: Int,
    val license: String,
    val isGated: Boolean,
    val tags: List<String>,
    val files: List<HfFileEntry>
)

private sealed interface PendingConfirmation {
    data class Install(
        val displayName: String,
        val fileName: String,
        val downloadUrl: String,
        val sizeBytes: Long,
        val license: String,
        val tier: String,
        val minRamGb: Double,
        val recRamGb: Double,
        val task: String,
        val isCurated: Boolean
    ) : PendingConfirmation

    data class Delete(
        val model: ModelEntity
    ) : PendingConfirmation
}

class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MAX_TERMINAL_LINES = 500
        private const val HF_TIMEOUT_MS = 15_000
    }

    private val db = AppDatabase.getInstance(application)
    private val modelDao = db.modelDao()
    private val benchmarkDao = db.benchmarkDao()
    private val engineManager = EngineManager.getInstance(application)
    private val downloadManager = ModelDownloadManager.getInstance(application)

    private var nextLineId = 1L

    private val _lines = MutableStateFlow<List<TerminalLine>>(emptyList())
    val lines: StateFlow<List<TerminalLine>> = _lines.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _commandHistory = MutableStateFlow<List<String>>(emptyList())
    val commandHistory: StateFlow<List<String>> = _commandHistory.asStateFlow()

    private var historyCursor: Int = -1
    private var pendingConfirmation: PendingConfirmation? = null

    val downloadState: StateFlow<DownloadState> = downloadManager.downloadState

    init {
        appendLine("NEX AI Local Terminal v1.0 [LiteRT-LM On-Device Shell]", TerminalColor.GREEN)
        appendLine("Type 'help' or tap '?' for commands. Type 'models' to see the starter catalog.", TerminalColor.GREY)
        observeDownloadEvents()
    }

    private fun observeDownloadEvents() {
        var lastLoggedCompletionPath: String? = null
        var lastLoggedErrorMsg: String? = null

        viewModelScope.launch {
            downloadManager.downloadState.collect { state ->
                when (state) {
                    is DownloadState.Completed -> {
                        val path = state.file.absolutePath
                        if (path != lastLoggedCompletionPath) {
                            lastLoggedCompletionPath = path
                            lastLoggedErrorMsg = null
                            val sizeStr = formatBytes(state.file.length())
                            appendLine(
                                "✓ Download verified & registered in Room: ${state.file.name} ($sizeStr)",
                                TerminalColor.GREEN
                            )
                            appendLine(
                                "  Run 'load \"${state.file.name}\"' to initialize it in LiteRT-LM.",
                                TerminalColor.GREY
                            )
                        }
                    }
                    is DownloadState.Error -> {
                        if (state.message != lastLoggedErrorMsg) {
                            lastLoggedErrorMsg = state.message
                            appendLine("✗ Download failed: ${state.message}", TerminalColor.RED)
                        }
                    }
                    is DownloadState.Progress -> {
                        lastLoggedCompletionPath = null
                        if (!state.isPaused) {
                            lastLoggedErrorMsg = null
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun updateInputText(newText: String) {
        _inputText.value = newText
    }

    fun insertExampleCommand(example: String) {
        _inputText.value = example
    }

    fun navigateHistoryUp() {
        val hist = _commandHistory.value
        if (hist.isEmpty()) return
        if (historyCursor < 0) {
            historyCursor = hist.lastIndex
        } else if (historyCursor > 0) {
            historyCursor--
        }
        _inputText.value = hist[historyCursor]
    }

    fun navigateHistoryDown() {
        val hist = _commandHistory.value
        if (hist.isEmpty() || historyCursor < 0) return
        if (historyCursor < hist.lastIndex) {
            historyCursor++
            _inputText.value = hist[historyCursor]
        } else {
            historyCursor = -1
            _inputText.value = ""
        }
    }

    fun handleTabAutocomplete() {
        val current = _inputText.value
        val matches = TerminalCommandParser.autocomplete(current)
        if (matches.size == 1) {
            _inputText.value = "${matches.first()} "
        } else if (matches.size > 1) {
            appendLine("Autocomplete: ${matches.joinToString("  ")}", TerminalColor.GREY)
        }
    }

    fun clearTerminal() {
        _lines.value = emptyList()
    }

    private fun appendLine(
        text: String,
        color: TerminalColor = TerminalColor.GREY,
        isCommandEcho: Boolean = false
    ) {
        val newLines = text.split('\n').map { lineText ->
            TerminalLine(
                id = nextLineId++,
                text = lineText,
                color = color,
                isCommandEcho = isCommandEcho
            )
        }
        val combined = (_lines.value + newLines)
        _lines.value = if (combined.size > MAX_TERMINAL_LINES) {
            combined.takeLast(MAX_TERMINAL_LINES)
        } else {
            combined
        }
    }

    fun submitCommand(rawInput: String = _inputText.value) {
        val trimmed = rawInput.trim()
        _inputText.value = ""
        historyCursor = -1
        if (trimmed.isEmpty()) return

        appendLine("nex> $trimmed", TerminalColor.GREEN, isCommandEcho = true)

        val updatedHistory = (_commandHistory.value + trimmed).takeLast(100)
        _commandHistory.value = updatedHistory

        viewModelScope.launch(Dispatchers.IO) {
            try {
                executeCommandSafe(trimmed)
            } catch (t: Throwable) {
                appendLine(
                    "Error executing command: ${t.message ?: t.javaClass.simpleName}",
                    TerminalColor.RED
                )
            }
        }
    }

    private suspend fun executeCommandSafe(trimmed: String) {
        // Check if user is responding to a pending confirmation prompt
        val pending = pendingConfirmation
        if (pending != null) {
            val lower = trimmed.lowercase(Locale.ROOT)
            if (lower == "y" || lower == "yes") {
                pendingConfirmation = null
                when (pending) {
                    is PendingConfirmation.Install -> startConfirmedInstall(pending)
                    is PendingConfirmation.Delete -> executeDeleteModel(pending.model)
                }
                return
            } else if (lower == "n" || lower == "no") {
                pendingConfirmation = null
                appendLine("Operation cancelled.", TerminalColor.YELLOW)
                return
            } else {
                pendingConfirmation = null
            }
        }

        val parsed = TerminalCommandParser.parse(trimmed) ?: return

        when (parsed.name) {
            "help", "?" -> handleHelp(parsed.args)
            "models" -> handleModels()
            "search" -> handleSearch(parsed)
            "info" -> handleInfo(parsed.args)
            "install" -> handleInstall(parsed)
            "cancel" -> handleCancel()
            "pause" -> handlePause()
            "resume" -> handleResume()
            "list", "ls" -> handleListInstalled()
            "load" -> handleLoad(parsed.args)
            "unload" -> handleUnload()
            "delete", "rm" -> handleDelete(parsed)
            "rename", "mv" -> handleRename(parsed.args)
            "storage", "df" -> handleStorage()
            "device", "hw" -> handleDevice()
            "bench", "benchmark" -> handleBench(parsed.args)
            "server" -> handleServer(parsed.args)
            "key", "keys" -> handleKey(parsed.args)
            "clear", "cls" -> clearTerminal()
            "history" -> handleHistory()
            "about" -> handleAbout()
            else -> {
                val suggestion = TerminalCommandParser.suggestCommand(parsed.name)
                if (suggestion != null) {
                    appendLine(
                        "Unknown command '${parsed.name}'. Did you mean '$suggestion'? (Type 'help' for all commands)",
                        TerminalColor.YELLOW
                    )
                } else {
                    appendLine(
                        "Unknown command '${parsed.name}'. Type 'help' to see available commands.",
                        TerminalColor.RED
                    )
                }
            }
        }
    }

    private fun handleHelp(args: List<String>) {
        if (args.isNotEmpty()) {
            val query = args.first().lowercase(Locale.ROOT)
            val entry = TerminalHelpCatalog.ENTRIES.firstOrNull { it.command == query }
            if (entry != null) {
                appendLine("=== ${entry.command.uppercase(Locale.ROOT)} (${entry.category}) ===", TerminalColor.GREEN)
                appendLine("Meaning : ${entry.summary}", TerminalColor.GREY)
                appendLine("Usage   : ${entry.usage}", TerminalColor.YELLOW)
                appendLine("Example : ${entry.example}", TerminalColor.GREEN)
            } else {
                val suggestion = TerminalCommandParser.suggestCommand(query)
                val hint = if (suggestion != null) " Did you mean '$suggestion'?" else ""
                appendLine("No help entry for '$query'.$hint", TerminalColor.YELLOW)
            }
            return
        }

        appendLine("=== NEX AI LOCAL TERMINAL HELP ===", TerminalColor.GREEN)
        for (category in TerminalHelpCatalog.CATEGORIES) {
            appendLine("[$category]", TerminalColor.YELLOW)
            val items = TerminalHelpCatalog.ENTRIES.filter { it.category == category }
            for (item in items) {
                appendLine(
                    "  %-18s - %s (e.g. %s)".format(item.usage, item.summary, item.example),
                    TerminalColor.GREY
                )
            }
        }
    }

    private suspend fun handleModels() {
        val context = getApplication<Application>()
        val installed = modelDao.getAllModels().first()
        appendLine("=== BUILT-IN STARTER MODEL CATALOG ===", TerminalColor.GREEN)
        for (model in StarterModelCatalog.MODELS) {
            val verdict = RamFitChecker.evaluateInstallFitForDevice(context, model.approximateSizeBytes)
            val isInstalled = installed.any {
                it.filePath.endsWith(model.fileName, ignoreCase = true) ||
                    it.name.equals(model.name, ignoreCase = true)
            }
            val installedTag = if (isInstalled) " [INSTALLED]" else ""
            val lineColor = fitLevelToTerminalColor(verdict.level)

            appendLine(
                "• ${model.id} | ${model.name} (${model.tier}) | ${model.approximateSize} | Fit: [${verdict.badge}]$installedTag",
                lineColor
            )
            appendLine(
                "  Task: ${model.task} | License: ${model.license} | ${verdict.summary}",
                TerminalColor.GREY
            )
            appendLine(
                "  Install: install ${model.id} --yes",
                TerminalColor.GREY
            )
        }
    }

    private suspend fun handleSearch(parsed: ParsedCommand) {
        if (parsed.args.isEmpty()) {
            appendLine("Usage: search <text> [--max-size 3gb]", TerminalColor.YELLOW)
            return
        }

        val maxSizeOpt = parsed.options["max-size"]
        val maxSizeBytes: Long? = if (maxSizeOpt != null) {
            val bytes = TerminalCommandParser.parseSizeBytes(maxSizeOpt)
            if (bytes == null) {
                appendLine(
                    "Invalid --max-size '$maxSizeOpt'. Examples: --max-size 3gb, --max-size 800mb",
                    TerminalColor.RED
                )
                return
            }
            bytes
        } else {
            null
        }

        val app = getApplication<Application>()
        val netErr = ModelDownloadManager.checkNetworkRestriction(app, downloadManager.wifiOnly.value)
        if (netErr != null) {
            appendLine(netErr, TerminalColor.RED)
            return
        }

        val queryText = parsed.args.joinToString(" ").trim()
        val maxFilterLabel = if (maxSizeBytes != null) " (max-size <= ${formatBytes(maxSizeBytes)})" else ""
        appendLine("Searching Hugging Face (litert-community) for '$queryText'$maxFilterLabel...", TerminalColor.GREY)

        val encoded = URLEncoder.encode(queryText, "UTF-8")
        val url = "https://huggingface.co/api/models?author=litert-community&search=$encoded&limit=30&sort=downloads&direction=-1"

        val responseResult = hfGetJsonArray(url)
        if (responseResult.isFailure) {
            appendLine(
                "Search failed: ${responseResult.exceptionOrNull()?.message ?: "Unable to reach Hugging Face."}",
                TerminalColor.RED
            )
            return
        }

        val array = responseResult.getOrThrow()
        val matchedRepos = mutableListOf<JSONObject>()

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val repoId = obj.optString("id", obj.optString("modelId", ""))
            if (repoId.isBlank()) continue

            val tagsArr = obj.optJSONArray("tags")
            var hasLiteRtTag = repoId.contains("litert", ignoreCase = true)
            if (tagsArr != null) {
                for (t in 0 until tagsArr.length()) {
                    val tag = tagsArr.optString(t, "")
                    if (tag.equals("litert-lm", ignoreCase = true) || tag.contains("litert", ignoreCase = true)) {
                        hasLiteRtTag = true
                        break
                    }
                }
            }

            val siblingsArr = obj.optJSONArray("siblings")
            var hasLiteRtFile = false
            if (siblingsArr != null) {
                for (s in 0 until siblingsArr.length()) {
                    val rfilename = siblingsArr.optJSONObject(s)?.optString("rfilename", "") ?: ""
                    if (rfilename.endsWith(".litertlm", ignoreCase = true)) {
                        hasLiteRtFile = true
                        break
                    }
                }
            }

            if (hasLiteRtTag || hasLiteRtFile) {
                matchedRepos.add(obj)
            }
        }

        if (matchedRepos.isEmpty()) {
            appendLine("No LiteRT-LM models found on Hugging Face matching '$queryText'.", TerminalColor.YELLOW)
            return
        }

        var printedCount = 0
        for (repoObj in matchedRepos.take(12)) {
            val repoId = repoObj.optString("id", repoObj.optString("modelId", ""))
            val downloads = repoObj.optInt("downloads", 0)
            val likes = repoObj.optInt("likes", 0)

            // Fetch .litertlm files from /tree/main when max-size is specified or for top results
            val filesResult = fetchRepoLiteRtFiles(repoId)
            val files = filesResult.getOrNull().orEmpty()
            val filteredFiles = if (maxSizeBytes != null) {
                files.filter { it.sizeBytes in 1..maxSizeBytes }
            } else {
                files
            }

            if (maxSizeBytes != null && filteredFiles.isEmpty()) {
                continue
            }

            printedCount++
            if (filteredFiles.isNotEmpty()) {
                val bestFile = pickBestFittingFile(filteredFiles)
                val verdict = RamFitChecker.evaluateInstallFitForDevice(app, bestFile.sizeBytes)
                val color = fitLevelToTerminalColor(verdict.level)
                appendLine(
                    "• $repoId  (↓$downloads | ♥$likes) -> ${bestFile.fileName} (${formatBytes(bestFile.sizeBytes)}) [${verdict.badge}]",
                    color
                )
                if (filteredFiles.size > 1) {
                    appendLine(
                        "  +${filteredFiles.size - 1} other .litertlm file(s). Run 'info $repoId' for all files.",
                        TerminalColor.GREY
                    )
                }
            } else {
                appendLine(
                    "• $repoId  (↓$downloads | ♥$likes) — run 'info $repoId' to inspect .litertlm files",
                    TerminalColor.GREY
                )
            }
        }

        if (printedCount == 0) {
            appendLine(
                "Found ${matchedRepos.size} repo(s), but none had .litertlm files under ${formatBytes(maxSizeBytes ?: 0L)}.",
                TerminalColor.YELLOW
            )
        } else {
            appendLine(
                "Showing $printedCount result(s). Use 'info <repo>' or 'install <repo>'.",
                TerminalColor.GREY
            )
        }
    }

    private suspend fun handleInfo(args: List<String>) {
        if (args.isEmpty()) {
            appendLine("Usage: info <id|repo>", TerminalColor.YELLOW)
            return
        }

        val target = args.joinToString(" ").trim()
        val app = getApplication<Application>()

        // 1. Check built-in starter catalog first
        val catalogMatch = findInStarterCatalog(target)
        if (catalogMatch != null) {
            val verdict = RamFitChecker.evaluateInstallFitForDevice(app, catalogMatch.approximateSizeBytes)
            appendLine("=== MODEL INFO: ${catalogMatch.name} (Catalog ID: ${catalogMatch.id}) ===", TerminalColor.GREEN)
            appendLine("Tier       : ${catalogMatch.tier} (${catalogMatch.task})", TerminalColor.GREY)
            appendLine("File       : ${catalogMatch.fileName}", TerminalColor.GREY)
            appendLine("Size       : ${catalogMatch.approximateSize} (${formatBytes(catalogMatch.approximateSizeBytes)})", TerminalColor.GREY)
            appendLine("License    : ${catalogMatch.license}", TerminalColor.GREY)
            appendLine("RAM Target : Min ${catalogMatch.minRamGb} GB | Recommended ${catalogMatch.recommendedRamGb} GB", TerminalColor.GREY)
            appendLine("Fit Verdict: ${verdict.summary}", fitLevelToTerminalColor(verdict.level))
            if (verdict.refusalReason != null) {
                appendLine("Warning    : ${verdict.refusalReason}", TerminalColor.RED)
            }
            appendLine("Source URL : ${catalogMatch.downloadUrl}", TerminalColor.GREY)
            appendLine("Summary    : ${catalogMatch.description}", TerminalColor.GREY)
            return
        }

        // 2. Otherwise query Hugging Face API
        val netErr = ModelDownloadManager.checkNetworkRestriction(app, downloadManager.wifiOnly.value)
        if (netErr != null) {
            appendLine(netErr, TerminalColor.RED)
            return
        }

        val repoId = normalizeRepoId(target)
        appendLine("Fetching repository info for '$repoId' from Hugging Face...", TerminalColor.GREY)

        val metaResult = fetchHuggingFaceRepoMetadata(repoId)
        if (metaResult.isFailure) {
            appendLine(
                "Could not fetch '$repoId': ${metaResult.exceptionOrNull()?.message}",
                TerminalColor.RED
            )
            return
        }

        val meta = metaResult.getOrThrow()
        appendLine("=== HUGGING FACE REPO: ${meta.repoId} ===", TerminalColor.GREEN)
        appendLine(
            "License    : ${meta.license.ifBlank { "Not specified" }} | Gated: ${if (meta.isGated) "YES (requires HF token)" else "No"}",
            if (meta.isGated) TerminalColor.YELLOW else TerminalColor.GREY
        )
        appendLine("Popularity : ${meta.downloads} downloads | ${meta.likes} likes", TerminalColor.GREY)

        if (meta.files.isEmpty()) {
            appendLine("No .litertlm files found in ${meta.repoId}/tree/main.", TerminalColor.RED)
            return
        }

        val bestFile = pickBestFittingFile(meta.files)
        appendLine(".litertlm files (${meta.files.size}):", TerminalColor.YELLOW)
        for (file in meta.files) {
            val verdict = RamFitChecker.evaluateInstallFitForDevice(app, file.sizeBytes)
            val bestTag = if (file.path == bestFile.path && meta.files.size > 1) " <-- [BEST FIT DEFAULT]" else ""
            appendLine(
                "  • ${file.fileName} | ${formatBytes(file.sizeBytes)} | Fit: ${verdict.summary}$bestTag",
                fitLevelToTerminalColor(verdict.level)
            )
            if (verdict.refusalReason != null) {
                appendLine("    ! ${verdict.refusalReason}", TerminalColor.RED)
            }
        }
        appendLine("Install command: install ${meta.repoId}/${bestFile.fileName} --yes", TerminalColor.GREEN)
    }

    private suspend fun handleInstall(parsed: ParsedCommand) {
        if (parsed.args.isEmpty()) {
            appendLine("Usage: install <catalog-id | hf-repo[/file]> [--yes] [--force]", TerminalColor.YELLOW)
            return
        }

        val autoYes = parsed.flags.contains("yes")
        val forceInstall = parsed.flags.contains("force")
        val rawTarget = parsed.args.joinToString(" ").trim()
        val app = getApplication<Application>()

        val netErr = ModelDownloadManager.checkNetworkRestriction(app, downloadManager.wifiOnly.value)
        if (netErr != null) {
            appendLine(netErr, TerminalColor.RED)
            return
        }

        // 1. Check built-in starter catalog first
        val catalogMatch = findInStarterCatalog(rawTarget)
        val pendingInstall: PendingConfirmation.Install = if (catalogMatch != null) {
            PendingConfirmation.Install(
                displayName = catalogMatch.name,
                fileName = catalogMatch.fileName,
                downloadUrl = catalogMatch.downloadUrl,
                sizeBytes = catalogMatch.approximateSizeBytes,
                license = catalogMatch.license,
                tier = catalogMatch.tier,
                minRamGb = catalogMatch.minRamGb,
                recRamGb = catalogMatch.recommendedRamGb,
                task = catalogMatch.task,
                isCurated = true
            )
        } else {
            // Resolve from Hugging Face repo[/file]
            val resolved = resolveHfInstallTarget(rawTarget)
            if (resolved.isFailure) {
                appendLine(
                    resolved.exceptionOrNull()?.message ?: "Failed to resolve Hugging Face model.",
                    TerminalColor.RED
                )
                return
            }
            resolved.getOrThrow()
        }

        // Validate .litertlm extension, safe filename, and https://huggingface.co/ URL
        val validation = TerminalCommandParser.validateDownloadTarget(
            fileName = pendingInstall.fileName,
            url = pendingInstall.downloadUrl
        )
        if (validation.isFailure) {
            appendLine(
                "Rejected: ${validation.exceptionOrNull()?.message}",
                TerminalColor.RED
            )
            return
        }

        // Perform phone safety & fit check (TOTAL RAM + free storage)
        val verdict = RamFitChecker.evaluateInstallFitForDevice(app, pendingInstall.sizeBytes)

        // Storage safety check: never allow if free storage < model size + 500 MB
        if (!verdict.hasEnoughStorage) {
            appendLine(
                "✗ Refused: ${verdict.refusalReason}",
                TerminalColor.RED
            )
            return
        }

        // Total RAM safety check: RED (> 60% of total RAM) refuses unless --force is passed
        if (verdict.level == FitLevel.CRITICAL) {
            if (!forceInstall) {
                appendLine(
                    "✗ ${verdict.refusalReason}",
                    TerminalColor.RED
                )
                appendLine(
                    "  Pass --force if you really want to download this model anyway.",
                    TerminalColor.YELLOW
                )
                return
            } else {
                appendLine(
                    "⚠ LOUD WARNING (--force): ${verdict.refusalReason}. Proceeding at your own risk — loading this model may trigger Android OOM termination!",
                    TerminalColor.RED
                )
            }
        }

        // Confirmation line (always shown)
        val confirmSummary = "Confirm install: ${pendingInstall.displayName} (${pendingInstall.fileName}) | " +
            "Size: ${formatBytes(pendingInstall.sizeBytes)} | Fit: ${verdict.badge} | License: ${pendingInstall.license}"
        appendLine(
            confirmSummary,
            fitLevelToTerminalColor(verdict.level)
        )

        if (autoYes) {
            startConfirmedInstall(pendingInstall)
        } else {
            pendingConfirmation = pendingInstall
            appendLine(
                "Type 'yes' to start download, 'no' to cancel, or re-run with --yes.",
                TerminalColor.YELLOW
            )
        }
    }

    private fun startConfirmedInstall(install: PendingConfirmation.Install) {
        val metadata = DownloadMetadata(
            displayName = install.displayName,
            tier = install.tier,
            minRamGb = install.minRamGb,
            recommendedRamGb = install.recRamGb,
            task = install.task,
            licenseNote = install.license,
            requiresLicenseAcceptance = false,
            isCurated = install.isCurated,
            expectedSizeBytes = install.sizeBytes
        )
        appendLine(
            "Starting download: ${install.fileName} (${formatBytes(install.sizeBytes)}) from huggingface.co...",
            TerminalColor.GREEN
        )
        downloadManager.startDownload(
            url = install.downloadUrl,
            customFileName = install.fileName,
            metadata = metadata
        )
    }

    private fun handleCancel() {
        val state = downloadManager.downloadState.value
        if (state is DownloadState.Idle) {
            appendLine("No active download to cancel.", TerminalColor.YELLOW)
            return
        }
        downloadManager.cancelDownload(deletePartial = false)
        appendLine("Download cancelled. Partial file kept on disk (use 'resume' to continue).", TerminalColor.YELLOW)
    }

    private fun handlePause() {
        val state = downloadManager.downloadState.value
        if (state !is DownloadState.Progress || state.isPaused) {
            appendLine("No active running download to pause.", TerminalColor.YELLOW)
            return
        }
        downloadManager.pauseDownload()
        appendLine("Download paused at ${state.percent}%. Run 'resume' to continue.", TerminalColor.YELLOW)
    }

    private fun handleResume() {
        downloadManager.resumeDownload()
        appendLine("Resuming model download...", TerminalColor.GREEN)
    }

    private suspend fun handleListInstalled() {
        val app = getApplication<Application>()
        val list = modelDao.getAllModels().first()
        if (list.isEmpty()) {
            appendLine("No models installed yet. Run 'models' or 'install qwen3_0_6b --yes'.", TerminalColor.YELLOW)
            return
        }

        val status = engineManager.engineStatus.value
        val loadedName = (status as? EngineStatus.Ready)?.modelName

        appendLine("=== INSTALLED MODELS (${list.size}) ===", TerminalColor.GREEN)
        for (model in list) {
            val fit = RamFitChecker.evaluateInstallFitForDevice(app, model.sizeBytes)
            val isLoadedNow = model.isLoaded ||
                (loadedName != null && (model.filePath.endsWith(loadedName) || model.name == loadedName))
            val tags = buildString {
                if (isLoadedNow) append(" [LOADED]")
                if (model.isLastUsed) append(" [LAST USED]")
            }
            val speedStr = if (model.measuredTokensPerSecond > 0.0) {
                " | ${"%.1f".format(Locale.US, model.measuredTokensPerSecond)} tok/s"
            } else {
                ""
            }
            appendLine(
                "• ${model.name} (${model.tier}) | ${formatBytes(model.sizeBytes)} | Fit: [${fit.badge}]$speedStr$tags",
                if (isLoadedNow) TerminalColor.GREEN else TerminalColor.GREY
            )
            appendLine("  File: ${File(model.filePath).name}", TerminalColor.GREY)
        }
    }

    private suspend fun handleLoad(args: List<String>) {
        if (args.isEmpty()) {
            appendLine("Usage: load <name>", TerminalColor.YELLOW)
            return
        }
        val query = args.joinToString(" ").trim()
        val model = findInstalledModel(query)
        if (model == null) {
            appendLine("Installed model '$query' not found. Run 'list' to see installed models.", TerminalColor.RED)
            return
        }

        val file = File(model.filePath)
        if (!file.exists()) {
            appendLine("Model file missing on disk at ${model.filePath}.", TerminalColor.RED)
            return
        }

        val app = getApplication<Application>()
        val ramFit = RamFitChecker.evaluate(app, model.sizeBytes)
        appendLine(
            "Fit Check: [${ramFit.badgeLabel}] ${ramFit.explanation}",
            fitLevelToTerminalColor(ramFit.level)
        )

        val useGpu = engineManager.useGpuSetting.value
        appendLine(
            "Loading '${model.name}' on ${if (useGpu) "GPU (experimental)" else "CPU"}...",
            TerminalColor.GREY
        )

        modelDao.clearLastUsed()
        modelDao.setLastUsed(model.filePath)
        modelDao.clearLoaded()
        modelDao.setLoaded(model.filePath)

        engineManager.loadModel(model.filePath, useGpu)

        val finalStatus = engineManager.engineStatus.value
        when (finalStatus) {
            is EngineStatus.Ready -> {
                if (finalStatus.fallbackNotice != null) {
                    appendLine("Notice: ${finalStatus.fallbackNotice}", TerminalColor.YELLOW)
                }
                appendLine(
                    "✓ Loaded '${model.name}' on ${finalStatus.backend} in ${finalStatus.loadTimeMs} ms.",
                    TerminalColor.GREEN
                )
            }
            is EngineStatus.Error -> {
                appendLine("✗ Failed to load '${model.name}': ${finalStatus.message}", TerminalColor.RED)
            }
            else -> {
                appendLine("Engine status: $finalStatus", TerminalColor.GREY)
            }
        }
    }

    private suspend fun handleUnload() {
        modelDao.clearLoaded()
        engineManager.unloadModel()
        appendLine("✓ Unloaded active model and released native memory.", TerminalColor.GREEN)
    }

    private suspend fun handleDelete(parsed: ParsedCommand) {
        if (parsed.args.isEmpty()) {
            appendLine("Usage: delete <name> [--yes]", TerminalColor.YELLOW)
            return
        }
        val query = parsed.args.joinToString(" ").trim()
        val model = findInstalledModel(query)
        if (model == null) {
            appendLine("Installed model '$query' not found. Run 'list' to see installed models.", TerminalColor.RED)
            return
        }

        if (parsed.flags.contains("yes")) {
            executeDeleteModel(model)
        } else {
            pendingConfirmation = PendingConfirmation.Delete(model)
            appendLine(
                "Confirm delete '${model.name}' (${formatBytes(model.sizeBytes)})? Type 'yes' to delete or 'no' to cancel.",
                TerminalColor.YELLOW
            )
        }
    }

    private suspend fun executeDeleteModel(model: ModelEntity) {
        val status = engineManager.engineStatus.value
        if (model.isLoaded || (status is EngineStatus.Ready && status.modelName == File(model.filePath).name)) {
            modelDao.clearLoaded()
            engineManager.unloadModel()
        }
        val file = File(model.filePath)
        if (file.exists()) {
            file.delete()
        }
        val partFile = File("${model.filePath}.part")
        if (partFile.exists()) {
            partFile.delete()
        }
        modelDao.deleteModel(model.id)
        appendLine("✓ Deleted model '${model.name}' and freed ${formatBytes(model.sizeBytes)}.", TerminalColor.GREEN)
    }

    private suspend fun handleRename(args: List<String>) {
        if (args.size < 2) {
            appendLine("Usage: rename <old-name> <new-name>", TerminalColor.YELLOW)
            return
        }
        val oldName = args[0].trim()
        val newName = args.drop(1).joinToString(" ").trim()
        if (newName.isBlank()) {
            appendLine("New model name cannot be blank.", TerminalColor.RED)
            return
        }

        val model = findInstalledModel(oldName)
        if (model == null) {
            appendLine("Installed model '$oldName' not found. Run 'list' to see installed models.", TerminalColor.RED)
            return
        }

        modelDao.updateModel(model.copy(name = newName))
        appendLine("✓ Renamed '${model.name}' -> '$newName'.", TerminalColor.GREEN)
    }

    private fun handleStorage() {
        val app = getApplication<Application>()
        val modelsDir = ModelDownloadManager.getModelsDir(app)
        val allFiles = modelsDir.listFiles().orEmpty()
        val modelFiles = allFiles.filter { it.isFile && it.name.endsWith(".litertlm", ignoreCase = true) }
        val partFiles = allFiles.filter { it.isFile && it.name.endsWith(".part", ignoreCase = true) }

        val modelsBytes = modelFiles.sumOf { it.length() }
        val partBytes = partFiles.sumOf { it.length() }
        val statFs = StatFs(modelsDir.path)
        val freeBytes = statFs.availableBytes
        val totalBytes = statFs.totalBytes

        appendLine("=== MODEL STORAGE SUMMARY ===", TerminalColor.GREEN)
        appendLine("Models Folder    : ${modelsDir.absolutePath}", TerminalColor.GREY)
        appendLine("Installed Models : ${modelFiles.size} file(s) (${formatBytes(modelsBytes)})", TerminalColor.GREY)
        if (partFiles.isNotEmpty()) {
            appendLine("Partial Downloads: ${partFiles.size} .part file(s) (${formatBytes(partBytes)})", TerminalColor.YELLOW)
        }
        appendLine("Total Used by AI : ${formatBytes(modelsBytes + partBytes)}", TerminalColor.GREEN)
        appendLine("Device Storage   : ${formatBytes(freeBytes)} free / ${formatBytes(totalBytes)} total", TerminalColor.GREY)
    }

    private fun handleDevice() {
        val app = getApplication<Application>()
        val mem = DeviceProtectionManager.getMemorySnapshot(app)
        val modelsDir = ModelDownloadManager.getModelsDir(app)
        val statFs = StatFs(modelsDir.path)
        val freeStorage = statFs.availableBytes
        val totalStorage = statFs.totalBytes
        val cores = Runtime.getRuntime().availableProcessors()
        val abis = Build.SUPPORTED_ABIS?.joinToString(", ") ?: "arm64-v8a"
        val androidVer = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val tempCelsius = DeviceProtectionManager.getBatteryTemperature(app)
        val thermalState = if (tempCelsius >= DeviceProtectionManager.THERMAL_THRESHOLD_CELSIUS) {
            "THROTTLING / HOT (${"%.1f".format(Locale.US, tempCelsius)}°C >= 42.0°C limit)"
        } else {
            "NOMINAL (${"%.1f".format(Locale.US, tempCelsius)}°C)"
        }
        val gpuAvailable = isGpuDelegateAvailable()

        appendLine("=== DEVICE TELEMETRY & HARDWARE PROFILE ===", TerminalColor.GREEN)
        appendLine("Device Model  : ${Build.MANUFACTURER} ${Build.MODEL}", TerminalColor.GREY)
        appendLine("System RAM    : ${mem.availMemMb} MB free / ${mem.totalMemMb} MB total (${mem.usedPercent}% used)", TerminalColor.GREY)
        appendLine("Free Storage  : ${formatBytes(freeStorage)} free / ${formatBytes(totalStorage)} total", TerminalColor.GREY)
        appendLine("CPU Cores     : $cores cores", TerminalColor.GREY)
        appendLine("CPU ABIs      : $abis", TerminalColor.GREY)
        appendLine("OS Version    : $androidVer", TerminalColor.GREY)
        appendLine(
            "Thermal State : $thermalState",
            if (tempCelsius >= DeviceProtectionManager.THERMAL_THRESHOLD_CELSIUS) TerminalColor.RED else TerminalColor.GREEN
        )
        appendLine(
            "GPU Delegate  : ${if (gpuAvailable) "yes (OpenCL library detected)" else "no (CPU fallback active)"}",
            if (gpuAvailable) TerminalColor.GREEN else TerminalColor.YELLOW
        )
    }

    private suspend fun handleBench(args: List<String>) {
        val installed = modelDao.getAllModels().first()
        if (installed.isEmpty()) {
            appendLine("No installed models to benchmark. Install a model first (e.g., 'install qwen3_0_6b --yes').", TerminalColor.RED)
            return
        }

        val query = args.joinToString(" ").trim()
        val targetModel = if (query.isNotEmpty()) {
            findInstalledModel(query) ?: run {
                appendLine("Model '$query' not found. Run 'list' to see installed models.", TerminalColor.RED)
                return
            }
        } else {
            installed.firstOrNull { it.isLoaded }
                ?: installed.firstOrNull { it.isLastUsed }
                ?: installed.first()
        }

        val useGpu = engineManager.useGpuSetting.value
        val backendLabel = if (useGpu) "GPU" else "CPU"
        appendLine(
            "Running benchmark on '${targetModel.name}' ($backendLabel)...",
            TerminalColor.YELLOW
        )

        val startLoad = SystemClock.elapsedRealtime()
        engineManager.loadModel(targetModel.filePath, useGpu = useGpu)

        var waitMs = 35_000L
        while (waitMs > 0 &&
            engineManager.engineStatus.value !is EngineStatus.Ready &&
            engineManager.engineStatus.value !is EngineStatus.Error
        ) {
            kotlinx.coroutines.delay(200)
            waitMs -= 200
        }

        val status = engineManager.engineStatus.value
        if (status !is EngineStatus.Ready) {
            val errMsg = (status as? EngineStatus.Error)?.message ?: "Model load timed out."
            appendLine("✗ Benchmark aborted: $errMsg", TerminalColor.RED)
            return
        }

        val loadTimeMs = status.loadTimeMs.coerceAtLeast(SystemClock.elapsedRealtime() - startLoad)
        val actualBackend = status.backend
        appendLine("Model loaded on $actualBackend in ${loadTimeMs} ms. Generating benchmark tokens...", TerminalColor.GREY)

        val engine = engineManager.getEngine()
        val startGen = SystemClock.elapsedRealtime()
        var ttftMs: Long? = null
        var tokenCount = 0
        var peakMemMb = DeviceProtectionManager.getCurrentProcessMemoryMb()
        val deferred = CompletableDeferred<Result<BenchmarkResultEntity>>()

        try {
            engine.streamGenerate(prompt = BenchmarkPrompts.SHORT_TEXT).collect { event ->
                when (event) {
                    is GenerationEvent.Token -> {
                        if (ttftMs == null) {
                            ttftMs = SystemClock.elapsedRealtime() - startGen
                        }
                        tokenCount++
                        val memNow = DeviceProtectionManager.getCurrentProcessMemoryMb()
                        if (memNow > peakMemMb) peakMemMb = memNow
                    }
                    is GenerationEvent.Complete -> {
                        val totalDurationMs = SystemClock.elapsedRealtime() - startGen
                        val ttft = ttftMs ?: totalDurationMs
                        val decodeDurationMs = (totalDurationMs - ttft).coerceAtLeast(1L)
                        val tps = if (tokenCount > 0) {
                            tokenCount.toDouble() / (decodeDurationMs / 1000.0)
                        } else {
                            0.0
                        }
                        val verdict = BenchmarkResultEntity.computeVerdict(tps)
                        val temp = DeviceProtectionManager.getBatteryTemperature(getApplication())

                        val res = BenchmarkResultEntity(
                            modelName = targetModel.name,
                            modelPath = targetModel.filePath,
                            backend = actualBackend,
                            promptType = BenchmarkPrompts.SHORT_NAME,
                            promptText = BenchmarkPrompts.SHORT_TEXT,
                            loadTimeMs = loadTimeMs,
                            timeToFirstTokenMs = ttft,
                            tokensPerSecond = tps,
                            totalTokens = tokenCount,
                            durationMs = totalDurationMs,
                            peakMemoryMb = peakMemMb,
                            verdict = verdict,
                            batteryTempCelsius = temp
                        )
                        benchmarkDao.insert(res)
                        if (tps > 0.0) {
                            modelDao.updateMeasuredSpeed(targetModel.filePath, tps)
                        }
                        deferred.complete(Result.success(res))
                    }
                    is GenerationEvent.Error -> {
                        deferred.complete(Result.failure(event.error))
                    }
                }
            }
        } catch (t: Throwable) {
            deferred.complete(Result.failure(t))
        }

        val benchResult = deferred.await()
        benchResult.onSuccess { res ->
            appendLine("=== BENCHMARK COMPLETE: ${res.modelName} (${res.backend}) ===", TerminalColor.GREEN)
            appendLine(
                "Decode Speed : ${"%.1f".format(Locale.US, res.tokensPerSecond)} tokens/sec (${res.verdict})",
                TerminalColor.GREEN
            )
            appendLine(
                "Load Time    : ${res.loadTimeMs} ms | TTFT: ${res.timeToFirstTokenMs} ms | Tokens: ${res.totalTokens}",
                TerminalColor.GREY
            )
            appendLine(
                "Peak Memory  : ${"%.0f".format(Locale.US, res.peakMemoryMb)} MB | Temp: ${"%.1f".format(Locale.US, res.batteryTempCelsius)}°C",
                TerminalColor.GREY
            )
        }.onFailure { err ->
            appendLine("✗ Benchmark failed: ${err.message}", TerminalColor.RED)
        }
    }

    private fun handleServer(args: List<String>) {
        val sub = args.firstOrNull()?.lowercase(Locale.ROOT) ?: "status"
        if (sub == "status") {
            appendLine("=== LOCAL API SERVER STATUS (PREVIEW) ===", TerminalColor.YELLOW)
            appendLine("Status  : STANDBY (Placeholder — full OpenAI-compatible HTTP routing will be wired in the next phase)", TerminalColor.GREY)
            appendLine("Endpoint: http://127.0.0.1:8080/v1/chat/completions", TerminalColor.GREY)
        } else {
            appendLine("Usage: server status", TerminalColor.YELLOW)
        }
    }

    private fun handleKey(args: List<String>) {
        val sub = args.firstOrNull()?.lowercase(Locale.ROOT) ?: "list"
        if (sub == "list") {
            appendLine("=== LOCAL API KEYS (PREVIEW) ===", TerminalColor.YELLOW)
            appendLine("No per-model API keys registered yet (key management will be wired in the next phase).", TerminalColor.GREY)
        } else {
            appendLine("Usage: key list", TerminalColor.YELLOW)
        }
    }

    private fun handleHistory() {
        val hist = _commandHistory.value
        if (hist.isEmpty()) {
            appendLine("No commands in history.", TerminalColor.GREY)
            return
        }
        appendLine("=== COMMAND HISTORY ===", TerminalColor.GREEN)
        hist.forEachIndexed { index, cmd ->
            appendLine("%3d  %s".format(index + 1, cmd), TerminalColor.GREY)
        }
    }

    private fun handleAbout() {
        appendLine("=== ABOUT NEX AI LOCAL ===", TerminalColor.GREEN)
        appendLine("Engine   : Google LiteRT-LM (com.google.ai.edge.litertlm)", TerminalColor.GREY)
        appendLine("Format   : .litertlm on-device model weights", TerminalColor.GREY)
        appendLine("Privacy  : 100% offline inference. Zero cloud AI APIs, zero telemetry.", TerminalColor.GREEN)
        appendLine("Target   : Optimized for 6 GB RAM MediaTek Helio G95 & Android 8+ phones.", TerminalColor.GREY)
    }

    // --- Helper methods for Catalog, Installed Models & Hugging Face Discovery ---

    private fun findInStarterCatalog(query: String): StarterCatalogModel? {
        val clean = query.trim()
        return StarterModelCatalog.MODELS.firstOrNull {
            it.id.equals(clean, ignoreCase = true) ||
                it.name.equals(clean, ignoreCase = true) ||
                it.fileName.equals(clean, ignoreCase = true) ||
                it.fileName.removeSuffix(".litertlm").equals(clean, ignoreCase = true)
        }
    }

    private suspend fun findInstalledModel(query: String): ModelEntity? {
        val clean = query.trim()
        val list = modelDao.getAllModels().first()

        // Also match catalog ID to installed model
        val catalogModel = findInStarterCatalog(clean)
        return list.firstOrNull {
            it.name.equals(clean, ignoreCase = true) ||
                File(it.filePath).name.equals(clean, ignoreCase = true) ||
                File(it.filePath).nameWithoutExtension.equals(clean, ignoreCase = true) ||
                (catalogModel != null && (it.name.equals(catalogModel.name, ignoreCase = true) ||
                    File(it.filePath).name.equals(catalogModel.fileName, ignoreCase = true)))
        } ?: list.firstOrNull {
            it.name.contains(clean, ignoreCase = true) ||
                File(it.filePath).name.contains(clean, ignoreCase = true)
        }
    }

    private fun normalizeRepoId(raw: String): String {
        val trimmed = raw.trim().removePrefix("https://huggingface.co/").trim('/')
        val parts = trimmed.split('/')
        return if (parts.size >= 2) {
            "${parts[0]}/${parts[1]}"
        } else {
            "litert-community/${parts[0]}"
        }
    }

    private suspend fun resolveHfInstallTarget(rawTarget: String): Result<PendingConfirmation.Install> {
        val cleaned = rawTarget.trim().removePrefix("https://huggingface.co/").trim('/')
        val segments = cleaned.split('/').filter { it.isNotBlank() }

        val repoId: String
        val explicitFileName: String?

        when {
            segments.isEmpty() -> {
                return Result.failure(IllegalArgumentException("Please provide a catalog ID or Hugging Face repo."))
            }
            segments.size == 1 -> {
                if (segments[0].endsWith(".litertlm", ignoreCase = true)) {
                    return Result.failure(
                        IllegalArgumentException("Please specify the Hugging Face repository (e.g. litert-community/Qwen3-0.6B).")
                    )
                }
                repoId = "litert-community/${segments[0]}"
                explicitFileName = null
            }
            segments.size == 2 -> {
                if (segments[1].endsWith(".litertlm", ignoreCase = true)) {
                    repoId = "litert-community/${segments[0]}"
                    explicitFileName = segments[1]
                } else {
                    repoId = "${segments[0]}/${segments[1]}"
                    explicitFileName = null
                }
            }
            else -> {
                repoId = "${segments[0]}/${segments[1]}"
                explicitFileName = segments.last()
            }
        }

        if (explicitFileName != null && !explicitFileName.endsWith(".litertlm", ignoreCase = true)) {
            return Result.failure(
                IllegalArgumentException(
                    "Unsupported file '$explicitFileName'. Only .litertlm model files can be installed."
                )
            )
        }

        appendLine("Inspecting Hugging Face repository '$repoId'...", TerminalColor.GREY)
        val metaResult = fetchHuggingFaceRepoMetadata(repoId)
        if (metaResult.isFailure) {
            return Result.failure(
                metaResult.exceptionOrNull() ?: IllegalStateException("Failed to query Hugging Face.")
            )
        }

        val meta = metaResult.getOrThrow()
        if (meta.files.isEmpty()) {
            return Result.failure(
                IllegalStateException("Repository '${meta.repoId}' does not contain any .litertlm files in /tree/main.")
            )
        }

        val chosenFile: HfFileEntry = if (explicitFileName != null) {
            meta.files.firstOrNull {
                it.fileName.equals(explicitFileName, ignoreCase = true) ||
                    it.path.equals(explicitFileName, ignoreCase = true)
            } ?: return Result.failure(
                IllegalArgumentException(
                    "File '$explicitFileName' not found in ${meta.repoId}. Available .litertlm files: ${meta.files.joinToString { it.fileName }}"
                )
            )
        } else {
            val best = pickBestFittingFile(meta.files)
            if (meta.files.size > 1) {
                appendLine("Found ${meta.files.size} .litertlm files in ${meta.repoId}:", TerminalColor.YELLOW)
                val app = getApplication<Application>()
                for (f in meta.files) {
                    val v = RamFitChecker.evaluateInstallFitForDevice(app, f.sizeBytes)
                    val tag = if (f.path == best.path) " <-- [SELECTED BEST FIT]" else ""
                    appendLine("  • ${f.fileName} (${formatBytes(f.sizeBytes)}) [${v.badge}]$tag", fitLevelToTerminalColor(v.level))
                }
            }
            best
        }

        val downloadUrl = "https://huggingface.co/${meta.repoId}/resolve/main/${chosenFile.path}"
        return Result.success(
            PendingConfirmation.Install(
                displayName = chosenFile.fileName.removeSuffix(".litertlm"),
                fileName = chosenFile.fileName,
                downloadUrl = downloadUrl,
                sizeBytes = chosenFile.sizeBytes,
                license = meta.license.ifBlank { "See repo" },
                tier = ModelEntity.inferTier(chosenFile.sizeBytes),
                minRamGb = ModelEntity.inferMinRamGb(chosenFile.sizeBytes),
                recRamGb = ModelEntity.inferRecommendedRamGb(chosenFile.sizeBytes),
                task = "chat",
                isCurated = false
            )
        )
    }

    private fun pickBestFittingFile(files: List<HfFileEntry>): HfFileEntry {
        val app = getApplication<Application>()
        val sortedBySize = files.sortedBy { it.sizeBytes }
        val greenFiles = sortedBySize.filter {
            val v = RamFitChecker.evaluateInstallFitForDevice(app, it.sizeBytes)
            v.level == FitLevel.OPTIMAL && v.hasEnoughStorage
        }
        if (greenFiles.isNotEmpty()) {
            return greenFiles.last()
        }
        val yellowFiles = sortedBySize.filter {
            val v = RamFitChecker.evaluateInstallFitForDevice(app, it.sizeBytes)
            v.level == FitLevel.TIGHT && v.hasEnoughStorage
        }
        if (yellowFiles.isNotEmpty()) {
            return yellowFiles.first()
        }
        return sortedBySize.first()
    }

    private suspend fun fetchHuggingFaceRepoMetadata(repoId: String): Result<HfRepoMetadata> = withContext(Dispatchers.IO) {
        val modelUrl = "https://huggingface.co/api/models/$repoId"
        val modelObjResult = hfGetJsonObject(modelUrl)
        if (modelObjResult.isFailure) {
            return@withContext Result.failure(modelObjResult.exceptionOrNull()!!)
        }
        val modelObj = modelObjResult.getOrThrow()
        val downloads = modelObj.optInt("downloads", 0)
        val likes = modelObj.optInt("likes", 0)
        val gatedVal = modelObj.opt("gated")
        val isGated = when (gatedVal) {
            is Boolean -> gatedVal
            is String -> gatedVal.isNotBlank() && !gatedVal.equals("false", ignoreCase = true)
            else -> false
        }

        val tags = mutableListOf<String>()
        var license = ""
        val cardData = modelObj.optJSONObject("cardData")
        if (cardData != null) {
            license = cardData.optString("license", "")
        }
        val tagsArr = modelObj.optJSONArray("tags")
        if (tagsArr != null) {
            for (i in 0 until tagsArr.length()) {
                val tag = tagsArr.optString(i, "")
                tags.add(tag)
                if (license.isBlank() && tag.startsWith("license:", ignoreCase = true)) {
                    license = tag.substringAfter(':')
                }
            }
        }

        val filesResult = fetchRepoLiteRtFiles(repoId)
        if (filesResult.isFailure) {
            return@withContext Result.failure(filesResult.exceptionOrNull()!!)
        }

        Result.success(
            HfRepoMetadata(
                repoId = repoId,
                downloads = downloads,
                likes = likes,
                license = license.ifBlank { "Apache-2.0 / Community" },
                isGated = isGated,
                tags = tags,
                files = filesResult.getOrThrow()
            )
        )
    }

    private suspend fun fetchRepoLiteRtFiles(repoId: String): Result<List<HfFileEntry>> = withContext(Dispatchers.IO) {
        val treeUrl = "https://huggingface.co/api/models/$repoId/tree/main"
        val arrResult = hfGetJsonArray(treeUrl)
        if (arrResult.isFailure) {
            return@withContext Result.failure(arrResult.exceptionOrNull()!!)
        }
        val arr = arrResult.getOrThrow()
        val list = mutableListOf<HfFileEntry>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val path = item.optString("path", "")
            if (!path.endsWith(".litertlm", ignoreCase = true)) continue
            val lfsObj = item.optJSONObject("lfs")
            val sizeBytes = when {
                lfsObj != null && lfsObj.optLong("size", -1L) > 0L -> lfsObj.optLong("size")
                else -> item.optLong("size", 0L)
            }
            list.add(HfFileEntry(path = path, sizeBytes = sizeBytes))
        }
        Result.success(list)
    }

    private suspend fun hfGetJsonArray(urlString: String): Result<JSONArray> = withContext(Dispatchers.IO) {
        val rawResult = hfHttpGet(urlString)
        rawResult.mapCatching { body -> JSONArray(body) }
    }

    private suspend fun hfGetJsonObject(urlString: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        val rawResult = hfHttpGet(urlString)
        rawResult.mapCatching { body -> JSONObject(body) }
    }

    private suspend fun hfHttpGet(urlString: String): Result<String> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val app = getApplication<Application>()
            val token = EncryptedTokenStorage.getToken(app)
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = HF_TIMEOUT_MS
                readTimeout = HF_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                Result.success(text)
            } else {
                val errMsg = when (code) {
                    HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                        "Repository is gated or requires authentication (HTTP $code). Add your Hugging Face token in Settings -> Hugging Face Access Token."
                    HttpURLConnection.HTTP_NOT_FOUND ->
                        "Repository or file not found on Hugging Face (HTTP 404)."
                    else ->
                        "Hugging Face returned HTTP $code."
                }
                Result.failure(IllegalStateException(errMsg))
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Network error"
            Result.failure(
                IllegalStateException("Offline or unable to reach Hugging Face ($msg). Check your internet connection.")
            )
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {}
        }
    }

    private fun isGpuDelegateAvailable(): Boolean {
        val candidatePaths = listOf(
            "/vendor/lib64/libOpenCL.so",
            "/system/vendor/lib64/libOpenCL.so",
            "/system/lib64/libOpenCL.so",
            "/vendor/lib/libOpenCL.so",
            "/system/vendor/lib/libOpenCL.so"
        )
        if (candidatePaths.any { File(it).exists() }) {
            return true
        }
        return try {
            System.loadLibrary("OpenCL")
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun fitLevelToTerminalColor(level: FitLevel): TerminalColor {
        return when (level) {
            FitLevel.OPTIMAL -> TerminalColor.GREEN
            FitLevel.TIGHT -> TerminalColor.YELLOW
            FitLevel.CRITICAL -> TerminalColor.RED
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            "%.2f GB".format(Locale.US, mb / 1024.0)
        } else {
            "%.0f MB".format(Locale.US, mb)
        }
    }
}
