package com.example

import android.content.ComponentCallbacks2
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ModelEntity
import com.example.inference.EngineStatus
import com.example.inference.GenerationEvent
import com.example.inference.PlaceholderInferenceEngine
import com.example.ui.MarkdownBlock
import com.example.ui.MarkdownHelper
import com.example.util.FitLevel
import com.example.util.ModelDownloadManager
import com.example.util.RamFitChecker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("NEX AI Local", appName)
  }

  @Test
  fun `placeholder engine reports unloaded and fails honestly without inventing output`() = runBlocking {
    val placeholder = PlaceholderInferenceEngine()
    assertEquals(EngineStatus.Unloaded, placeholder.status.value)
    assertFalse(placeholder.isModelLoaded)

    val event = placeholder.streamGenerate("test prompt").first()
    assertTrue(event is GenerationEvent.Error)
    val error = event as GenerationEvent.Error
    assertTrue(error.message.contains("No model loaded") || error.message.contains("Select a model"))
  }

  @Test
  fun `ram fit checker evaluates small and large file sizes appropriately`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val smallStatus = RamFitChecker.evaluate(context, 100L * 1024 * 1024)
    assertEquals(FitLevel.OPTIMAL, smallStatus.level)

    val hugeStatus = RamFitChecker.evaluate(context, 50L * 1024 * 1024 * 1024)
    assertEquals(FitLevel.CRITICAL, hugeStatus.level)
  }

  @Test
  fun `model dao inserts and tracks last used model`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getInstance(context)
    val dao = db.modelDao()

    val model = ModelEntity(
      name = "test_gemma.litertlm",
      filePath = "/data/user/0/com.example/files/models/test_gemma.litertlm",
      sizeBytes = 1024 * 1024 * 500,
      isLastUsed = true
    )
    dao.insertModel(model)

    val lastUsed = dao.getLastUsedModel()
    assertNotNull(lastUsed)
    assertEquals("test_gemma.litertlm", lastUsed?.name)
  }

  @Test
  fun `url fileName extractor parses litertlm filenames`() {
    val name1 = ModelDownloadManager.extractFileNameFromUrl("https://huggingface.co/models/gemma-3-1b.litertlm?token=123")
    assertEquals("gemma-3-1b.litertlm", name1)

    val name2 = ModelDownloadManager.extractFileNameFromUrl("https://example.com/download/qwen")
    assertEquals("qwen.litertlm", name2)
  }

  @Test
  fun `onTrimMemory executes safely without recursion or stack overflow`() {
    val app = ApplicationProvider.getApplicationContext<NEXApplication>()
    app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
    app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
  }

  @Test
  fun `markdown helper parses thinking tags and code blocks`() {
    val raw = "<think>\nCheck nullability\n</think>\nHere is the code:\n```kotlin\nfun hello() = Unit\n```"
    val (thinking, content) = MarkdownHelper.parseThinking(raw)
    assertEquals("Check nullability", thinking)
    assertTrue(content.contains("Here is the code:"))

    val blocks = MarkdownHelper.parseBlocks(content)
    assertTrue(blocks.any { it is MarkdownBlock.CodeBlock && (it as MarkdownBlock.CodeBlock).language == "kotlin" })
  }

  @Test
  fun `memory dao saves toggles and retrieves enabled facts`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getInstance(context)
    val dao = db.memoryDao()

    dao.clearAllFacts()
    val id = dao.insertFact(com.example.data.MemoryFactEntity(fact = "I use Kotlin", isEnabled = true))
    dao.insertFact(com.example.data.MemoryFactEntity(fact = "Answer in Bangla", isEnabled = false))

    val enabled = dao.getEnabledFacts()
    assertEquals(1, enabled.size)
    assertEquals("I use Kotlin", enabled.first().fact)

    dao.toggleFactEnabled(id, false)
    val enabledAfter = dao.getEnabledFacts()
    assertEquals(0, enabledAfter.size)
  }

  @Test
  fun `project indexer search and rank accurately ranks relevant chunks and extracts source`() {
    val chunk1 = com.example.data.CodeChunkEntity(
      id = 1,
      projectUri = "content://dummy/tree",
      filePath = "src/main/EngineManager.kt",
      fileName = "EngineManager.kt",
      startLine = 1,
      endLine = 50,
      content = "class EngineManager { fun initializeEngine() { /* LiteRT-LM backend */ } }"
    )
    val chunk2 = com.example.data.CodeChunkEntity(
      id = 2,
      projectUri = "content://dummy/tree",
      filePath = "src/main/Theme.kt",
      fileName = "Theme.kt",
      startLine = 1,
      endLine = 30,
      content = "val DarkBackground = Color(0xFF101010)"
    )

    val results = com.example.util.ProjectIndexer.searchAndRank(
      chunks = listOf(chunk1, chunk2),
      query = "How does EngineManager initialize engine?"
    )

    assertEquals(1, results.size)
    assertEquals("EngineManager.kt", results.first().fileName)
    assertEquals("EngineManager.kt (lines 1–50)", results.first().toDisplayString())
  }
}
