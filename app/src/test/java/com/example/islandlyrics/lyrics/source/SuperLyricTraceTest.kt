@file:Suppress("DEPRECATION")

package com.example.islandlyrics.lyrics.source

import com.hchen.superlyricapi.LyricView
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricLine
import com.hchen.superlyricapi.model.Line
import com.hchen.superlyricapi.model.Lyric
import com.hchen.superlyricapi.model.Word
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SuperLyricTraceTest {
    private val client = Any()

    @Before fun start() { SuperLyricTrace.clear(); SuperLyricTrace.capture(client, true) }
    @After fun finish() { SuperLyricTrace.capture(client, false); SuperLyricTrace.clear() }

    @Test fun rawFieldsRetainInvalidWordsWhileDecodedShowsNormalization() {
        val view = LyricView(Lyric("id", "Song", "Artist", "Album", 1000, listOf(
            Line("", -10, -1, listOf(Word("bad", -1, 2), Word("ok", 100, 200)), "", null)
        )), 0, 150, 1f, null, "player")
        val raw = SuperLyricTraceFormat.modern(view)
        val decoded = SuperLyricTraceFormat.decoded(SuperLyricMapper.modern(view))
        assertTrue(raw.contains("bad @ -1..2"))
        assertTrue(raw.contains("lyric.lines[0].start = -10"))
        assertTrue(raw.contains("lyric.lines[0].text = \"\""))
        assertTrue(decoded.contains("currentLine.text = \"ok\""))
        assertTrue(decoded.contains("currentLine.startTime = 0"))
        assertFalse(decoded.contains("bad @"))
        assertTrue(decoded.contains("translation = null"))
    }

    @Test fun historyIsBoundedAndDecisionsStayWithTheirOriginalCallback() {
        val first = SuperLyricTrace.begin("v4", "LYRIC") { "first" }!!
        val second = SuperLyricTrace.begin("legacy", "STOP") { "second" }!!
        SuperLyricTrace.decoded(first) { "decoded first" }
        SuperLyricTrace.decision(first, "ignored: mismatch")
        assertEquals("等待处理", SuperLyricTrace.entries.value.first().decision)
        assertEquals("ignored: mismatch", SuperLyricTrace.entries.value.last().decision)
        assertEquals("decoded first", SuperLyricTrace.entries.value.last().decoded)
        assertEquals(second, SuperLyricTrace.entries.value.first().id)
        repeat(30) { SuperLyricTrace.begin("v4", "LYRIC") { "row $it" } }
        assertEquals(SuperLyricTrace.MAX_EVENTS, SuperLyricTrace.entries.value.size)
        assertFalse(SuperLyricTrace.entries.value.any { it.id == first })
    }

    @Test fun legacySidecarOnlyCallbackRemainsDistinctFromPrimaryLine() {
        val data = SuperLyricData().setTranslation(SuperLyricLine("translation\nsecond row"))
        val raw = SuperLyricTraceFormat.legacy("player", data)
        val decoded = SuperLyricTraceFormat.decoded(SuperLyricMapper.legacy("player", data))
        assertTrue(raw.contains("lyric = null"))
        assertTrue(raw.contains("translation.text = \"translation\\nsecond row\""))
        assertTrue(decoded.contains("currentLine = null"))
        assertTrue(decoded.contains("translation = \"translation\\nsecond row\""))
    }

    @Test fun pausedCaptureDoesNotEvaluateRawPayloadAndClearRejectsLateDecision() {
        val old = SuperLyricTrace.begin("v4", "LYRIC") { "old" }
        SuperLyricTrace.capture(client, false)
        assertNull(SuperLyricTrace.begin("v4", "LYRIC") { error("must not run") })
        SuperLyricTrace.clear()
        SuperLyricTrace.capture(client, true)
        val current = SuperLyricTrace.begin("v4", "LYRIC") { "current" }
        SuperLyricTrace.decision(old, "late result")
        assertNotEquals(old, current)
        assertEquals("等待处理", SuperLyricTrace.entries.value.single().decision)
    }

    @Test fun oversizedDocumentIsBoundedAndExplicitlyMarked() {
        val view = LyricView(Lyric("id", "Song", "Artist", "Album", 1000,
            (0..1000).map { Line("row $it " + "x".repeat(300), 0, 1) }), -1, 0, 1f, null, null)
        val raw = SuperLyricTraceFormat.modern(view)
        assertTrue(raw.length <= SuperLyricTraceFormat.MAX_CHARS)
        assertTrue(raw.contains("已截断"))
        assertTrue(raw.contains("lines.count = 1001"))
    }

    @Test fun failedConversionRetainsRawCallbackAndItsFailure() {
        val id = SuperLyricTrace.begin("legacy", "LYRIC") { "malformed input" }
        SuperLyricTrace.failed(id, IllegalArgumentException("invalid word"))
        val entry = SuperLyricTrace.entries.value.single()
        assertEquals("malformed input", entry.raw)
        assertTrue(entry.decoded.contains("IllegalArgumentException"))
        assertTrue(entry.export().contains("invalid word"))
    }
}
