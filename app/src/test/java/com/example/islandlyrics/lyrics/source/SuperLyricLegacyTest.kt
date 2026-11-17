@file:Suppress("DEPRECATION")

package com.example.islandlyrics.lyrics.source

import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricLine
import com.hchen.superlyricapi.SuperLyricWord
import org.junit.Assert.*
import org.junit.Test

class SuperLyricLegacyTest {
    private val publisher = "com.apple.android.music"

    private fun map(data: SuperLyricData, previous: SuperLyricUpdate? = null) =
        SuperLyricMapper.legacy(publisher, data, previous)

    private fun line(text: String = "current", start: Long = 1000, end: Long = 2000) =
        SuperLyricLine(text, start, end)

    @Test fun reportedPlainLinePublishesTimingAndTranslationWithoutWordsOrFullDocument() {
        val update = map(SuperLyricData().setLyric(
            line("信じたもの 守れたなら", 132073, 140868)
                .setTranslation("As long as I can protect what I believed in")
        ))
        assertEquals("legacy-line", update.mode)
        assertEquals("", update.title)
        assertEquals("", update.artist)
        assertNull(update.lines)
        assertNull(update.position)
        assertTrue(update.hasTimeline)
        assertFalse(update.hasFullTimeline)
        val row = update.timelineLines().single()
        assertEquals(132073L, row.startTime)
        assertEquals(140868L, row.endTime)
        assertNull(row.syllables)
        assertEquals("As long as I can protect what I believed in", row.translation)
    }

    @Test fun correctedWordSamplePreservesAllFourAbsoluteIntervalsAndTranslation() {
        val words = arrayOf(
            SuperLyricWord("鮮", 26688, 27007),
            SuperLyricWord("烈な", 27007, 27601),
            SuperLyricWord("出来", 27601, 27925),
            SuperLyricWord("事", 27925, 28321)
        )
        val update = map(SuperLyricData()
            .setLyric(SuperLyricLine("鮮烈な出来事", words, 26688, 28321).setTranslation("A vivid event"))
            .setTranslation(SuperLyricLine("A vivid event")))
        assertTrue(update.hasTimeline)
        assertFalse(update.hasFullTimeline)
        val row = update.timelineLines().single()
        assertEquals("鮮烈な出来事", row.text)
        assertEquals("A vivid event", row.translation)
        assertEquals(listOf(26688L, 27007L, 27601L, 27925L), row.syllables!!.map { it.startTime })
        assertEquals(listOf(27007L, 27601L, 27925L, 28321L), row.syllables.map { it.endTime })
        assertEquals(listOf("鮮", "烈な", "出来", "事"), row.syllables.map { it.text })
    }

    @Test fun separateTranslationAndRomanizationAlsoAppearInPublishedLine() {
        val update = map(SuperLyricData().setLyric(line())
            .setTranslation(SuperLyricLine("translation"))
            .setSecondary(SuperLyricLine("romanization")))
        val row = update.timelineLines().single()
        assertEquals("translation", update.translation)
        assertEquals("romanization", update.roma)
        assertEquals(update.translation, row.translation)
        assertEquals(update.roma, row.roma)
    }

    @Test fun splitSidecarCallbacksRetainTimingAndMergeBothKindsOnSameLine() {
        val state = SuperLyricLegacyLineState()
        state.accept(map(SuperLyricData().setLyric(line())))
        val translated = state.accept(map(SuperLyricData().setTranslation(SuperLyricLine("translation"))))
        assertEquals("translation", translated.timelineLines().single().translation)
        val romanized = state.accept(map(SuperLyricData().setSecondary(SuperLyricLine("romanization"))))
        val row = romanized.timelineLines().single()
        assertEquals(1000L, row.startTime)
        assertEquals(2000L, row.endTime)
        assertEquals("current", row.text)
        assertEquals("translation", row.translation)
        assertEquals("romanization", row.roma)
        assertFalse(romanized.hasFullTimeline)
    }

    @Test fun nextLineDoesNotInheritPreviousTranslationOrRomanizationEvenWithRepeatedText() {
        val state = SuperLyricLegacyLineState()
        state.accept(map(SuperLyricData().setLyric(line())
            .setTranslation(SuperLyricLine("old translation")).setSecondary(SuperLyricLine("old roma"))))
        val next = state.accept(map(SuperLyricData().setLyric(line(start = 3000, end = 4000))))
        assertNull(next.translation)
        assertNull(next.roma)
        assertNull(next.timelineLines().single().translation)
        assertNull(next.timelineLines().single().roma)
    }

    @Test fun repeatedPrimaryCallbackPreservesPreviouslyReceivedSidecarsForSameTimedLine() {
        val state = SuperLyricLegacyLineState()
        state.accept(map(SuperLyricData().setLyric(line()).setSecondary(SuperLyricLine("roma"))))
        val next = state.accept(map(SuperLyricData().setLyric(line())
            .setTranslation(SuperLyricLine("translation"))))
        assertEquals("roma", next.timelineLines().single().roma)
        assertEquals("translation", next.timelineLines().single().translation)
    }

    @Test fun resetOrPublisherChangeDoesNotAttachOrphanSidecarToOldLine() {
        val state = SuperLyricLegacyLineState()
        state.accept(map(SuperLyricData().setLyric(line())))
        val other = state.accept(SuperLyricMapper.legacy("other.player",
            SuperLyricData().setSecondary(SuperLyricLine("other roma"))))
        assertNull(other.currentLine)
        state.reset()
        val afterReset = state.accept(map(SuperLyricData().setTranslation(SuperLyricLine("orphan"))))
        assertNull(afterReset.currentLine)
        assertTrue(afterReset.timelineLines().isEmpty())
    }

    @Test fun fullDocumentKeepsWireIndexWhilePublishingSortedTimelineWithMergedSidecars() {
        val full = map(SuperLyricData().setLyricId("doc")
            .setAllLyrics(arrayOf(line("later", 3000, 4000), line("first", 1000, 2000)))
            .setCurrentLyricIndex(0).setTranslation(SuperLyricLine("later translation")))
        assertTrue(full.hasFullTimeline)
        assertEquals("later", full.currentLine!!.text)
        assertEquals(listOf("first", "later"), full.timelineLines().map { it.text })
        assertEquals("later translation", full.timelineLines()[1].translation)
        val next = map(SuperLyricData().setLyricId("doc").setCurrentLyricIndex(1), full)
        assertEquals("first", next.currentLine!!.text)
        assertNull(next.translation)
        val companion = map(SuperLyricData().setLyricId("doc").setCurrentLyricIndex(1)
            .setSecondary(SuperLyricLine("first roma")), next)
        assertEquals("first", companion.currentLine!!.text)
        assertEquals("first roma", companion.timelineLines().first().roma)
    }

    @Test fun untimedLegacyTextRemainsTextWithoutFabricatingTimeline() {
        val update = map(SuperLyricData().setLyric(SuperLyricLine("untimed"))
            .setSecondary(SuperLyricLine("roma")))
        assertFalse(update.hasTimeline)
        assertFalse(update.hasFullTimeline)
        assertTrue(update.timelineLines().isEmpty())
        assertEquals("roma", update.currentLine!!.roma)
    }

    @Test fun explicitNoActiveIndexDoesNotAttachFullDocumentSidecarToPreviousLine() {
        val full = map(SuperLyricData().setLyricId("doc").setAllLyrics(arrayOf(line())).setCurrentLyricIndex(0))
        val prelude = map(SuperLyricData().setLyricId("doc").setCurrentLyricIndex(-1)
            .setTranslation(SuperLyricLine("unbound translation")), full)
        assertNull(prelude.currentLine)
        assertNull(prelude.timelineLines().single().translation)
    }

    @Test fun newFullPayloadDoesNotInheritPreviousActiveIndex() {
        val full = map(SuperLyricData().setAllLyrics(arrayOf(line())).setCurrentLyricIndex(0))
        val next = map(SuperLyricData().setAllLyrics(arrayOf(line("next")))
            .setTranslation(SuperLyricLine("unbound translation")), full)
        assertNull(next.currentLine)
        assertEquals(-1, next.index)
        assertNull(next.timelineLines().single().translation)
    }

    @Test fun zeroStartLineKeepsItsBoundsAndMissingBoundsUseOnlyValidWords() {
        val fromZero = map(SuperLyricData().setLyric(SuperLyricLine("line",
            arrayOf(SuperLyricWord("line", 100, 800)), 0, 1000)))
        assertEquals(0L, fromZero.currentLine!!.startTime)
        assertEquals(1000L, fromZero.currentLine.endTime)
        val inferred = map(SuperLyricData().setLyric(SuperLyricLine("line",
            arrayOf(SuperLyricWord("bad", -5, 100), SuperLyricWord("line", 200, 800)), 0, 0)))
        assertEquals(200L, inferred.currentLine!!.startTime)
        assertEquals(800L, inferred.currentLine.endTime)
        assertEquals(1, inferred.currentLine.syllables!!.size)
    }
}
