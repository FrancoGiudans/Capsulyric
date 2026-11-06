package com.example.islandlyrics.lyrics.cache

import com.example.islandlyrics.lyrics.importer.LyricifyDatabaseImporter
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.SyllableInfo
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.ui.overlay.display.ParsedLyricDisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineLyricCacheRecoveryTest {
    private val body = "<00:01.000>Hello<00:02.000>"
    private val lyrics = "[00:01.000]$body"

    @Test
    fun legacyInlineTagsBecomeTextAndWordTiming() {
        val restored = restoreCachedEnhancedLrcLines(lyrics, listOf(LyricLine(1_000, 6_000, body)))

        assertEquals(listOf(LyricLine(1_000, 2_000, "Hello", listOf(SyllableInfo(1_000, 2_000, "Hello")))), restored)
    }

    @Test
    fun importedOffsetAndPerLineSidecarsSurviveRecovery() {
        val cached = LyricLine(2_500, 7_500, body, translation = "你好", roma = "ni hao")
        val restored = restoreCachedEnhancedLrcLines("[0:01]$body", listOf(cached)).single()

        assertEquals(2_500L, restored.startTime)
        assertEquals(3_500L, restored.endTime)
        assertEquals(listOf(SyllableInfo(2_500, 3_500, "Hello")), restored.syllables)
        assertEquals("你好", restored.translation)
        assertEquals("ni hao", restored.roma)
    }

    @Test
    fun negativeImportOffsetKeepsClampedLineAndWordTimes() {
        val first = "<00:00.100>A<00:01.000>"
        val second = "<00:02.000>B<00:03.000>"
        val restored = restoreCachedEnhancedLrcLines(
            "[00:00.100]$first\n[00:02.000]$second",
            listOf(LyricLine(0, 1_500, first), LyricLine(1_500, 6_500, second))
        )

        assertEquals(listOf(0L, 1_500L), restored.map { it.startTime })
        assertEquals(listOf(500L, 2_500L), restored.map { it.endTime })
        assertEquals(listOf(SyllableInfo(0, 500, "A")), restored.first().syllables)
        assertEquals(listOf(SyllableInfo(1_500, 2_500, "B")), restored.last().syllables)
    }

    @Test
    fun singleClampedStartRecoversOffsetFromLegacyEnd() {
        val text = "<00:00.100>A<00:01.100>"
        val restored = restoreCachedEnhancedLrcLines(
            "[00:00.100]$text", listOf(LyricLine(0, 4_900, text))
        ).single()

        assertEquals(0L, restored.startTime)
        assertEquals(900L, restored.endTime)
        assertEquals(listOf(SyllableInfo(0, 900, "A")), restored.syllables)
    }

    @Test
    fun mixedOrdinaryRowsAndBlankBoundariesAreRetained() {
        val restored = restoreCachedEnhancedLrcLines(
            "[00:00.000]\n$lyrics\n[00:03.000]Next",
            listOf(LyricLine(0, 1_000, ""), LyricLine(1_000, 3_000, body), LyricLine(3_000, 8_000, "Next"))
        )

        assertEquals(listOf("", "Hello", "Next"), restored.map { it.text })
        assertEquals(listOf(1_000L, 2_000L, 8_000L), restored.map { it.endTime })
        assertNull(restored.last().syllables)
    }

    @Test
    fun cachedOverlapFallsBackAtNextLineWithoutResettingOffset() {
        val cached = listOf(
            LyricLine(2_500, 6_500, "First", listOf(SyllableInfo(2_500, 6_500, "First")), "第一行", "first"),
            LyricLine(4_500, 5_500, "Second", listOf(SyllableInfo(4_500, 5_500, "Second")))
        )
        val restored = restoreCachedEnhancedLrcLines(
            "[00:01.000]<00:01.000>First<00:05.000>\n[00:03.000]<00:03.000>Second<00:04.000>", cached
        )

        assertEquals(cached.first().copy(endTime = 4_500, syllables = null), restored.first())
        assertEquals(cached.last(), restored.last())
        val state = ParsedLyricDisplayState.from(LyricRepository.ParsedLyricsInfo(
            lines = restored, hasSyllable = true, timelineCapability = LyricRepository.TimelineCapability.MULTI_LINE
        ))
        assertEquals("Second", state.currentLine(5_000)!!.text)
    }

    @Test
    fun unalignableRawSourceKeepsCachedTextClockAndSidecars() {
        val cached = LyricLine(9_000, 10_000, "<00:01.000>Edited<00:02.000>", translation = "译文", roma = "roma")

        assertEquals(cached.copy(text = "Edited"), restoreCachedEnhancedLrcLines(lyrics, listOf(cached)).single())
    }

    @Test
    fun otherFormatsKeepTheirExistingTiming() {
        val lines = listOf(LyricLine(1_000, 2_000, "你好", listOf(SyllableInfo(1_000, 2_000, "你好"))))

        assertSame(lines, restoreCachedEnhancedLrcLines("[1000,1000]<0,1000,0>你好", lines))
        assertSame(lines, restoreCachedEnhancedLrcLines(null, lines))
    }

    @Test
    fun recoveredLinesAreStableOnLaterReads() {
        val restored = restoreCachedEnhancedLrcLines(lyrics, listOf(LyricLine(1_000, 6_000, body)))

        assertEquals(restored, restoreCachedEnhancedLrcLines(lyrics, restored))
        assertTrue(restored.any { !it.syllables.isNullOrEmpty() })
    }

    @Test
    fun sourceBeyondImportBudgetKeepsReadableCachedContent() {
        val oversized = lyrics + "x".repeat(LyricifyDatabaseImporter.MAX_PAYLOAD_BYTES.toInt())
        val cached = LyricLine(1_000, 6_000, body, translation = "译文")

        assertEquals(cached.copy(text = "Hello"), restoreCachedEnhancedLrcLines(oversized, listOf(cached)).single())
    }
}
