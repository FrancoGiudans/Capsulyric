package com.example.islandlyrics.lyrics.online.parser

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.SyllableInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineLyricInfoFilterTest {

    private fun line(
        startMs: Long,
        text: String,
        endMs: Long = startMs + 3_000L,
        syllables: List<SyllableInfo>? = null
    ) = LyricLine(
        startTime = startMs,
        endTime = endMs,
        text = text,
        syllables = syllables
    )

    @Test
    fun filtersExchangeRemainingLifeHeaderAndExtractsMergedWriters() {
        val lines = listOf(
            line(0, "交换余生 (No Turning Back) - 林俊杰 (JJ Lin)"),
            line(1_000, "词：易家扬"),
            line(2_000, "曲：林俊杰 JJ LIN"),
            line(3_000, "编曲 & 键盘 MUSIC ARRANGEMENT & KEYBOARDS：简道生Dawson Chien/蔡政勋 Andy Tsai"),
            line(4_000, "制作人 PRODUCER：林俊杰 JJ LIN"),
            line(5_000, "配唱制作 VOCAL PRODUCTION：林俊杰 JJ LIN"),
            line(18_500, "孤单听雨的猫"),
            line(22_000, "往时间裂缝里看到了我")
        )
        val context = TrackFilterContext(
            title = "交换余生",
            artist = "林俊杰"
        )

        val result = OnlineLyricInfoFilter.process(lines, context = context, filterInfoLines = true)

        assertEquals(6, result.removedCount)
        assertEquals(listOf("孤单听雨的猫", "往时间裂缝里看到了我"), result.lines.map { it.text })
        assertEquals(18_500L, result.lines.first().startTime)
        assertEquals(listOf("易家扬", "林俊杰 JJ LIN"), result.writers)
    }

    @Test
    fun filtersQrcHeadCreditsAndTrailingOpSpCredits() {
        val lines = listOf(
            line(0, "交换余生 - 林俊杰"),
            line(500, "词：易家扬"),
            line(1_000, "曲：林俊杰"),
            line(1_500, "编曲：简道生"),
            line(18_500, "孤单听雨的猫"),
            line(22_000, "雷雨呼啸而过"),
            line(240_000, "OP：JFJ Productions"),
            line(241_000, "SP：Warner Chappell Music"),
            line(242_000, "未经著作权人书面许可，不得以任何方式（包括翻唱、翻录等）使用")
        )

        val result = OnlineLyricInfoFilter.process(
            lines = lines,
            context = TrackFilterContext(title = "交换余生", artist = "林俊杰"),
            filterInfoLines = true
        )

        assertEquals(7, result.removedCount)
        assertEquals(listOf("孤单听雨的猫", "雷雨呼啸而过"), result.lines.map { it.text })
        assertEquals(listOf("易家扬", "林俊杰"), result.writers)
    }

    @Test
    fun extractsWritersFromNeteaseYrcJsonMetadataAndDeduplicates() {
        val rawYrc = """
            {"t":0,"c":[{"tx":"作词: "},{"tx":"林俊杰"}]}
            {"t":1000,"c":[{"tx":"作曲: "},{"tx":"林俊杰"}]}
            {"t":2000,"c":[{"tx":"编曲: "},{"tx":"简道生"}]}
            [18500,3000](18500,500,0)孤(19000,500,0)单
        """.trimIndent()
        val parsedLines = listOf(
            line(18_500, "孤单"),
            line(21_500, "往时间裂缝里看到了我")
        )

        val result = OnlineLyricInfoFilter.process(
            lines = parsedLines,
            rawLyrics = rawYrc,
            context = TrackFilterContext(title = "交换余生", artist = "林俊杰"),
            filterInfoLines = true
        )

        assertEquals(0, result.removedCount)
        assertEquals(2, result.lines.size)
        assertEquals(listOf("林俊杰"), result.writers)
    }

    @Test
    fun filtersNeteaseSpaceSeparatedNoColonCreditsAndExtractsWriters() {
        val lines = listOf(
            line(0, "作词 易家扬"),
            line(1_000, "作曲 林俊杰"),
            line(2_000, "编曲 简道生"),
            line(15_000, "第一句真正歌词"),
            line(19_000, "第二句真正歌词")
        )

        val result = OnlineLyricInfoFilter.process(
            lines = lines,
            context = TrackFilterContext(title = "交换余生", artist = "林俊杰"),
            filterInfoLines = true
        )

        assertEquals(3, result.removedCount)
        assertEquals(listOf("第一句真正歌词", "第二句真正歌词"), result.lines.map { it.text })
        assertEquals(listOf("易家扬", "林俊杰"), result.writers)
    }

    @Test
    fun filtersKugouEnglishCreditsAndExtractsWrittenBy() {
        val lines = listOf(
            line(0, "OneRepublic - Counting Stars"),
            line(1_000, "Written by：Ryan Tedder"),
            line(2_000, "Produced by: Ryan Tedder / Noel Zancanella"),
            line(12_000, "Lately I've been, I've been losing sleep"),
            line(16_000, "Dreaming about the things that we could be")
        )

        val result = OnlineLyricInfoFilter.process(
            lines = lines,
            context = TrackFilterContext(title = "Counting Stars", artist = "OneRepublic"),
            filterInfoLines = true
        )

        assertEquals(3, result.removedCount)
        assertEquals(
            listOf(
                "Lately I've been, I've been losing sleep",
                "Dreaming about the things that we could be"
            ),
            result.lines.map { it.text }
        )
        assertEquals(listOf("Ryan Tedder"), result.writers)
    }

    @Test
    fun extractsAppleMusicTtmlSongwritersDirectly() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal">
              <head>
                <metadata>
                  <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
                    <songwriters>
                      <songwriter>易家扬</songwriter>
                      <songwriter>林俊杰</songwriter>
                      <songwriter>易家扬</songwriter>
                    </songwriters>
                  </iTunesMetadata>
                </metadata>
              </head>
              <body>
                <div>
                  <p begin="18.5s" end="21.0s">孤单听雨的猫</p>
                </div>
              </body>
            </tt>
        """.trimIndent()
        val lines = listOf(line(18_500, "孤单听雨的猫"))

        val result = OnlineLyricInfoFilter.process(lines, rawLyrics = ttml, filterInfoLines = true)

        assertEquals(0, result.removedCount)
        assertEquals(listOf("易家扬", "林俊杰"), result.writers)
    }

    @Test
    fun filtersTraditionalChineseAndJapaneseKoreanCredits() {
        val lines = listOf(
            line(0, "交換餘生 - 林俊傑"),
            line(500, "作詞：易家揚"),
            line(1_000, "作曲：林俊傑"),
            line(1_500, "編曲：簡道生"),
            line(18_000, "孤單聽雨的貓")
        )
        val result = OnlineLyricInfoFilter.process(
            lines = lines,
            context = TrackFilterContext(title = "交换余生", artist = "林俊杰"),
            filterInfoLines = true
        )
        assertEquals(4, result.removedCount)
        assertEquals(listOf("孤單聽雨的貓"), result.lines.map { it.text })
        assertEquals(listOf("易家揚", "林俊傑"), result.writers)

        val krLines = listOf(
            line(0, "작사: IU"),
            line(500, "작곡: 이종훈"),
            line(1_000, "편곡: 이채규"),
            line(12_000, "이 밤 그날의 반딧불을")
        )
        val krResult = OnlineLyricInfoFilter.process(lines = krLines, filterInfoLines = true)
        assertEquals(3, krResult.removedCount)
        assertEquals(listOf("이 밤 그날의 반딧불을"), krResult.lines.map { it.text })
        assertEquals(listOf("IU", "이종훈"), krResult.writers)
    }

    @Test
    fun preservesFalsePositiveProneSungLyricsAndDuetMarkers() {
        val lines = listOf(
            line(0, "小酒窝 - 林俊杰/蔡卓妍"),
            line(500, "作词：王雅君"),
            line(1_000, "作曲：林俊杰"),
            line(10_000, "男：我还在寻找 一个依靠和一个拥抱"),
            line(15_000, "女：谁替我祈祷 替我烦恼 为我生气为我闹"),
            line(20_000, "合：小酒窝 长睫毛 是你最美的记号"),
            line(25_000, "她说：声音越来越远"),
            line(30_000, "Time: 12:00 at midnight"),
            line(35_000, "词不达意的沉默"),
            line(40_000, "曲终人散以后")
        )

        val result = OnlineLyricInfoFilter.process(
            lines = lines,
            context = TrackFilterContext(title = "小酒窝", artist = "林俊杰/蔡卓妍"),
            filterInfoLines = true
        )

        assertEquals(3, result.removedCount)
        assertEquals(
            listOf(
                "男：我还在寻找 一个依靠和一个拥抱",
                "女：谁替我祈祷 替我烦恼 为我生气为我闹",
                "合：小酒窝 长睫毛 是你最美的记号",
                "她说：声音越来越远",
                "Time: 12:00 at midnight",
                "词不达意的沉默",
                "曲终人散以后"
            ),
            result.lines.map { it.text }
        )
        assertEquals(listOf("王雅君", "林俊杰"), result.writers)
    }

    @Test
    fun protectsNamedArtistSpeakerPrefixesInDuets() {
        val lines = listOf(
            line(0, "词：王雅君"),
            line(500, "曲：林俊杰"),
            line(10_000, "林俊杰：我还在寻找"),
            line(14_000, "蔡卓妍：谁替我祈祷")
        )

        val result = OnlineLyricInfoFilter.process(
            lines = lines,
            context = TrackFilterContext(title = "小酒窝", artist = "林俊杰 / 蔡卓妍"),
            filterInfoLines = true
        )

        assertEquals(2, result.removedCount)
        assertEquals(listOf("林俊杰：我还在寻找", "蔡卓妍：谁替我祈祷"), result.lines.map { it.text })
    }

    @Test
    fun failSafeNeverEmptiesSongWhenAllLinesLookLikeInfo() {
        val lines = listOf(
            line(0, "纯音乐，请欣赏"),
            line(1_000, "作词：无")
        )
        // First line is not a credit line, so only second is removed.
        val partial = OnlineLyricInfoFilter.process(lines, filterInfoLines = true)
        assertEquals(listOf("纯音乐，请欣赏"), partial.lines.map { it.text })
        assertTrue(partial.writers.isEmpty())

        // If every single line looks like info, return original list untouched.
        val allInfo = listOf(
            line(0, "词：易家扬"),
            line(1_000, "曲：林俊杰")
        )
        val safe = OnlineLyricInfoFilter.process(allInfo, filterInfoLines = true)
        assertEquals(2, safe.lines.size)
        assertEquals(0, safe.removedCount)
        assertEquals(listOf("易家扬", "林俊杰"), safe.writers)
    }

    @Test
    fun filterDisabledStillExtractsWritersWithoutRemovingLines() {
        val lines = listOf(
            line(0, "词：易家扬"),
            line(1_000, "曲：林俊杰"),
            line(18_500, "孤单听雨的猫")
        )

        val result = OnlineLyricInfoFilter.process(lines, filterInfoLines = false)

        assertEquals(0, result.removedCount)
        assertEquals(3, result.lines.size)
        assertEquals(listOf("易家扬", "林俊杰"), result.writers)
    }

    @Test
    fun sidecarNoiseLineDetectsPlaceholdersAndCopyrightNotices() {
        assertTrue(OnlineLyricInfoFilter.isSidecarNoiseLine("//"))
        assertTrue(OnlineLyricInfoFilter.isSidecarNoiseLine("腾讯音乐享有本翻译作品的著作权"))
        assertTrue(OnlineLyricInfoFilter.isSidecarNoiseLine("未经许可，请勿翻唱或使用"))
        assertFalse(OnlineLyricInfoFilter.isSidecarNoiseLine("A cat listening to the rain alone"))
    }

    @Test
    fun filtersGluedCjkEnglishCreditsAndExtractsWritersUnderAndroidUnicodeRegexSemantics() {
        val lines = listOf(
            line(0, "恋人 - 李荣浩"),
            line(500, "词Lyricist：刘嘉星Jiaxing Liu"),
            line(1_000, "曲Composer：刘嘉星Jiaxing Liu"),
            line(1_500, "编曲Arrangement：李荣浩Ronghao Li"),
            line(2_000, "制作人Producer：李荣浩Ronghao Li"),
            line(2_500, "吉他Guitars：李荣浩Ronghao Li"),
            line(3_000, "贝斯BASS：李荣浩Ronghao Li"),
            line(3_500, "和音编写Backing Vocal Arrangement：李荣浩Ronghao Li"),
            line(4_000, "音乐制作室Music Production Studio：北京一样音乐工作室"),
            line(16_000, "第一句真正演唱的歌词"),
            line(20_000, "第二句真正演唱的歌词")
        )

        val result = OnlineLyricInfoFilter.process(
            lines = lines,
            context = TrackFilterContext(title = "恋人", artist = "李荣浩"),
            filterInfoLines = true
        )

        assertEquals(9, result.removedCount)
        assertEquals(listOf("第一句真正演唱的歌词", "第二句真正演唱的歌词"), result.lines.map { it.text })
        assertEquals(listOf("刘嘉星Jiaxing Liu"), result.writers)

        // Verify the regex patterns themselves match under Pattern.UNICODE_CHARACTER_CLASS
        // (matching Android ART's ICU regex engine where CJK chars are `\w`).
        val flags = java.util.regex.Pattern.CASE_INSENSITIVE or java.util.regex.Pattern.UNICODE_CHARACTER_CLASS
        assertTrue(java.util.regex.Pattern.compile(OnlineLyricInfoFilter.ENGLISH_ROLE_REGEX.pattern, flags).matcher("词lyricist").find())
        assertTrue(java.util.regex.Pattern.compile(OnlineLyricInfoFilter.ENGLISH_ROLE_REGEX.pattern, flags).matcher("曲composer").find())
        assertTrue(java.util.regex.Pattern.compile(OnlineLyricInfoFilter.LYRICIST_EN_REGEX.pattern, flags).matcher("词lyricist").find())
        assertTrue(java.util.regex.Pattern.compile(OnlineLyricInfoFilter.COMPOSER_EN_REGEX.pattern, flags).matcher("曲composer").find())
        assertFalse(java.util.regex.Pattern.compile(OnlineLyricInfoFilter.ENGLISH_ROLE_REGEX.pattern, flags).matcher("remix").find())
    }

    @Test
    fun filtersCreditsEvenWithTightDashHeaderOrLeadingCustomCredit() {
        val lines = listOf(
            line(0, "恋人-李荣浩"),
            line(500, "特别企划顾问：某工作室"),
            line(1_000, "词Lyricist：刘嘉星Jiaxing Liu"),
            line(1_500, "曲Composer：刘嘉星Jiaxing Liu"),
            line(16_000, "第一句真正演唱的歌词")
        )

        val result = OnlineLyricInfoFilter.process(
            lines = lines,
            context = TrackFilterContext(title = "Lover", artist = "Ronghao Li"),
            filterInfoLines = true
        )

        assertEquals(4, result.removedCount)
        assertEquals(listOf("第一句真正演唱的歌词"), result.lines.map { it.text })
        assertEquals(listOf("刘嘉星Jiaxing Liu"), result.writers)
    }

    @Test
    fun filtersTrailingUnknownCreditInBlockWhileProtectingRepeatingDuetSpeaker() {
        val lines = listOf(
            line(0, "词：方文山"),
            line(1_000, "曲：周杰伦"),
            line(2_000, "特殊乐器：某演奏家"),
            line(18_000, "阿信：电话开始躲"),
            line(22_000, "周杰伦：没有了联络"),
            line(40_000, "阿信：你什么都没有")
        )

        val result = OnlineLyricInfoFilter.process(
            lines = lines,
            context = TrackFilterContext(title = "说好不哭", artist = "周杰伦"),
            filterInfoLines = true
        )

        assertEquals(3, result.removedCount)
        assertEquals(
            listOf("阿信：电话开始躲", "周杰伦：没有了联络", "阿信：你什么都没有"),
            result.lines.map { it.text }
        )
        assertEquals(listOf("方文山", "周杰伦"), result.writers)
    }
}
