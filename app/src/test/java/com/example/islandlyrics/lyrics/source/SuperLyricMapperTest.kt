package com.example.islandlyrics.lyrics.source

import com.hchen.superlyricapi.LyricView
import com.hchen.superlyricapi.model.Line
import com.hchen.superlyricapi.model.Lyric
import com.hchen.superlyricapi.model.Word
import org.junit.Assert.*
import org.junit.Test

class SuperLyricMapperTest {
    private fun doc(id: String = "track-1", lines: List<Line> = listOf(
        Line("hello", 1000, 2000, listOf(Word("hel", 1000, 1400), Word("lo", 1400, 2000)), "你好", "he-lo"),
        Line("again", 3000, 4000)
    )) = Lyric(id, "Song", "Artist", "Album", 10_000, lines)

    private fun view(document: Lyric = doc(), index: Int = 0) =
        LyricView(document, index, 1500, 1f, null, "player")

    @Test fun fullDocumentPreservesAbsoluteWordTimesAndSidecars() {
        val mapped = SuperLyricMapper.modern(view())
        assertTrue(mapped.hasTimeline)
        assertEquals(2, mapped.lines!!.size)
        assertEquals(1000L, mapped.lines[0].syllables!![0].startTime)
        assertEquals(2000L, mapped.lines[0].syllables!![1].endTime)
        assertEquals("你好", mapped.translation)
        assertEquals("he-lo", mapped.roma)
        assertNull(mapped.playbackState)
    }

    @Test fun preludeHasTimelineWithoutInventingACurrentLine() {
        val mapped = SuperLyricMapper.modern(view(index = -1))
        assertTrue(mapped.hasTimeline)
        assertNull(mapped.currentLine)
        assertNull(mapped.translation)
    }

    @Test fun outOfRangeIndexDoesNotCrashOrSelectFirstLine() {
        assertNull(SuperLyricMapper.modern(view(index = 999)).currentLine)
    }

    @Test fun lineOnlyDocumentWithoutTimingCanUseOnlineFallback() {
        val mapped = SuperLyricMapper.modern(view(doc(lines = listOf(Line("text", 0, 0)))))
        assertFalse(mapped.hasTimeline)
        assertEquals("text", mapped.currentLine!!.text)
    }

    @Test fun plainTimedLinesAreAUsableFullTimeline() {
        val mapped = SuperLyricMapper.modern(view(doc(lines = listOf(Line("text", 1000, 2000)))))
        assertTrue(mapped.hasTimeline)
        assertNull(mapped.currentLine!!.syllables)
    }

    @Test fun severalUntimedLinesDoNotPretendToProvideATimeline() {
        val mapped = SuperLyricMapper.modern(view(doc(lines = listOf(Line("one", 0, 0), Line("two", 0, 0)))))
        assertFalse(mapped.hasTimeline)
    }

    @Test fun malformedWordTimesAreDiscardedWithoutShiftingGoodWords() {
        val line = Line("", -10, -1, listOf(Word("bad", -1, 20), Word("bad", 100, 90), Word("ok", 200, 300)), null, null)
        val mapped = SuperLyricMapper.modern(view(doc(lines = listOf(line)))).currentLine!!
        assertEquals("ok", mapped.text)
        assertEquals(0L, mapped.startTime)
        assertEquals(0L, mapped.endTime)
        assertEquals(listOf(200L), mapped.syllables!!.map { it.startTime })
    }

    @Test fun repeatedChorusHasDifferentLineKeys() {
        val document = doc(lines = listOf(Line("same", 1000, 2000), Line("same", 5000, 6000)))
        val track = SuperLyricTrack("player", "Song", "Artist")
        assertNotEquals(
            SuperLyricMapper.lineKey(track, SuperLyricMapper.modern(view(document, 0))),
            SuperLyricMapper.lineKey(track, SuperLyricMapper.modern(view(document, 1)))
        )
    }

    @Test fun sameTextOnDifferentTracksDoesNotShareDedupKey() {
        val mapped = SuperLyricMapper.modern(view())
        assertNotEquals(
            SuperLyricMapper.lineKey(SuperLyricTrack("player", "Song", "Artist", "1"), mapped),
            SuperLyricMapper.lineKey(SuperLyricTrack("player", "Song", "Artist", "2"), mapped)
        )
    }

    @Test fun positionFramesReuseConvertedDocument() {
        val mapper = SuperLyricDocumentMapper()
        val document = doc()
        val first = mapper.map(view(document, 0))
        val second = mapper.map(view(document, 1))
        assertSame(first.lines, second.lines)
        assertNotEquals(first.currentLine, second.currentLine)
    }

    @Test fun updatedDocumentWithSameIdRefreshesContent() {
        val mapper = SuperLyricDocumentMapper()
        val first = mapper.map(view(doc()))
        val next = mapper.map(view(doc(lines = listOf(Line("corrected", 1000, 2500)))))
        assertEquals(first.documentId, next.documentId)
        assertNotEquals(first.lines, next.lines)
        assertEquals("corrected", next.currentLine!!.text)
    }

    @Test fun publisherAndMetadataMustMatchCurrentSession() {
        val update = SuperLyricMapper.modern(view())
        assertTrue(SuperLyricMapper.matches(update, SuperLyricTrack("player", "song", "ARTIST")))
        assertFalse(SuperLyricMapper.matches(update, SuperLyricTrack("other", "Song", "Artist")))
        assertFalse(SuperLyricMapper.matches(update, SuperLyricTrack("player", "Other Song", "Artist")))
        assertFalse(SuperLyricMapper.matches(update, SuperLyricTrack("player", "Song", "Other Artist")))
        assertTrue(SuperLyricMapper.matches(update.copy(publisher = null), SuperLyricTrack("player", "Song", "Artist")))
    }
}
