package com.example.islandlyrics.lyrics.online.parser

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.provider.MusixmatchLyricProvider
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import com.example.islandlyrics.lyrics.online.provider.QqMusicLyricProvider
import com.example.islandlyrics.lyrics.online.selection.CandidateMatcher
import com.example.islandlyrics.rules.ParserRule
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpstreamSyncV021Test {

    @Test
    fun ttmlParserExtractsNativePronunciationsAndStripsUnmatchedParenthesizedTranslations() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xml:lang="ja">
              <head>
                <metadata>
                  <itunes:iTunesMetadata>
                    <translations>
                      <translation type="subtitle" xml:lang="zh-Hans-CN">
                        <text for="L1">凝望星空 (合声翻译不应混入主行)</text>
                        <text for="L2">(纯背景翻译)</text>
                      </translation>
                    </translations>
                    <transliterations>
                      <transliteration xml:lang="ja-Latn">
                        <text for="L1"><span begin="00:01.000" end="00:01.500">ho</span><span begin="00:01.500" end="00:02.000">shi</span> <span begin="00:02.000" end="00:03.000">wo</span><span ttm:role="x-bg"><span begin="00:02.500" end="00:03.000">aa</span></span></text>
                        <text for="L2"><span begin="00:04.000" end="00:05.000">yo</span><span begin="00:05.000" end="00:06.000">ru</span></text>
                      </transliteration>
                    </transliterations>
                  </itunes:iTunesMetadata>
                </metadata>
              </head>
              <body>
                <div>
                  <p begin="00:01.000" end="00:03.000" itunes:key="L1">
                    <span begin="00:01.000" end="00:02.000">星</span><span begin="00:02.000" end="00:03.000">を</span>
                    <span ttm:role="x-bg"><span begin="00:02.500" end="00:03.000">(ああ)</span></span>
                  </p>
                  <p begin="00:04.000" end="00:06.000" itunes:key="L2">
                    <span begin="00:04.000" end="00:06.000">夜</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val parsed = OnlineLyricParser.parseTtmlDocument(ttml)
        assertEquals(2, parsed.lines.size)
        // Main line text and syllables exclude x-bg vocal spans
        assertEquals("星を", parsed.lines[0].text)
        assertEquals(2, parsed.lines[0].syllables?.size)

        val appleResult = OnlineLyricFetcher.LyricResult(
            api = "AppleMusic",
            lyrics = ttml,
            parsedLines = parsed.lines,
            hasSyllable = true,
            provider = OnlineLyricProvider.AppleMusic,
            translationLyrics = parsed.translationLrc,
            romanLyrics = parsed.romanizationLrc
        )
        val merged = OnlineLyricSidecarMerger.withSidecars(
            result = appleResult,
            rule = ParserRule(
                packageName = "test.apple",
                receiveOnlineTranslation = true,
                receiveOnlineRomanization = true
            )
        )

        // L1: parenthesized bg translation stripped; native ja-Latn pronunciation attached without x-bg "aa"
        assertEquals("凝望星空", merged[0].translation)
        assertEquals("hoshi wo", merged[0].roma)

        // L2: subtitle had ONLY "(纯背景翻译)" on a main vocal line -> stripped completely (null)
        assertNull(merged[1].translation)
        assertEquals("yoru", merged[1].roma)
    }

    @Test
    fun ttmlParserAppliesSimplifiedChineseReplacementWhenRootIsTraditional() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xml:lang="zh-Hant">
              <head>
                <metadata>
                  <itunes:iTunesMetadata>
                    <translations>
                      <translation type="replacement" xml:lang="zh-Hans">
                        <text for="L1"><span begin="00:01.000" end="00:01.500">风</span><span begin="00:01.500" end="00:02.000">华</span></text>
                      </translation>
                    </translations>
                  </itunes:iTunesMetadata>
                </metadata>
              </head>
              <body>
                <div>
                  <p begin="00:01.000" end="00:02.000" itunes:key="L1">
                    <span begin="00:01.000" end="00:01.500">風</span><span begin="00:01.500" end="00:02.000">華</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val parsed = TtmlParser.parse(ttml)
        assertEquals("风华", parsed.lines.single().text)
        assertEquals(listOf("风", "华"), parsed.lines.single().syllables?.map { it.text })
    }

    @Test
    fun qqMobileSearchParserStripsHighlightTagsAndExpandsGroupedVersions() {
        val mobileResponse = """
            {
              "code": 0,
              "music.search.SearchCgiService.DoSearchForQQMusicMobile": {
                "code": 0,
                "data": {
                  "code": 0,
                  "body": {
                    "item_song": [
                      {
                        "id": "1001",
                        "mid": "001StudioMid",
                        "title": "<em>残響散歌</em>",
                        "interval": 184,
                        "singer": [{"name": "<em>Aimer</em>"}],
                        "album": {"name": "<em>残響散歌</em> / 朝が来る"},
                        "grp": [
                          {
                            "id": "1002",
                            "mid": "002TftMid",
                            "title": "<em>残響散歌</em> - From THE FIRST TAKE",
                            "interval": 185,
                            "singer": [{"name": "<em>Aimer</em>"}],
                            "album": {"name": "残響散歌 - From THE FIRST TAKE"}
                          }
                        ]
                      }
                    ]
                  }
                }
              }
            }
        """.trimIndent()

        val candidates = QqMusicLyricProvider.parseMobileSearchCandidates(mobileResponse)
        assertEquals(2, candidates.size)
        assertEquals("残響散歌", candidates[0].matchedTitle)
        assertEquals("Aimer", candidates[0].matchedArtist)
        assertEquals("残響散歌 / 朝が来る", candidates[0].matchedAlbum)
        assertEquals("残響散歌 - From THE FIRST TAKE", candidates[1].matchedTitle)

        // Selecting for a TFT target directly hits the expanded grp sub-track!
        val picked = CandidateMatcher.pickBest(
            candidates = candidates,
            title = "残響散歌 - From THE FIRST TAKE",
            artist = "Aimer",
            durationMs = 184_952L
        )
        assertEquals("002TftMid", picked?.providerTrackId)
    }

    @Test
    fun musixmatchSubtitleListAndTokenValidationFollowUpstreamRules() {
        assertFalse(MusixmatchLyricProvider.isUsableToken(null))
        assertFalse(MusixmatchLyricProvider.isUsableToken("null"))
        assertFalse(MusixmatchLyricProvider.isUsableToken("000000"))
        assertTrue(MusixmatchLyricProvider.isUsableToken("260119abc123"))

        val subtitleCall = JSONObject(
            """
            {
              "message": {
                "header": { "status_code": 200 },
                "body": {
                  "subtitle_list": [
                    {
                      "subtitle": {
                        "subtitle_body": "[00:01.00]Hello world"
                      }
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )
        assertEquals("[00:01.00]Hello world", MusixmatchLyricProvider.extractSubtitleBody(subtitleCall))
    }

    @Test
    fun instrumentalPromptsAndNewProductionInfoRolesAreDetected() {
        val djPrompt = listOf(
            OnlineLyricFetcher.LyricLine(0L, 5_000L, "此歌曲为没有填词的DJ舞曲，请您欣赏")
        )
        assertTrue(OnlineLyricParser.isInstrumentalLyrics(djPrompt))

        val lines = listOf(
            OnlineLyricFetcher.LyricLine(0L, 1_000L, "作词：张三"),
            OnlineLyricFetcher.LyricLine(1_000L, 2_000L, "导演：李四"),
            OnlineLyricFetcher.LyricLine(2_000L, 3_000L, "妆发：王五"),
            OnlineLyricFetcher.LyricLine(3_000L, 4_000L, "调色：赵六"),
            OnlineLyricFetcher.LyricLine(10_000L, 15_000L, "真正的第一句歌词")
        )
        val filtered = OnlineLyricInfoFilter.process(lines = lines, filterInfoLines = true)
        assertEquals(listOf("真正的第一句歌词"), filtered.lines.map { it.text })
        assertEquals(listOf("张三"), filtered.writers)
    }
}
