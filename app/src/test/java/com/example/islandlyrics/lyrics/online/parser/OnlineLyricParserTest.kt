package com.example.islandlyrics.lyrics.online.parser

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
    fun testIsWordLevelLyrics() {
        val krcSample = "[100,500]<0,200,0>七<200,300,0>月"
        assertTrue(OnlineLyricParser.isWordLevelLyrics(krcSample))

        val plainLrc = "[00:12.34]普通歌词"
        assertFalse(OnlineLyricParser.isWordLevelLyrics(plainLrc))
    }
}
