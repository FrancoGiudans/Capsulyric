package com.example.islandlyrics.ui.overlay.superisland.calibration

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import org.junit.Assert.*
import org.junit.Test

class CalibratedLyricWindowResolverTest {
    private val policy = RightTextWidthPolicy(RightTextCalibration(capacity = 3f)) { it.length * 0.5f }
    private val text = "一二三四五六七八九十"

    private fun resolve(resolver: CalibratedLyricWindowResolver, now: Long, text: String = this.text,
        line: OnlineLyricFetcher.LyricLine? = null, position: Long = 0L, disabled: Boolean = false,
        syllables: Boolean = false, left: Int? = null) = resolver.resolve(
        text = text, policy = policy, leftLimit = left, disabled = disabled, line = line,
        syllablesEnabled = syllables, centered = true, position = position, now = now,
        shiftDelay = 100L, initialPause = 400L, finalPause = 300L
    )

    @Test fun disabledScrollingKeepsPrefixAndDoesNotAnimate() {
        val r = resolve(CalibratedLyricWindowResolver(), 0, disabled = true)
        assertEquals("一二三", r.text)
        assertTrue(r.done)
    }

    @Test fun adaptiveScrollKeepsInitialPauseReachesTailAndResetsForNextLine() {
        val resolver = CalibratedLyricWindowResolver()
        assertEquals("一二三", resolve(resolver, 0).text)
        assertEquals("一二三", resolve(resolver, 399).text)
        assertEquals("二三四", resolve(resolver, 400).text)
        for (time in 500L..1_000L step 100L) resolve(resolver, time)
        assertEquals("八九十", resolve(resolver, 1_100).text)
        assertTrue(resolve(resolver, 1_400).done)
        assertEquals("甲乙丙", resolve(resolver, 1_500, text = "甲乙丙丁戊己庚辛").text)
    }

    @Test fun lineProgressEndsOnSuffixIncludingSpacesAndPunctuation() {
        val text = "一 二，三 四。五 六！"
        val line = OnlineLyricFetcher.LyricLine(0L, 1_000L, text)
        val resolver = CalibratedLyricWindowResolver()
        val windows = (0L..1_000L step 50).map { resolve(resolver, it, text, line, it).text }
        assertTrue(windows.last().endsWith("六！"))
        assertTrue(windows.all { policy.fits(it) })
        assertTrue(windows.any { ' ' in it })
    }

    @Test fun syllableWindowShowsLastSyllableWithoutDroppingWhitespaceDuringMeasurement() {
        val text = "一 二三四 五六七八"
        val units = text.filterNot(Char::isWhitespace).mapIndexed { index, char ->
            OnlineLyricFetcher.SyllableInfo(index * 100L, (index + 1) * 100L, char.toString())
        }
        val line = OnlineLyricFetcher.LyricLine(0, 800, text, units)
        val resolver = CalibratedLyricWindowResolver()
        val last = resolve(resolver, 750, text, line, 750, syllables = true)
        assertTrue(last.text.endsWith("八"))
        assertTrue(policy.fits(last.text))
    }

    @Test fun fullLyricModeRetainsItsSeparateLeftBudget() {
        val resolver = CalibratedLyricWindowResolver()
        val line = OnlineLyricFetcher.LyricLine(0, 1_000, text)
        val last = resolve(resolver, 1_000, text, line, 1_000, left = 4)
        assertEquals("六七八九十", last.text)
    }

    @Test fun finalLrcWindowAppearsBeforeLineEndRatherThanAtAnUnreachableEndpoint() {
        val line = OnlineLyricFetcher.LyricLine(0, 1_000, text)
        val result = resolve(CalibratedLyricWindowResolver(), 800, line = line, position = 800)
        assertEquals("八九十", result.text)
    }

    @Test fun lastMultiCharacterSyllablePinsTheFinalSuffix() {
        val line = OnlineLyricFetcher.LyricLine(0, 1_000, text, listOf(
            OnlineLyricFetcher.SyllableInfo(0, 800, "一二三四五六七"),
            OnlineLyricFetcher.SyllableInfo(800, 1_000, "八九十")
        ))
        val result = resolve(CalibratedLyricWindowResolver(), 850, line = line, position = 850, syllables = true)
        assertEquals("八九十", result.text)
    }
}
