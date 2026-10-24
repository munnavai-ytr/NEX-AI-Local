package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.example.data.CodeChunkEntity
import com.example.data.ProjectFolderEntity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

data class IndexingProgress(
    val currentFile: String,
    val filesProcessed: Int,
    val chunksCreated: Int,
    val isComplete: Boolean = false,
    val error: String? = null
)

data class RetrievedSource(
    val fileName: String,
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val content: String,
    val score: Double
) {
    fun toDisplayString(): String = "$fileName (lines $startLine–$endLine)"
}

object ProjectIndexer {

    private const val TAG = "ProjectIndexer"
    private const val MAX_FILE_SIZE_BYTES = 250_000L // 250 KB
    private const val CHUNK_LINES = 50
    private const val CHUNK_OVERLAP = 5

    private val IGNORED_DIR_NAMES = setOf(
        ".git", ".gradle", "build", ".idea", "node_modules", "dist",
        "bin", ".target", "out", "obj", ".vs", ".cxx", ".externalNativeBuild"
    )

    private val BINARY_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "ico", "pdf", "zip", "tar", "gz",
        "7z", "rar", "jar", "aar", "apk", "aab", "dex", "class", "so", "dylib",
        "dll", "exe", "litertlm", "bin", "dat", "db", "sqlite", "mp3", "mp4",
        "wav", "ogg", "flac", "ttf", "otf", "woff", "woff2", "keystore", "jks"
    )

    private val TEXT_EXTENSIONS = setOf(
        "kt", "kts", "java", "xml", "json", "md", "txt", "gradle", "properties",
        "toml", "py", "js", "ts", "tsx", "jsx", "c", "cpp", "h", "hpp", "rs",
        "go", "swift", "rb", "php", "sh", "bash", "sql", "html", "css", "scss",
        "yml", "yaml", "env", "conf", "cfg", "ini", "pro"
    )

    private val STOPWORDS = setOf(
        "a", "about", "above", "after", "again", "all", "am", "an", "and", "any",
        "are", "as", "at", "be", "because", "been", "before", "being", "below",
        "between", "both", "but", "by", "can", "could", "did", "do", "does",
        "doing", "down", "during", "each", "few", "for", "from", "further",
        "had", "has", "have", "having", "he", "her", "here", "hers", "herself",
        "him", "himself", "his", "how", "i", "if", "in", "into", "is", "it",
        "its", "itself", "just", "me", "more", "most", "my", "myself", "no",
        "nor", "not", "now", "of", "off", "on", "once", "only", "or", "other",
        "our", "ours", "ourselves", "out", "over", "own", "same", "she", "should",
        "so", "some", "such", "than", "that", "the", "their", "theirs", "them",
        "themselves", "then", "there", "these", "they", "this", "those", "through",
        "to", "too", "under", "until", "up", "very", "was", "we", "were", "what",
        "when", "where", "which", "while", "who", "whom", "why", "with", "would",
        "you", "your", "yours", "yourself", "yourselves", "code", "file", "please",
        "tell", "explain", "find", "show", "give", "help"
    )

    /**
     * Recursively traverses a SAF document tree, parses eligible text/code files,
     * slices them into 40-60 line chunks, and builds a list of CodeChunkEntity.
     */
    suspend fun indexFolder(
        context: Context,
        treeUri: Uri,
        onProgress: (IndexingProgress) -> Unit
    ): Pair<ProjectFolderEntity, List<CodeChunkEntity>> {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val projectUriString = treeUri.toString()

        val rootName = getDocumentName(context, treeUri, rootDocId) ?: "Project"
        val chunks = mutableListOf<CodeChunkEntity>()
        var filesProcessed = 0

        fun processDirectory(parentDocId: String, currentPath: String) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            )

            try {
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(idCol)
                        val name = cursor.getString(nameCol) ?: continue
                        val mimeType = cursor.getString(mimeCol) ?: ""
                        val size = if (!cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L

                        val relPath = if (currentPath.isEmpty()) name else "$currentPath/$name"

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (!IGNORED_DIR_NAMES.contains(name.lowercase(Locale.ROOT)) && !name.startsWith(".")) {
                                processDirectory(docId, relPath)
                            }
                        } else {
                            if (isIndexableTextFile(name, mimeType, size)) {
                                filesProcessed++
                                onProgress(
                                    IndexingProgress(
                                        currentFile = name,
                                        filesProcessed = filesProcessed,
                                        chunksCreated = chunks.size
                                    )
                                )

                                val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                                val fileChunks = parseFileChunks(context, fileUri, projectUriString, relPath, name)
                                chunks.addAll(fileChunks)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error traversing directory $parentDocId", e)
            }
        }

        processDirectory(rootDocId, "")

        val project = ProjectFolderEntity(
            uriString = projectUriString,
            displayName = rootName,
            fileCount = filesProcessed,
            chunkCount = chunks.size,
            lastIndexedTimestamp = System.currentTimeMillis()
        )

        onProgress(
            IndexingProgress(
                currentFile = "",
                filesProcessed = filesProcessed,
                chunksCreated = chunks.size,
                isComplete = true
            )
        )

        return Pair(project, chunks)
    }

    private fun isIndexableTextFile(name: String, mimeType: String, sizeBytes: Long): Boolean {
        if (sizeBytes > MAX_FILE_SIZE_BYTES) return false
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (BINARY_EXTENSIONS.contains(ext)) return false
        if (TEXT_EXTENSIONS.contains(ext)) return true
        if (mimeType.startsWith("text/")) return true
        if (mimeType.contains("json") || mimeType.contains("xml") || mimeType.contains("javascript")) return true
        return ext.isNotEmpty() && !name.startsWith(".")
    }

    private fun parseFileChunks(
        context: Context,
        fileUri: Uri,
        projectUri: String,
        filePath: String,
        fileName: String
    ): List<CodeChunkEntity> {
        val result = mutableListOf<CodeChunkEntity>()
        try {
            val lines = mutableListOf<String>()
            context.contentResolver.openInputStream(fileUri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lineSeq ->
                    lines.addAll(lineSeq)
                }
            } ?: return emptyList()

            if (lines.isEmpty()) return emptyList()

            if (lines.size <= CHUNK_LINES) {
                val content = lines.joinToString("\n")
                if (content.isNotBlank()) {
                    result.add(
                        CodeChunkEntity(
                            projectUri = projectUri,
                            filePath = filePath,
                            fileName = fileName,
                            startLine = 1,
                            endLine = lines.size,
                            content = content
                        )
                    )
                }
                return result
            }

            var start = 0
            while (start < lines.size) {
                val end = (start + CHUNK_LINES).coerceAtMost(lines.size)
                val chunkContent = lines.subList(start, end).joinToString("\n")
                if (chunkContent.isNotBlank()) {
                    result.add(
                        CodeChunkEntity(
                            projectUri = projectUri,
                            filePath = filePath,
                            fileName = fileName,
                            startLine = start + 1,
                            endLine = end,
                            content = chunkContent
                        )
                    )
                }
                if (end >= lines.size) break
                start += (CHUNK_LINES - CHUNK_OVERLAP)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing file $filePath", e)
        }
        return result
    }

    private fun getDocumentName(context: Context, treeUri: Uri, docId: String): String? {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        return try {
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Performs full-text query extraction and multi-factor ranking across indexed code chunks.
     */
    fun searchAndRank(
        chunks: List<CodeChunkEntity>,
        query: String,
        topK: Int = 3,
        maxTokensBudget: Int = 650
    ): List<RetrievedSource> {
        if (chunks.isEmpty() || query.isBlank()) return emptyList()

        // Extract normalized query terms
        val rawTerms = query.lowercase(Locale.ROOT)
            .split(Regex("[^a-zA-Z0-9_]"))
            .map { it.trim() }
            .filter { it.length >= 3 && !STOPWORDS.contains(it) }

        if (rawTerms.isEmpty()) return emptyList()

        val uniqueTerms = rawTerms.distinct()

        val scoredList = mutableListOf<RetrievedSource>()

        for (chunk in chunks) {
            val contentLower = chunk.content.lowercase(Locale.ROOT)
            val fileNameLower = chunk.fileName.lowercase(Locale.ROOT)
            val filePathLower = chunk.filePath.lowercase(Locale.ROOT)

            var score = 0.0
            var matchedTermCount = 0

            for (term in uniqueTerms) {
                var termMatchedInChunk = false

                // File name & path match bonus
                if (fileNameLower.contains(term)) {
                    score += 15.0
                    termMatchedInChunk = true
                } else if (filePathLower.contains(term)) {
                    score += 8.0
                    termMatchedInChunk = true
                }

                // Content occurrence match
                if (contentLower.contains(term)) {
                    termMatchedInChunk = true
                    // Count occurrences up to 6
                    var idx = 0
                    var occurrences = 0
                    while (occurrences < 6) {
                        val found = contentLower.indexOf(term, idx)
                        if (found == -1) break
                        occurrences++
                        idx = found + term.length
                    }
                    score += (occurrences * 2.5)
                }

                if (termMatchedInChunk) {
                    matchedTermCount++
                }
            }

            // Reward matching multiple distinct query terms
            if (matchedTermCount > 1) {
                score += (matchedTermCount * 6.0)
            }

            if (score > 3.0) {
                scoredList.add(
                    RetrievedSource(
                        fileName = chunk.fileName,
                        filePath = chunk.filePath,
                        startLine = chunk.startLine,
                        endLine = chunk.endLine,
                        content = chunk.content,
                        score = score
                    )
                )
            }
        }

        // Sort descending by relevance score
        val sorted = scoredList.sortedByDescending { it.score }

        // Select top K within token budget (~4 chars per token)
        val selected = mutableListOf<RetrievedSource>()
        var accumulatedChars = 0
        val maxChars = maxTokensBudget * 4

        for (item in sorted) {
            if (selected.size >= topK) break
            val chars = item.content.length
            if (selected.isEmpty() || accumulatedChars + chars <= maxChars) {
                selected.add(item)
                accumulatedChars += chars
            }
        }

        return selected
    }
}
