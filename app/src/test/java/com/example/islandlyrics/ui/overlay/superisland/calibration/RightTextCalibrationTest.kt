package com.example.islandlyrics.ui.overlay.superisland.calibration

import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandLyricLayout
import org.junit.Assert.*
import org.junit.Test

class RightTextCalibrationTest {
    private fun policy(c: RightTextCalibration = RightTextCalibration(capacity = 4f)) =
        RightTextWidthPolicy(c) { text -> Glyphs.parts(text).sumOf { glyph ->
            when (glyph) { "i", "1" -> 0.2; "W", "8" -> 0.8; else -> 0.5 }
        }.toFloat() }

    @Test fun cjkUsesOneUnitAndWesternWidthsAreNotAssumedEqual() {
        val p = policy()
        assertEquals(4f, p.width("中日한あ"), 0.0001f)
        assertTrue(p.width("iiii") < p.width("WWWW"))
        assertTrue(p.width("1111") < p.width("8888"))
    }

    @Test fun lettersAndDigitsCanBeCalibratedIndependently() {
        val p = policy(RightTextCalibration(letterMonospace = true, letterWidth = 0.6f))
        assertEquals(p.width("iiii"), p.width("WWWW"), 0.0001f)
        assertTrue(p.width("1111") < p.width("8888"))
        val digits = policy(RightTextCalibration(digitMonospace = true, digitWidth = 0.7f))
        assertEquals(digits.width("1111"), digits.width("8888"), 0.0001f)
        assertTrue(digits.width("iiii") < digits.width("WWWW"))
    }

    @Test fun spacesAndPunctuationConsumeCapacityWithoutChangingTheSource() {
        val p = policy(RightTextCalibration(capacity = 3.5f, spaceWidth = 0.5f))
        assertEquals(3f, p.width("一 二，"), 0.0001f)
        assertEquals("一 二，", p.prefix("一 二，三"))
        assertEquals(1f, p.width("\u3000"), 0.0001f)
        assertEquals(1f, p.width("  "), 0.0001f)
    }

    @Test fun overridesWorkForCustomFontsAndWholeEmoji() {
        val p = policy(RightTextCalibration(capacity = 2f, overrides = mapOf("W" to 1.5f, "👨‍👩‍👧‍👦" to 2f)))
        assertEquals(1.5f, p.width("W"), 0.0001f)
        assertEquals("👨‍👩‍👧‍👦", p.prefix("👨‍👩‍👧‍👦一"))
        assertEquals("e\u0301", policy(RightTextCalibration(capacity = 1f)).prefix("e\u0301一"))
    }

    @Test fun marginAndPaddingUseTheSameCapacity() {
        val p = policy(RightTextCalibration(capacity = 4f, margin = 0.5f, spaceWidth = 0.5f))
        assertEquals("一二三", p.prefix("一二三四"))
        assertEquals("一二三 ", p.padded("一二三", 4))
        assertTrue(p.fits(p.padded("一二三", 4)))
    }

    @Test fun windowsReachTheLastGlyphAtFractionalCapacity() {
        val p = policy(RightTextCalibration(capacity = 3.5f))
        val text = "一二三四五六七八九十"
        val w = CalibratedTextWindow(text, p)
        assertEquals("一二三", w.prefix())
        assertEquals("八九十", w.suffix())
        assertEquals(w.suffix(), w.atProgress(1f))
        assertEquals(w.suffix(), w.atFocus(text.lastIndex..text.lastIndex, true))
        for (i in 0..20) assertTrue(p.fits(w.atProgress(i / 20f)))
    }

    @Test fun splittingAndWindowSelectionShareTheRightCapacity() {
        val texts = listOf("一二三四五六七八九十", "一二WWW三iiii四五六", "一二👨‍👩‍👧‍👦三e\u0301四五六七")
        val p = policy(RightTextCalibration(capacity = 2.75f))
        for (text in texts) {
            val w = CalibratedTextWindow(text, p, 6, SuperIslandLyricLayout::calculateWeight)
            for (i in 0..10) {
                val window = w.atProgress(i / 10f)
                val split = SuperIslandLyricLayout.splitFullLyric(window, true, 6, 14, p)
                assertEquals("$text: $window / $split", window, split.left + split.right.trimEnd())
                assertTrue(p.fits(split.right))
                assertTrue(SuperIslandLyricLayout.calculateWeight(split.left) <= 6)
            }
            assertTrue(w.suffix().endsWith(Glyphs.parts(text).last()))
        }
    }

    @Test fun focusedWideGlyphCanMoveIntoLeftColumn() {
        val p = policy(RightTextCalibration(capacity = 1f, overrides = mapOf("😀" to 2f)))
        val text = "一二😀三四五"
        val w = CalibratedTextWindow(text, p, 4, SuperIslandLyricLayout::calculateWeight)
        val focus = text.indexOf("😀")
        assertTrue(w.atFocus(focus until focus + "😀".length, true).contains("😀"))
    }

    @Test fun fullModeKeepsInternalSpacesIncludingThoseAtColumnBoundary() {
        val text = "一二  WWW三 四，五六七"
        val p = policy(RightTextCalibration(capacity = 3.5f, spaceWidth = 0.5f))
        val window = CalibratedTextWindow(text, p, 4, SuperIslandLyricLayout::calculateWeight)
        for (i in 0..20) {
            val visible = window.atProgress(i / 20f)
            val split = SuperIslandLyricLayout.splitFullLyric(visible, true, 4, 14, p)
            assertEquals("$visible / $split", visible.trim(), (split.left + split.right).trimEnd())
            assertTrue(p.fits(split.right))
        }
    }

    @Test fun corruptNumericValuesAreBounded() {
        val c = RightTextCalibration(capacity = Float.NaN, spaceWidth = -1f, margin = Float.POSITIVE_INFINITY,
            overrides = mapOf("AB" to 1f, "A" to Float.NaN, "W" to 1f)).sanitized()
        assertTrue(c.capacity.isFinite())
        assertTrue(c.spaceWidth > 0)
        assertTrue(c.margin < c.capacity)
        assertEquals(mapOf("W" to 1f), c.overrides)
    }

    @Test fun editorLengthLimitsDoNotSplitSurrogatesOrEmojiSequences() {
        assertEquals("A", Glyphs.limit("A😀B", 2))
        assertEquals("A", Glyphs.limit("A👨‍👩‍👧‍👦B", 8))
        assertEquals("Ae\u0301", Glyphs.limit("Ae\u0301B", 3))
    }
}
