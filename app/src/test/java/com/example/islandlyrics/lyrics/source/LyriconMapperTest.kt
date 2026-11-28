package com.example.islandlyrics.lyrics.source

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyriconMapperTest {

    @Test
    fun convertSongUsesDurationWithoutStretchingShortLines() {
        val song = Song(
            id = "s1",
            name = "Song",
            artist = "Artist",
            duration = 12_000L,
            lyrics = listOf(
                RichLyricLine(begin = 1_000L, end = 0L, duration = 2_000L, text = "Line 1"),
                RichLyricLine(begin = 3_000L, end = 0L, duration = 0L, text = "Line 2"),
                RichLyricLine(begin = 8_000L, end = 0L, duration = 0L, text = "Line 3")
            )
        )

        val lines = LyriconMapper.convertSong(
            song = song,
            receiveTranslation = true,
            receiveRomanization = true
        )

        assertEquals(3, lines.size)
        assertEquals(1_000L, lines[0].startTime)
        assertEquals(3_000L, lines[0].endTime)
        assertEquals(3_000L, lines[1].startTime)
        assertEquals(8_000L, lines[1].endTime)
        assertEquals(8_000L, lines[2].startTime)
        assertEquals(12_000L, lines[2].endTime)
    }

    @Test
    fun convertSongFallsBackToDefaultDurationForLastLineWhenSongDurationMissing() {
        val song = Song(
            name = "Song",
            artist = "Artist",
            duration = 0L,
            lyrics = listOf(RichLyricLine(begin = 4_000L, end = 0L, duration = 0L, text = "Only Line"))
        )

        val lines = LyriconMapper.convertSong(song, receiveTranslation = false, receiveRomanization = false)
        assertEquals(1, lines.size)
        assertEquals(4_000L + LyriconMapper.DEFAULT_LINE_DURATION_MS, lines[0].endTime)
    }

    @Test
    fun convertSongPreservesWhitespaceWordsWordDurationAndAlignment() {
        val song = Song(
            name = "Hello World",
            artist = "Artist",
            lyrics = listOf(
                RichLyricLine(
                    begin = 1_000L,
                    end = 2_000L,
                    isAlignedRight = true,
                    words = listOf(
                        LyricWord(begin = 1_000L, end = 0L, duration = 400L, text = "Hello"),
                        LyricWord(begin = 1_400L, end = 1_400L, text = " "),
                        LyricWord(begin = 1_400L, end = 2_000L, text = "World")
                    )
                )
            )
        )

        val lines = LyriconMapper.convertSong(song, receiveTranslation = false, receiveRomanization = false)
        assertEquals(1, lines.size)
        val line = lines[0]
        assertEquals("Hello World", line.text)
        assertEquals(OnlineLyricFetcher.LyricAlignment.RIGHT, line.alignment)
        val syllables = line.syllables
        assertNotNull(syllables)
        assertEquals(listOf("Hello", " ", "World"), syllables!!.map { it.text })
        assertEquals(1_400L, syllables[0].endTime)
    }

    @Test
    fun convertSongKeepsIndependentSidecarsForRepeatedChorusLinesAndRespectsFlags() {
        val song = Song(
            name = "Chorus",
            artist = "Singer",
            lyrics = listOf(
                RichLyricLine(
                    begin = 1_000L,
                    end = 3_000L,
                    text = "Repeat me",
                    translation = "第一遍翻译",
                    roma = "di yi bian"
                ),
                RichLyricLine(
                    begin = 5_000L,
                    end = 7_000L,
                    text = "Repeat me",
                    translation = "第二遍翻译",
                    secondary = "di er bian"
                )
            )
        )

        val both = LyriconMapper.convertSong(song, receiveTranslation = true, receiveRomanization = true)
        assertEquals("第一遍翻译", both[0].translation)
        assertEquals("di yi bian", both[0].roma)
        assertEquals("第二遍翻译", both[1].translation)
        assertEquals("di er bian", both[1].roma)

        val transOnly = LyriconMapper.convertSong(song, receiveTranslation = true, receiveRomanization = false)
        assertEquals("第一遍翻译", transOnly[0].translation)
        assertNull(transOnly[0].roma)
        assertNull(transOnly[1].roma)

        val neither = LyriconMapper.convertSong(song, receiveTranslation = false, receiveRomanization = false)
        assertNull(neither[0].translation)
        assertNull(neither[0].roma)
    }

    @Test
    fun findActiveLineRespectsPreludeActiveAndInterlude() {
        val lines = listOf(
            OnlineLyricFetcher.LyricLine(startTime = 2_000L, endTime = 4_000L, text = "First"),
            OnlineLyricFetcher.LyricLine(startTime = 6_000L, endTime = 8_000L, text = "Second")
        )

        assertNull(LyriconMapper.findActiveLine(lines, 1_500L))
        assertEquals("First", LyriconMapper.findActiveLine(lines, 2_000L)?.text)
        assertNull(LyriconMapper.findActiveLine(lines, 4_500L))
        assertEquals("Second", LyriconMapper.findActiveLine(lines, 6_500L)?.text)
        assertNull(LyriconMapper.findActiveLine(lines, -1L))
    }

    @Test
    fun parseReceiveTextSplitsMultilineAndHonorsTranslationFlag() {
        assertNull(LyriconMapper.parseReceiveText(null, receiveTranslation = true))
        assertNull(LyriconMapper.parseReceiveText("   \n  ", receiveTranslation = true))

        val single = LyriconMapper.parseReceiveText("Hello World", receiveTranslation = true)
        assertEquals(LyriconParsedText("Hello World", null), single)

        val multiWithTrans = LyriconMapper.parseReceiveText("你好 世界\nHello world", receiveTranslation = true)
        assertEquals(LyriconParsedText("你好 世界", "Hello world"), multiWithTrans)

        val multiWithoutTrans = LyriconMapper.parseReceiveText("你好 世界\nHello world", receiveTranslation = false)
        assertEquals(LyriconParsedText("你好 世界", null), multiWithoutTrans)
    }

    @Test
    fun matchesSessionHandlesMediaIdRawMetadataAndArtistSeparators() {
        val track = LyriconTrack(
            packageName = "com.netease.cloudmusic",
            title = "千里之外",
            artist = "周杰伦、费玉清",
            rawTitle = "千里之外 (Live)",
            rawArtist = "周杰伦 / 费玉清",
            mediaId = "track-42"
        )

        assertTrue(
            LyriconMapper.matchesSession(
                playerPkg = "com.netease.cloudmusic",
                song = Song(id = "track-42", name = "Totally Different", artist = "Other"),
                track = track
            )
        )
        assertTrue(
            LyriconMapper.matchesSession(
                playerPkg = "com.netease.cloudmusic",
                song = Song(name = "千里之外 (Live)", artist = "周杰伦 / 费玉清"),
                track = track
            )
        )
        assertTrue(
            LyriconMapper.matchesSession(
                playerPkg = "com.netease.cloudmusic",
                song = Song(name = "千里之外", artist = "周杰伦, 费玉清"),
                track = track
            )
        )
        assertFalse(
            LyriconMapper.matchesSession(
                playerPkg = "com.tencent.qqmusic",
                song = Song(name = "千里之外", artist = "周杰伦"),
                track = track
            )
        )
        assertFalse(
            LyriconMapper.matchesSession(
                playerPkg = "com.netease.cloudmusic",
                song = Song(name = "七里香", artist = "周杰伦"),
                track = track
            )
        )
    }
}
