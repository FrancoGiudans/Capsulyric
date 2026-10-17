package com.example.islandlyrics.ui.overlay.display

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricTextWindowCalculatorTest {
    @Test
    fun visualCenterSyllableWindow_keepsShortLyricsWhole() {
        val text = "短歌词"

        val result = LyricTextWindowCalculator.visualCenterSyllableWindow(
            fullText = text,
            syllables = listOf(syllable(0, "短")),
            position = 0,
            maxDisplayWeight = 10
        )

        assertEquals(text, result)
    }

    @Test
    fun visualCenterSyllableWindow_pinsStartAndEndAndCentersMiddle() {
        val text = "一二三四五六七八九十"
        val units = charSyllables(text)

        assertEquals("一二三四五", center(text, units, position = 0, width = 10))
        assertEquals("五六七八九", center(text, units, position = 600, width = 10))
        assertEquals("六七八九十", center(text, units, position = 900, width = 10))
    }

    @Test
    fun visualCenterSyllableWindow_pinsCjkTailAtHalfCharacterLimits() {
        val text = "一二三四五六七八九十"
        val units = charSyllables(text)

        assertEquals("六七八九十", center(text, units, position = 900, width = 11))
        assertEquals("五六七八九十", center(text, units, position = 900, width = 13))
        assertEquals("四五六七八九十", center(text, units, position = 900, width = 15))
    }

    @Test
    fun visualCenterSyllableWindow_keepsMixedLanguageLastSyllableVisible() {
        val text = "一二三四五六七八九十A"
        val units = charSyllables(text)

        assertEquals("七八九十A", center(text, units, position = 1000, width = 10))
        assertEquals("四五六七八九十A", center(text, units, position = 1000, width = 16))
    }

    @Test
    fun visualCenterSyllableWindow_tailFitsEverySupportedCharacterBudget() {
        for (text in listOf("一二三四五六七八九十", "一二三四五六七八九十A", "abc一二三四五")) {
            val units = charSyllables(text)
            for (width in 2..LyricTextWindowCalculator.weight(text)) {
                val result = center(text, units, position = (units.size - 1) * 100L, width = width)
                assertTrue("Missing tail: text=$text width=$width result=$result", result.endsWith(text.last()))
                assertTrue("Window exceeds budget: width=$width result=$result", LyricTextWindowCalculator.weight(result) <= width)
            }
        }
    }

    @Test
    fun visualCenterSyllableWindow_centersCurrentEnglishWordAsAWhole() {
        val result = center(
            text = "we make changes",
            units = listOf(syllable(0, "we"), syllable(100, "make"), syllable(200, "changes")),
            position = 200,
            width = 8
        )

        assertEquals("e changes", result)
    }

    @Test
    fun visualCenterSyllableWindow_alignsWhitespaceDifferencesInOrder() {
        val result = center(
            text = "甲 乙丙丁戊己庚",
            units = listOf(
                syllable(0, "甲"),
                syllable(100, "乙"),
                syllable(200, "丙"),
                syllable(300, "丁"),
                syllable(400, "戊"),
                syllable(500, "己"),
                syllable(600, "庚")
            ),
            position = 200,
            width = 6
        )

        assertEquals("乙丙丁", result)
    }

    @Test
    fun visualCenterSyllableWindow_usesTheCorrectOccurrenceOfRepeatedWords() {
        val result = center(
            text = "start go xx go yy go end",
            units = listOf("start", "go", "xx", "go", "yy", "go", "end")
                .mapIndexed { index, unit -> syllable(index * 100L, unit) },
            position = 500,
            width = 6
        )

        assertEquals("yy go en", result)
    }

    @Test
    fun visualCenterSyllableWindow_rejectsMismatchedTimeline() {
        val result = LyricTextWindowCalculator.visualCenterSyllableWindow(
            fullText = "这是没有对应时间轴的长歌词",
            syllables = listOf(syllable(0, "错误")),
            position = 0,
            maxDisplayWeight = 6
        )

        assertNull(result)
    }

    @Test
    fun visualCenterSyllableWindow_keepsUnicodeCharactersIntact() {
        val text = "ab😀cd😀ef"
        val result = center(
            text = text,
            units = listOf("a", "b", "😀", "c", "d", "😀", "e", "f")
                .mapIndexed { index, unit -> syllable(index * 100L, unit) },
            position = 500,
            width = 4
        )

        assertEquals("d😀e", result)
        assertFalse(result.isNotEmpty() && Character.isHighSurrogate(result.last()))
    }

    @Test
    fun visualCenterSyllableWindow_holdsLastStartedSyllableDuringPause() {
        val text = "一二三四五六七八九十"
        val units = charSyllables(text)

        assertEquals(
            center(text, units, position = 650, width = 10),
            center(text, units, position = 699, width = 10)
        )
    }

    private fun center(
        text: String,
        units: List<OnlineLyricFetcher.SyllableInfo>,
        position: Long,
        width: Int
    ): String = checkNotNull(
        LyricTextWindowCalculator.visualCenterSyllableWindow(text, units, position, width)
    )

    private fun charSyllables(text: String): List<OnlineLyricFetcher.SyllableInfo> =
        text.mapIndexed { index, char -> syllable(index * 100L, char.toString()) }

    private fun syllable(startTime: Long, text: String) =
        OnlineLyricFetcher.SyllableInfo(
            startTime = startTime,
            endTime = startTime + 100L,
            text = text
        )
}
