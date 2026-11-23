package com.example.islandlyrics.lyrics.online.parser

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricAlignment
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.SyllableInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineLyricDuetAlignerTest {

    private fun line(
        startMs: Long,
        text: String,
        endMs: Long = startMs + 3_000L,
        syllables: List<SyllableInfo>? = null,
        translation: String? = null,
        roma: String? = null
    ) = LyricLine(
        startTime = startMs,
        endTime = endMs,
        text = text,
        syllables = syllables,
        translation = translation,
        roma = roma
    )

    @Test
    fun maleFirstFemaleSecondRightAlignsFemaleAndStripsPrefixes() {
        val lines = listOf(
            line(10_000, "男：我还在寻找 一个依靠和一个拥抱", translation = "Male: I'm still looking"),
            line(14_000, "女：谁替我祈祷 替我烦恼", translation = "Female: Who prays for me"),
            line(18_000, "合：小酒窝 长睫毛 是你最美的记号", translation = "Both: Dimples and long lashes"),
            line(22_000, "女：我每天睡不着 想念你的微笑")
        )

        val result = OnlineLyricDuetAligner.process(lines, enabled = true)

        assertEquals(
            listOf(
                "我还在寻找 一个依靠和一个拥抱",
                "谁替我祈祷 替我烦恼",
                "小酒窝 长睫毛 是你最美的记号",
                "我每天睡不着 想念你的微笑"
            ),
            result.map { it.text }
        )
        assertEquals(
            listOf(
                LyricAlignment.LEFT,
                LyricAlignment.RIGHT,
                LyricAlignment.LEFT,
                LyricAlignment.RIGHT
            ),
            result.map { it.alignment }
        )
        assertEquals("I'm still looking", result[0].translation)
        assertEquals("Who prays for me", result[1].translation)
        assertEquals("Dimples and long lashes", result[2].translation)
    }

    @Test
    fun femaleFirstMaleSecondRightAlignsLaterEnteringMale() {
        val lines = listOf(
            line(10_000, "女：风到这里就是粘"),
            line(14_000, "粘住过客的思念"),
            line(18_000, "男：雨到了这里缠成线"),
            line(22_000, "缠着我们流连人世间"),
            line(26_000, "合：你在身边就是缘"),
            line(30_000, "男：缘分写在三生石上面")
        )

        val result = OnlineLyricDuetAligner.process(lines, enabled = true)

        assertEquals(
            listOf(
                LyricAlignment.LEFT,
                LyricAlignment.LEFT,
                LyricAlignment.RIGHT,
                LyricAlignment.RIGHT,
                LyricAlignment.LEFT,
                LyricAlignment.RIGHT
            ),
            result.map { it.alignment }
        )
        assertEquals(
            listOf(
                "风到这里就是粘",
                "粘住过客的思念",
                "雨到了这里缠成线",
                "缠着我们流连人世间",
                "你在身边就是缘",
                "缘分写在三生石上面"
            ),
            result.map { it.text }
        )
    }

    @Test
    fun handlesStandaloneSpeakerCueLinesAndBracketedPrefixes() {
        val lines = listOf(
            line(10_000, "（男）好久不见"),
            line(14_000, "女："),
            line(15_000, "你还好吗"),
            line(19_000, "【合】一切都变了")
        )

        val result = OnlineLyricDuetAligner.process(lines, enabled = true)

        assertEquals(listOf("好久不见", "你还好吗", "一切都变了"), result.map { it.text })
        assertEquals(
            listOf(LyricAlignment.LEFT, LyricAlignment.RIGHT, LyricAlignment.LEFT),
            result.map { it.alignment }
        )
    }

    @Test
    fun supportsNamedArtistPrefixesAndCompositeChorus() {
        val lines = listOf(
            line(10_000, "周杰伦：屋檐如悬崖 风铃如沧海"),
            line(15_000, "费玉清：我送你离开 千里之外"),
            line(20_000, "周+费：沉默年代 或许不该")
        )
        val context = TrackFilterContext(title = "千里之外", artist = "周杰伦 / 费玉清")

        val result = OnlineLyricDuetAligner.process(lines, context = context, enabled = true)

        assertEquals(
            listOf("屋檐如悬崖 风铃如沧海", "我送你离开 千里之外", "沉默年代 或许不该"),
            result.map { it.text }
        )
        assertEquals(
            listOf(LyricAlignment.LEFT, LyricAlignment.RIGHT, LyricAlignment.LEFT),
            result.map { it.alignment }
        )
    }

    @Test
    fun singleSpeakerDoesNotTriggerDuetModeOrStripPrefix() {
        val lines = listOf(
            line(10_000, "男：孤单听雨的猫"),
            line(14_000, "她说：声音越来越远")
        )

        val result = OnlineLyricDuetAligner.process(lines, enabled = true)

        assertEquals(lines, result)
        assertEquals(listOf(LyricAlignment.UNSPECIFIED, LyricAlignment.UNSPECIFIED), result.map { it.alignment })
    }

    @Test
    fun disabledFlagLeavesLinesUntouched() {
        val lines = listOf(
            line(10_000, "男：第一句"),
            line(14_000, "女：第二句")
        )

        val result = OnlineLyricDuetAligner.process(lines, enabled = false)

        assertEquals(lines, result)
    }

    @Test
    fun trimsLeadingPrefixFromSyllablesBothWholeAndMidToken() {
        // Case 1: whole prefix syllables
        val line1 = line(
            startMs = 10_000,
            text = "男：你好",
            syllables = listOf(
                SyllableInfo(10_000, 10_100, "男"),
                SyllableInfo(10_100, 10_200, "："),
                SyllableInfo(10_200, 10_600, "你"),
                SyllableInfo(10_600, 11_000, "好")
            )
        )
        // Case 2: prefix merged inside first syllable token ("女：世")
        val line2 = line(
            startMs = 12_000,
            text = "女：世界",
            syllables = listOf(
                SyllableInfo(12_000, 12_900, "女：世"),
                SyllableInfo(12_900, 13_400, "界")
            )
        )

        val result = OnlineLyricDuetAligner.process(listOf(line1, line2), enabled = true)

        assertEquals("你好", result[0].text)
        assertEquals(
            listOf(SyllableInfo(10_200, 10_600, "你"), SyllableInfo(10_600, 11_000, "好")),
            result[0].syllables
        )

        assertEquals("世界", result[1].text)
        assertEquals(
            listOf(SyllableInfo(12_600, 12_900, "世"), SyllableInfo(12_900, 13_400, "界")),
            result[1].syllables
        )
        assertEquals(LyricAlignment.RIGHT, result[1].alignment)
    }

    @Test
    fun mismatchedSyllablesFallbackSafelyToNullOnPrefixStrip() {
        val line1 = line(
            startMs = 10_000,
            text = "男：你好",
            syllables = listOf(SyllableInfo(10_000, 11_000, "不匹配"))
        )
        val line2 = line(startMs = 12_000, text = "女：世界")

        val result = OnlineLyricDuetAligner.process(listOf(line1, line2), enabled = true)

        assertEquals("你好", result[0].text)
        assertNull(result[0].syllables)
    }

    @Test
    fun parsesAppleMusicTtmlNativeDuetAgentsAndKeepsSoloLeft() {
        val duetTtml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <head>
                <metadata>
                  <ttm:agent type="person" xml:id="v1"/>
                  <ttm:agent type="person" xml:id="v2"/>
                  <ttm:agent type="group" xml:id="v1000"/>
                </metadata>
              </head>
              <body>
                <div>
                  <p begin="1.0s" end="3.0s" ttm:agent="v1">First singer line</p>
                  <p begin="4.0s" end="6.0s" ttm:agent="v2">Second singer line</p>
                  <p begin="7.0s" end="9.0s" ttm:agent="v1000">Together chorus</p>
                  <p begin="10.0s" end="12.0s" ttm:agent="v2">Second singer returns</p>
                  <p begin="13.0s" end="15.0s" ttm:agent="v1">First singer returns</p>
                </div>
              </body>
            </tt>
        """.trimIndent()
        val duetLines = OnlineLyricParser.parseTtmlLyrics(duetTtml)

        val aligned = OnlineLyricDuetAligner.process(duetLines, rawLyrics = duetTtml, enabled = true)

        assertEquals(
            listOf(
                LyricAlignment.LEFT,
                LyricAlignment.RIGHT,
                LyricAlignment.LEFT,
                LyricAlignment.RIGHT,
                LyricAlignment.LEFT
            ),
            aligned.map { it.alignment }
        )

        // Solo TTML with single agent must not right-align anything
        val soloTtml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <head><metadata><ttm:agent type="person" xml:id="v1"/></metadata></head>
              <body><div>
                <p begin="1.0s" end="3.0s" ttm:agent="v1">Solo line 1</p>
                <p begin="4.0s" end="6.0s" ttm:agent="v1">Solo line 2</p>
              </div></body>
            </tt>
        """.trimIndent()
        val soloLines = OnlineLyricParser.parseTtmlLyrics(soloTtml)
        val soloResult = OnlineLyricDuetAligner.process(soloLines, rawLyrics = soloTtml, enabled = true)
        assertEquals(
            listOf(LyricAlignment.UNSPECIFIED, LyricAlignment.UNSPECIFIED),
            soloResult.map { it.alignment }
        )
    }

    @Test
    fun supportsStandaloneArtistCueLinesEvenWithoutGuestInMetadata() {
        val lines = listOf(
            line(25_000, "周杰伦："),
            line(26_000, "没有了联络后来的生活"),
            line(31_000, "我都是听别人说"),
            line(48_000, "阿信："),
            line(49_000, "电话开始躲从不对我说"),
            line(54_000, "不习惯一个人生活"),
            line(70_000, "周杰伦/阿信："),
            line(71_000, "你什么都没有"),
            line(85_000, "阿信："),
            line(86_000, "却还为我的梦加油")
        )

        // Works even when guest artist ("阿信") is absent from TrackFilterContext
        val result = OnlineLyricDuetAligner.process(
            lines = lines,
            context = TrackFilterContext(title = "说好不哭", artist = "周杰伦"),
            enabled = true
        )

        assertEquals(
            listOf(
                "没有了联络后来的生活",
                "我都是听别人说",
                "电话开始躲从不对我说",
                "不习惯一个人生活",
                "你什么都没有",
                "却还为我的梦加油"
            ),
            result.map { it.text }
        )
        assertEquals(
            listOf(
                LyricAlignment.LEFT,
                LyricAlignment.LEFT,
                LyricAlignment.RIGHT,
                LyricAlignment.RIGHT,
                LyricAlignment.LEFT,
                LyricAlignment.RIGHT
            ),
            result.map { it.alignment }
        )
    }

    @Test
    fun supportsFeaturedGuestFromTitleAndCjkSuffixAliasInline() {
        val lines = listOf(
            line(26_000, "周杰伦：没有了联络后来的生活"),
            line(49_000, "阿信：电话开始躲从不对我说")
        )

        val result = OnlineLyricDuetAligner.process(
            lines = lines,
            context = TrackFilterContext(title = "说好不哭 (with 五月天阿信)", artist = "周杰伦"),
            enabled = true
        )

        assertEquals(listOf("没有了联络后来的生活", "电话开始躲从不对我说"), result.map { it.text })
        assertEquals(listOf(LyricAlignment.LEFT, LyricAlignment.RIGHT), result.map { it.alignment })
    }

    @Test
    fun doesNotMistakeSectionOrCreditStandaloneLinesAsDuetSingers() {
        val lines = listOf(
            line(1_000, "主歌："),
            line(2_000, "第一句歌词"),
            line(10_000, "副歌："),
            line(11_000, "第二句歌词")
        )

        val result = OnlineLyricDuetAligner.process(lines, enabled = true)

        assertEquals(lines, result)
    }

    @Test
    fun supportsUncreditedBandMembersInlineAndRejectsNarrativeDialogueColons() {
        val bandLines = listOf(
            line(10_000, "王俊凯：跟着我左手右手一个慢动作"),
            line(14_000, "王源：右手左手慢动作重播"),
            line(18_000, "易烊千玺：这首歌给你快乐"),
            line(22_000, "合：你有没有爱上我")
        )
        val bandResult = OnlineLyricDuetAligner.process(
            lines = bandLines,
            context = TrackFilterContext(title = "青春修炼手册", artist = "TFBOYS"),
            enabled = true
        )
        assertEquals(
            listOf("跟着我左手右手一个慢动作", "右手左手慢动作重播", "这首歌给你快乐", "你有没有爱上我"),
            bandResult.map { it.text }
        )
        assertEquals(
            listOf(LyricAlignment.LEFT, LyricAlignment.RIGHT, LyricAlignment.LEFT, LyricAlignment.LEFT),
            bandResult.map { it.alignment }
        )

        val dialogueLines = listOf(
            line(10_000, "妈妈说：天黑要早点回家"),
            line(14_000, "她说：声音越来越远"),
            line(18_000, "日记写着：今天下起了雨")
        )
        val dialogueResult = OnlineLyricDuetAligner.process(dialogueLines, enabled = true)
        assertEquals(dialogueLines, dialogueResult)
    }
}
