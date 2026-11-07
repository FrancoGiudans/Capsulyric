package com.example.islandlyrics.lyrics.online.provider

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.network.OnlineLyricHttpClient
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.ui.overlay.display.ParsedLyricDisplayState
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcApiLyricProviderTest {
    @Test
    fun enhancedLrcReportsWordCapabilityToDisplayState() {
        val result = fetch("[00:01.000]<00:01.000>Hello <00:02.000>world<00:03.000>")

        assertTrue(result.hasSyllable)
        assertEquals("Hello world", result.parsedLines!!.single().text)
        assertEquals(2, result.parsedLines!!.single().syllables!!.size)
        assertTrue(displayState(result).useSyllableScrolling)
    }

    @Test
    fun plainLrcKeepsLineScrolling() {
        val result = fetch("[00:01.000]Hello world")

        assertFalse(result.hasSyllable)
        assertEquals("Hello world", result.parsedLines!!.single().text)
        assertTrue(displayState(result).useLrcScrolling)
    }

    @Test
    fun invalidWordTimingDoesNotAdvertiseWordCapability() {
        val result = fetch("[00:01.000]<00:02.000>Hello<00:01.500>")

        assertFalse(result.hasSyllable)
        assertEquals("Hello", result.parsedLines!!.single().text)
        assertTrue(displayState(result).useLrcScrolling)
    }

    @Test
    fun existingKrcFormatKeepsItsParserAndWordCapability() {
        val result = fetch("[1000,1000]<0,500,0>你<500,500,0>好")

        assertTrue(result.hasSyllable)
        assertEquals("你好", result.parsedLines!!.single().text)
        assertEquals(listOf(1_000L, 1_500L), result.parsedLines!!.single().syllables!!.map { it.startTime })
    }

    private fun displayState(result: OnlineLyricFetcher.LyricResult) =
        ParsedLyricDisplayState.from(LyricRepository.ParsedLyricsInfo(
            lines = result.parsedLines!!,
            hasSyllable = result.hasSyllable,
            timelineCapability = LyricRepository.TimelineCapability.MULTI_LINE
        ))

    private fun fetch(content: String): OnlineLyricFetcher.LyricResult = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(content.toResponseBody("text/plain; charset=utf-8".toMediaType()))
                .build()
        }.build()
        try {
            LrcApiLyricProvider(OnlineLyricHttpClient(client)).fetch("Title", "Artist")!!
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
