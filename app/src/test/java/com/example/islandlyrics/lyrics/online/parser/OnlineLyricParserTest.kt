package com.example.islandlyrics.lyrics.online.parser

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.SyllableInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineLyricParserTest {

    @Test
    fun testHasQqLineSegments_validPatterns() {
        val sampleQqLrc = """
            [ti:七月七日晴]
            [ar:许慧欣]
            [0,3946]七月七日晴 忽上忽下
            [3946,4200]天气的变化
        """.trimIndent()

        assertTrue(OnlineLyricParser.hasQqLineSegments(sampleQqLrc))
        assertTrue(OnlineLyricParser.hasQqLineSegments("[1234,5678]歌词内容"))
        assertTrue(OnlineLyricParser.hasQqLineSegments("[100,200]一段[300,400]二段"))
    }

    @Test
    fun testHasQqLineSegments_invalidPatterns() {
        val standardLrc = """
            [ti:七月七日晴]
            [ar:许慧欣]
            [00:12.34]标准LRC歌词
            [01:05.67]第二行歌词
        """.trimIndent()

        assertFalse(OnlineLyricParser.hasQqLineSegments(standardLrc))
        assertFalse(OnlineLyricParser.hasQqLineSegments("纯文本无标签"))
        assertFalse(OnlineLyricParser.hasQqLineSegments(""))
    }

    @Test
    fun testParseLrcLyrics_withQqSegments() {
        val sampleQqLrc = """
            [0,3000]七月七日晴
            [3000,4000]忽上忽下
        """.trimIndent()

        val parsed = OnlineLyricParser.parseLrcLyrics(sampleQqLrc)
        assertEquals(2, parsed.size)
        assertEquals(0L, parsed[0].startTime)
        assertEquals("七月七日晴", parsed[0].text)
        assertEquals(3000L, parsed[1].startTime)
        assertEquals("忽上忽下", parsed[1].text)
    }

    @Test
    fun testParseLrcLyrics_withMultiSegmentsInSingleLine() {
        val line = "[0,2000]第一段[2000,3000]第二段"
        val parsed = OnlineLyricParser.parseLrcLyrics(line)
        assertEquals(2, parsed.size)
        assertEquals(0L, parsed[0].startTime)
        assertEquals("第一段", parsed[0].text)
        assertEquals(2000L, parsed[1].startTime)
        assertEquals("第二段", parsed[1].text)
    }

    @Test
    fun parseEnhancedLrcUsesTrailingTimestampAsWordAndLineEnd() {
        val line = OnlineLyricParser.parseLrcLyrics("[00:01.000]<00:01.000>Hello<00:02.000>").single()

        assertEquals("Hello", line.text)
        assertEquals(1_000L, line.startTime)
        assertEquals(2_000L, line.endTime)
        assertEquals(listOf(SyllableInfo(1_000, 2_000, "Hello")), line.syllables)
    }

    @Test
    fun parseEnhancedLrcPreservesAbsoluteTimingSpacesAndPrecision() {
        val line = OnlineLyricParser.parseLrcLyrics(
            "[00:01.000]<00:01.25>Hello <00:02.345>world!<00:03.50>"
        ).single()

        assertEquals("Hello world!", line.text)
        assertEquals(3_500L, line.endTime)
        assertEquals(listOf(SyllableInfo(1_250, 2_345, "Hello "), SyllableInfo(2_345, 3_500, "world!")), line.syllables)
    }

    @Test
    fun parseEnhancedLrcWithoutFinalTimestampUsesNextLineBoundary() {
        val lines = OnlineLyricParser.parseLrcLyrics(
            "[00:01.000]<00:01.000>Hello <00:02.000>world\n[00:04.000]Next"
        )

        assertEquals(listOf("Hello world", "Next"), lines.map { it.text })
        assertEquals(4_000L, lines.first().endTime)
        assertEquals(SyllableInfo(2_000, 4_000, "world"), lines.first().syllables!!.last())
        assertTrue(lines.last().syllables.isNullOrEmpty())
    }

    @Test
    fun parseEnhancedLrcOverlappingTailFallsBackAtNextLine() {
        val lines = OnlineLyricParser.parseLrcLyrics(
            "[00:01.000]<00:01.000>Hello <00:02.000>world!<00:05.000>\n" +
                "[00:03.000]<00:03.000>Next<00:04.000>"
        )

        assertEquals(listOf("Hello world!", "Next"), lines.map { it.text })
        assertEquals(listOf(3_000L, 4_000L), lines.map { it.endTime })
        assertTrue(lines.first().syllables.isNullOrEmpty())
        assertEquals(listOf(SyllableInfo(3_000, 4_000, "Next")), lines.last().syllables)
    }

    @Test
    fun parseEnhancedLrcEndingAtNextLineKeepsWordTiming() {
        val line = OnlineLyricParser.parseLrcLyrics(
            "[00:01.000]<00:01.000>Hello<00:03.000>\n[00:03.000]Next"
        ).first()

        assertEquals(3_000L, line.endTime)
        assertEquals(listOf(SyllableInfo(1_000, 3_000, "Hello")), line.syllables)
    }

    @Test
    fun parseEnhancedLrcFinalLineKeepsLastWordInsideFallbackBoundary() {
        val line = OnlineLyricParser.parseLrcLyrics("[00:01.000]<00:01.000>Hello <00:08.000>world").single()

        assertEquals(13_000L, line.endTime)
        assertEquals(SyllableInfo(8_000, 13_000, "world"), line.syllables!!.last())
    }

    @Test
    fun parseEnhancedLrcPreservesUntimedPrefixAndLiteralAngleBrackets() {
        val line = OnlineLyricParser.parseLrcLyrics("[00:01.00]Say <00:02.00>I <3 you!<00:03.00>").single()

        assertEquals("Say I <3 you!", line.text)
        assertEquals(listOf(SyllableInfo(1_000, 2_000, "Say "), SyllableInfo(2_000, 3_000, "I <3 you!")), line.syllables)
    }

    @Test
    fun parseEnhancedLrcInvalidTimingFallsBackToCleanLine() {
        for (content in listOf(
            "[00:01.000]<00:02.000>Hello<00:01.500>",
            "[00:02.000]<00:01.000>Hello<00:03.000>",
            "[00:01.000]<00:01.000>Hello<00:01.000>"
        )) {
            val line = OnlineLyricParser.parseLrcLyrics(content).single()
            assertEquals(content, "Hello", line.text)
            assertTrue(content, line.syllables.isNullOrEmpty())
            assertEquals(content, line.startTime + 5_000, line.endTime)
        }
    }

    @Test
    fun parseOrdinaryLrcPreservesRepeatedTimestampsAndNonTimestampAngleText() {
        val lines = OnlineLyricParser.parseLrcLyrics("[00:01.00][00:03.00]I <3 <chorus> <0,500>")

        assertEquals(listOf(1_000L, 3_000L), lines.map { it.startTime })
        assertEquals(listOf(3_000L, 8_000L), lines.map { it.endTime })
        assertEquals(listOf("I <3 <chorus> <0,500>", "I <3 <chorus> <0,500>"), lines.map { it.text })
        assertTrue(lines.all { it.syllables.isNullOrEmpty() })
    }

    @Test
    fun testIsWordLevelLyrics() {
        val krcSample = "[100,500]<0,200,0>七<200,300,0>月"
        assertTrue(OnlineLyricParser.isWordLevelLyrics(krcSample))

        val plainLrc = "[00:12.34]普通歌词"
        assertFalse(OnlineLyricParser.isWordLevelLyrics(plainLrc))
    }

    @Test
    fun parseTtmlLyrics_doesNotInsertSpacesBetweenAdjacentCjkSpans() {
        val ttml = """
            <tt><body><div>
                <p begin="0s" end="2s">
                    <span begin="0s" end="0.2s">太</span>
                    <span begin="0.2s" end="0.4s">擁</span>
                    <span begin="0.4s" end="0.6s">擠</span>
                    <span begin="0.6s" end="0.8s">就</span>
                    <span begin="0.8s" end="1s">開</span>
                    <span begin="1s" end="1.2s">到</span>
                    <span begin="1.2s" end="1.4s">了</span>
                    <span begin="1.4s" end="1.6s">別</span>
                    <span begin="1.6s" end="1.8s">的</span>
                    <span begin="1.8s" end="2s">土壤</span>
                </p>
            </div></body></tt>
        """.trimIndent()

        val parsed = OnlineLyricParser.parseTtmlLyrics(ttml)

        assertEquals(1, parsed.size)
        assertEquals("太擁擠就開到了別的土壤", parsed.single().text)
    }

    @Test
    fun parseTtmlLyricsPreservesExplicitWhitespaceSpan() {
        val ttml = """
            <tt><body><div><p begin="0s" end="1s">
                <span begin="0s" end="0.3s">Hello</span>
                <span begin="0.3s" end="0.4s"> </span>
                <span begin="0.4s" end="1s">world</span>
            </p></div></body></tt>
        """.trimIndent()

        val parsed = OnlineLyricParser.parseTtmlLyrics(ttml)

        assertEquals("Hello world", parsed.single().text)
    }
}
