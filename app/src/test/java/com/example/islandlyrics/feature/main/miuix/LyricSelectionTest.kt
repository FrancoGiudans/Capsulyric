/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.main.miuix

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricSelectionTest {
    @Test
    fun exportsSelectedLinesInTimelineOrderWithTranslationAndWithoutRomanization() {
        val lines = listOf(
            LyricLine(0, 1_000, "第一句", translation = "First\nline", roma = "yi"),
            LyricLine(1_000, 2_000, "第二句", translation = "第二句"),
            LyricLine(2_000, 3_000, "第三句", roma = "san"),
        )
        val selection = LyricSelection.start(lines, 2).toggle(0)

        assertEquals(listOf("第一句\nFirst line", "第三句"), selection.selectedLines().map { it.asParagraph() })
        assertEquals(16, selection.characterCount)
        assertNull(selection.loopRange(4_000))
        assertNull(LyricSelection.start(lines, 1).selectedLines().single().translation)
    }

    @Test
    fun limitCountsUnicodeCodePointsAndBothTextLayers() {
        val lines = listOf(
            LyricLine(0, 1_000, "🎵".repeat(198), translation = "译"),
            LyricLine(1_000, 2_000, "好"),
            LyricLine(2_000, 3_000, "另"),
        )
        val full = LyricSelection.start(lines, 0).toggle(1)

        assertEquals(LyricShareCharacterLimit, full.characterCount)
        assertTrue(full.canExport)
        assertFalse(full.canAdd(2))
        assertSame(full, full.toggle(2))
        assertEquals(0, full.toggle(1).toggle(0).characterCount)
        assertFalse(full.toggle(1).toggle(0).canExport)
    }

    @Test
    fun rejectsExportWhenInitialLineAloneExceedsLimit() {
        val selection = LyricSelection.start(listOf(LyricLine(0, 1_000, "a".repeat(201))), 0)
        assertEquals(setOf(0), selection.indices)
        assertFalse(selection.canExport)
        assertTrue(selection.toggle(0).indices.isEmpty())
    }

    @Test
    fun singleLineLoopClampsToTheTrackAndRecognizesItsEnd() {
        val selection = LyricSelection.start(listOf(LyricLine(1_000, 2_000, "Line")), 0)
        val loop = requireNotNull(selection.loopRange(1_400))
        assertEquals(1_000L, loop.start)
        assertEquals(1_400L, loop.end)
        assertTrue(loop.reachedStart(1_100))
        assertFalse(loop.reachedStart(1_300))
        assertFalse(loop.contains(1_400))
        assertNull(selection.loopRange(1_000))
    }
}
