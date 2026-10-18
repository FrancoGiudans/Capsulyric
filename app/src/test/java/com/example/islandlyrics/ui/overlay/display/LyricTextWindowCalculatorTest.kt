package com.example.islandlyrics.ui.overlay.display

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandLyricLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.BreakIterator
import java.util.Locale

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

    @Test
    fun fullLyricWindow_movesFromStartToTailUsingBothColumns() {
        val text = "一二三四五六七八九十甲乙丙丁戊己庚辛壬癸"
        val units = charSyllables(text)

        val start = center(text, units, position = 0, width = 26, leftColumnWeight = 12)
        val middle = center(text, units, position = 1100, width = 26, leftColumnWeight = 12)
        val tail = center(text, units, position = 1900, width = 26, leftColumnWeight = 12)

        assertEquals(text.take(13), start)
        assertFalse(start == middle)
        assertEquals(text.takeLast(13), tail)
        assertEquals(start, center(text, units, position = 0, width = 26, leftColumnWeight = 12))
        assertEquals(tail, center(text, units, position = 1900, width = 26, leftColumnWeight = 12))
    }

    @Test
    fun fullLyricWindow_keepsTailWhenSummedHalfCharacterBudgetsCannotFitWholeLine() {
        val text = "一二三四五六七八九十甲乙"
        val units = charSyllables(text)

        assertEquals(text.take(11), center(text, units, position = 0, width = 24, leftColumnWeight = 11))
        assertEquals(text.takeLast(11), center(text, units, position = 1100, width = 24, leftColumnWeight = 11))
    }

    @Test
    fun fullLyricWindow_fitsBothColumnsWithoutLosingOrSplittingCharacters() {
        val texts = listOf(
            "一二三四五六七八九十甲乙丙丁戊己庚辛壬癸",
            "ab一二三四五六七八九十甲乙丙丁戊己Z",
            "we make changes and keep moving forward",
            "一二😀三四五e\u0301六七八😀九十甲乙丙丁戊己",
            "😀"
        )
        val budgets = listOf(8 to 12, 9 to 13, 11 to 13, 12 to 14, 16 to 14)
        for (text in texts) {
            val units = text.codePoints().toArray().mapIndexed { index, codePoint ->
                syllable(index * 100L, String(Character.toChars(codePoint)))
            }
            for ((leftWeight, rightWeight) in budgets) {
                for (index in units.indices) {
                    val window = center(
                        text, units, index * 100L, leftWeight + rightWeight, leftColumnWeight = leftWeight
                    )
                    val split = SuperIslandLyricLayout.splitFullLyric(
                        window, showLeftCover = true, leftMaxWeight = leftWeight, rightMaxWeight = rightWeight
                    )
                    val message = "text=$text left=$leftWeight right=$rightWeight position=$index window=$window split=$split"
                    assertEquals(
                        message,
                        window.filterNot(Char::isWhitespace),
                        (split.left + split.right).filterNot(Char::isWhitespace)
                    )
                    assertTrue(message, LyricTextWindowCalculator.weight(split.left) <= leftWeight)
                    assertTrue(message, LyricTextWindowCalculator.weight(split.right) <= rightWeight)
                    val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(window) }
                    assertTrue(message, boundaries.isBoundary(split.left.length))
                    assertFalse(message, window.isNotEmpty() && Character.isLowSurrogate(window.first()))
                    assertFalse(message, window.isNotEmpty() && Character.isHighSurrogate(window.last()))
                    if (index == units.lastIndex) {
                        assertTrue(message, window.endsWith(units.last().text))
                    }
                }
            }
        }
    }

    private fun center(
        text: String,
        units: List<OnlineLyricFetcher.SyllableInfo>,
        position: Long,
        width: Int,
        leftColumnWeight: Int? = null
    ): String = checkNotNull(
        LyricTextWindowCalculator.visualCenterSyllableWindow(text, units, position, width, leftColumnWeight)
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
