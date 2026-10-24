package com.example

import com.example.ui.TerminalCommandParser
import com.example.util.FitLevel
import com.example.util.RamFitChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests (no Robolectric) for Terminal command parser, size parser, and install fit verdict.
 */
class TerminalUnitTest {

    @Test
    fun `command parser handles double and single quotes flags and options`() {
        val parsed = TerminalCommandParser.parse("""rename "Qwen3 0.6B" 'Qwen3 Fast' --yes""")
        assertNotNull(parsed)
        assertEquals("rename", parsed?.name)
        assertEquals(listOf("Qwen3 0.6B", "Qwen3 Fast"), parsed?.args)
        assertTrue(parsed?.flags?.contains("yes") == true)

        val searchCmd = TerminalCommandParser.parse("search gemma --max-size 3gb")
        assertNotNull(searchCmd)
        assertEquals("search", searchCmd?.name)
        assertEquals(listOf("gemma"), searchCmd?.args)
        assertEquals("3gb", searchCmd?.options?.get("max-size"))
    }

    @Test
    fun `command parser suggests did you mean for typos`() {
        assertEquals("install", TerminalCommandParser.suggestCommand("instal"))
        assertEquals("models", TerminalCommandParser.suggestCommand("model"))
        assertEquals("search", TerminalCommandParser.suggestCommand("serch"))
        assertEquals("device", TerminalCommandParser.suggestCommand("devic"))
        assertNull(TerminalCommandParser.suggestCommand("xyzunknowncommand999"))
    }

    @Test
    fun `size parser converts human readable strings like 3gb and 800mb to bytes`() {
        val threeGb = TerminalCommandParser.parseSizeBytes("3gb")
        assertEquals(3L * 1024L * 1024L * 1024L, threeGb)

        val eightHundredMb = TerminalCommandParser.parseSizeBytes("800mb")
        assertEquals(800L * 1024L * 1024L, eightHundredMb)

        val onePointFiveGb = TerminalCommandParser.parseSizeBytes("1.5 GB")
        assertEquals((1.5 * 1024.0 * 1024.0 * 1024.0).toLong(), onePointFiveGb)

        assertNull(TerminalCommandParser.parseSizeBytes("invalid"))
        assertNull(TerminalCommandParser.parseSizeBytes("-5gb"))
    }

    @Test
    fun `fit verdict evaluates green yellow and red thresholds against total ram and checks free storage`() {
        val sixGbRam = 6L * 1024L * 1024L * 1024L
        val plentyStorage = 20L * 1024L * 1024L * 1024L

        // 1.5 GB model on 6 GB RAM = 25% (<= 40%) -> GREEN
        val greenVerdict = RamFitChecker.evaluateInstallFit(
            modelSizeBytes = (1.5 * 1024 * 1024 * 1024).toLong(),
            totalRamBytes = sixGbRam,
            freeStorageBytes = plentyStorage
        )
        assertEquals(FitLevel.OPTIMAL, greenVerdict.level)
        assertEquals("GREEN", greenVerdict.badge)
        assertTrue(greenVerdict.hasEnoughStorage)
        assertNull(greenVerdict.refusalReason)

        // 3.0 GB model on 6 GB RAM = 50% (<= 60%) -> YELLOW
        val yellowVerdict = RamFitChecker.evaluateInstallFit(
            modelSizeBytes = 3L * 1024L * 1024L * 1024L,
            totalRamBytes = sixGbRam,
            freeStorageBytes = plentyStorage
        )
        assertEquals(FitLevel.TIGHT, yellowVerdict.level)
        assertEquals("YELLOW", yellowVerdict.badge)
        assertTrue(yellowVerdict.hasEnoughStorage)
        assertNull(yellowVerdict.refusalReason)

        // 4.5 GB model on 6 GB RAM = 75% (> 60%) -> RED with refusal message
        val redVerdict = RamFitChecker.evaluateInstallFit(
            modelSizeBytes = (4.5 * 1024 * 1024 * 1024).toLong(),
            totalRamBytes = sixGbRam,
            freeStorageBytes = plentyStorage
        )
        assertEquals(FitLevel.CRITICAL, redVerdict.level)
        assertEquals("RED", redVerdict.badge)
        assertNotNull(redVerdict.refusalReason)
        assertTrue(redVerdict.refusalReason!!.contains("This model is too big for your phone"))

        // Insufficient free storage (< model size + 500 MB) -> refused
        val model1Gb = 1L * 1024L * 1024L * 1024L
        val lowStorage = model1Gb + (200L * 1024L * 1024L) // Only +200 MB free instead of +500 MB
        val lowStorageVerdict = RamFitChecker.evaluateInstallFit(
            modelSizeBytes = model1Gb,
            totalRamBytes = sixGbRam,
            freeStorageBytes = lowStorage
        )
        assertFalse(lowStorageVerdict.hasEnoughStorage)
        assertNotNull(lowStorageVerdict.refusalReason)
        assertTrue(lowStorageVerdict.refusalReason!!.contains("Not enough free storage"))
    }

    @Test
    fun `validateDownloadTarget enforces litertlm extension safe filename and huggingface https url`() {
        assertTrue(
            TerminalCommandParser.validateDownloadTarget(
                fileName = "Qwen3-0.6B.litertlm",
                url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm"
            ).isSuccess
        )

        assertTrue(
            TerminalCommandParser.validateDownloadTarget(
                fileName = "model.gguf",
                url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/model.gguf"
            ).isFailure
        )

        assertTrue(
            TerminalCommandParser.validateDownloadTarget(
                fileName = "../evil.litertlm",
                url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/evil.litertlm"
            ).isFailure
        )

        assertTrue(
            TerminalCommandParser.validateDownloadTarget(
                fileName = "Qwen3-0.6B.litertlm",
                url = "http://evil.example.com/Qwen3-0.6B.litertlm"
            ).isFailure
        )
    }
}
