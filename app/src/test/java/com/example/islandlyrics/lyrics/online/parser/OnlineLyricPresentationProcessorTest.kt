package com.example.islandlyrics.lyrics.online.parser

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricAlignment
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.SyllableInfo
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import com.example.islandlyrics.rules.ParserRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineLyricPresentationProcessorTest {

    @Test
    fun runsSidecarFilterInfoFilterAndDuetAlignerInOrderWithoutMutatingInput() {
        val rawLines = listOf(
            LyricLine(0L, 1_000L, "小酒窝 - 林俊杰 / 蔡卓妍"),
            LyricLine(1_000L, 2_000L, "作词：王雅君"),
            LyricLine(2_000L, 3_000L, "作曲：林俊杰"),
            LyricLine(3_000L, 4_000L, "编曲：林俊杰/吴剑泓"),
            LyricLine(
                startTime = 12_000L,
                endTime = 16_000L,
                text = "男：我还在寻找",
                syllables = listOf(
                    SyllableInfo(12_000L, 12_100L, "男"),
                    SyllableInfo(12_100L, 12_200L, "："),
                    SyllableInfo(12_200L, 13_000L, "我"),
                    SyllableInfo(13_000L, 14_000L, "还在"),
                    SyllableInfo(14_000L, 16_000L, "寻找")
                )
            ),
            LyricLine(16_000L, 20_000L, "女：谁替我祈祷"),
            LyricLine(20_000L, 24_000L, "合：小酒窝 长睫毛"),
            LyricLine(220_000L, 222_000L, "OP：JFJ Productions")
        )
        val result = OnlineLyricFetcher.LyricResult(
            api = "QQMusic",
            lyrics = "[00:00.00]小酒窝",
            parsedLines = rawLines,
            hasSyllable = true,
            provider = OnlineLyricProvider.QQMusic,
            matchedTitle = "小酒窝",
            matchedArtist = "林俊杰/蔡卓妍",
            translationLyrics = """
                [00:00.00]腾讯音乐享有本翻译作品的著作权
                [00:12.00]Male: I am still looking
                [00:16.00]Female: Who prays for me
                [00:20.00]//
            """.trimIndent()
        )
        val rule = ParserRule(packageName = "test.pkg", receiveOnlineTranslation = true)
        val context = TrackFilterContext(title = "小酒窝", artist = "林俊杰 / 蔡卓妍")

        // Enabled: filters 5 info lines, extracts writers, aligns & strips duet prefixes
        val enabled = OnlineLyricPresentationProcessor.prepare(
            result = result,
            rule = rule,
            trackContext = context,
            filterInfoLines = true,
            duetAlignmentEnabled = true
        )

        assertEquals(5, enabled.removedInfoLineCount)
        assertEquals(listOf("王雅君", "林俊杰"), enabled.writers)
        assertEquals(listOf("我还在寻找", "谁替我祈祷", "小酒窝 长睫毛"), enabled.lines.map { it.text })
        assertEquals(
            listOf(LyricAlignment.LEFT, LyricAlignment.RIGHT, LyricAlignment.LEFT),
            enabled.lines.map { it.alignment }
        )
        assertEquals("我还在寻找", enabled.lines[0].syllables?.joinToString("") { it.text })
        assertEquals("I am still looking", enabled.lines[0].translation)
        assertEquals("Who prays for me", enabled.lines[1].translation)
        assertNull(enabled.lines[2].translation)

        // Toggled off: preserves all raw lines and prefixes without losing writers
        val disabled = OnlineLyricPresentationProcessor.prepare(
            result = result,
            rule = rule,
            trackContext = context,
            filterInfoLines = false,
            duetAlignmentEnabled = false
        )

        assertEquals(0, disabled.removedInfoLineCount)
        assertEquals(8, disabled.lines.size)
        assertEquals("男：我还在寻找", disabled.lines[4].text)
        assertEquals(LyricAlignment.UNSPECIFIED, disabled.lines[5].alignment)
        assertEquals(listOf("王雅君", "林俊杰"), disabled.writers)
    }
}
