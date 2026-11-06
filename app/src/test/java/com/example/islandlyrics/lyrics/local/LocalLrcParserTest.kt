package com.example.islandlyrics.lyrics.local

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.SyllableInfo
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.ui.overlay.display.ParsedLyricDisplayState
import com.example.islandlyrics.ui.overlay.model.WordProgressCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLrcParserTest {
    @Test
    fun enhancedLrcProducesCleanTextAndWordTiming() {
        val result = LocalLrcParser.parseContent("[00:01.000]<00:01.000>Hello<00:02.000>")!!

        assertTrue(result.hasSyllable)
        assertEquals("Hello", result.lines.single().text)
        assertEquals(2_000L, result.lines.single().endTime)
        assertEquals(listOf(SyllableInfo(1_000, 2_000, "Hello")), result.lines.single().syllables)
    }

    @Test
    fun enhancedLrcFeedsExistingCurrentLineAndWordProgress() {
        val result = LocalLrcParser.parseContent(
            "[00:01.000]<00:01.000>Hello<00:02.000>\n[00:03.000]Next"
        )!!
        val state = ParsedLyricDisplayState.from(LyricRepository.ParsedLyricsInfo(
            lines = result.lines,
            hasSyllable = result.hasSyllable,
            timelineCapability = LyricRepository.TimelineCapability.MULTI_LINE
        ))

        assertTrue(state.useSyllableScrolling)
        assertNull(state.currentLine(999))
        val current = state.currentLine(1_500)!!
        assertEquals("Hello", current.text)
        val progress = WordProgressCalculator.compute(
            current.text, current.startTime, current.endTime, current.syllables, 1_500
        )!!.syllables.single()
        assertEquals("Hello", progress.text)
        assertEquals(0, progress.charStart)
        assertEquals(5, progress.charEnd)
        assertEquals(0.5f, progress.progress, 0.001f)
        assertNull(state.currentLine(2_000))
        assertEquals("Next", state.currentLine(3_000)!!.text)
    }

    @Test
    fun mixedPlainAndEnhancedLrcKeepsAllLinesAndOriginalOrder() {
        val result = LocalLrcParser.parseContent(
            "[ti:Example]\n[00:03.000]After\n[0:01.00]Before\n[00:02.000]<0:02.00>Word<00:02.50>"
        )!!

        assertEquals(listOf("Before", "Word", "After"), result.lines.map { it.text })
        assertEquals(listOf(1_000L, 2_000L, 3_000L), result.lines.map { it.startTime })
        assertTrue(result.hasSyllable)
        assertTrue(result.lines.first().syllables.isNullOrEmpty())
        assertEquals(listOf(SyllableInfo(2_000, 2_500, "Word")), result.lines[1].syllables)
        assertTrue(result.lines.last().syllables.isNullOrEmpty())
    }

    @Test
    fun plainLrcRetainsRepeatedTimestampsAndLiteralAngleText() {
        val result = LocalLrcParser.parseContent("[00:01.00][00:03.00]I <3 <chorus>")!!

        assertFalse(result.hasSyllable)
        assertEquals(listOf("I <3 <chorus>", "I <3 <chorus>"), result.lines.map { it.text })
        assertEquals(listOf(1_000L, 3_000L), result.lines.map { it.startTime })
        assertEquals(listOf(3_000L, 8_000L), result.lines.map { it.endTime })
    }

    @Test
    fun timingTagsWithoutTextDoNotBecomeLyrics() {
        assertNull(LocalLrcParser.parseContent("[00:01.000]<00:01.000><00:02.000>"))
        val result = LocalLrcParser.parseContent("[00:01.000]<00:01.000><00:02.000>\n[00:03.000]Text")!!
        assertEquals(listOf("Text"), result.lines.map { it.text })
        assertFalse(result.hasSyllable)
    }

    @Test
    fun esAndQqWordTimingKeepTheirRelativeOffsets() {
        for (header in listOf("[00:01.000]", "[1000,1000]")) {
            val result = LocalLrcParser.parseContent("${header}<0,500>你<500,500>好")!!
            assertTrue(header, result.hasSyllable)
            assertEquals(header, "你好", result.lines.single().text)
            assertEquals(header, listOf(SyllableInfo(1_000, 1_500, "你"), SyllableInfo(1_500, 2_000, "好")), result.lines.single().syllables)
        }
    }

    @Test
    fun qqLineSegmentsKeepTheirTextAndTiming() {
        val result = LocalLrcParser.parseContent("[1000,500]Hi[2000,1000]Again")!!

        assertFalse(result.hasSyllable)
        assertEquals(listOf("Hi", "Again"), result.lines.map { it.text })
        assertEquals(listOf(1_000L, 2_000L), result.lines.map { it.startTime })
    }

    @Test
    fun whitespaceWordKeepsCharacterOffsetsAlignedWithLineText() {
        val line = LocalLrcParser.parseContent(
            "[00:01.000]<00:01.00>Hello<00:01.50> <00:01.60>world<00:02.00>"
        )!!.lines.single()

        assertEquals("Hello world", line.text)
        val syllables = line.syllables!!
        assertEquals(listOf("Hello", " ", "world"), syllables.map { it.text })
        assertEquals(line.text, syllables.joinToString("") { it.text })
    }

    @Test
    fun invalidWordTimingFallsBackToLineTextWithoutWordMode() {
        val result = LocalLrcParser.parseContent("[00:01.000]<00:02.000>Hello<00:01.500>")!!

        assertEquals("Hello", result.lines.single().text)
        assertFalse(result.hasSyllable)
        assertEquals(6_000L, result.lines.single().endTime)
    }
}
