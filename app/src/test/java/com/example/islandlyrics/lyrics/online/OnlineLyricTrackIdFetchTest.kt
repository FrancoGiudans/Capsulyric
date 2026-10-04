package com.example.islandlyrics.lyrics.online

import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineLyricTrackIdFetchTest {
    @Test
    fun offlineModeStopsEverySupportedIdProvider() = runBlocking {
        var networkChecks = 0
        val fetcher = OnlineLyricFetcher(networkAllowed = { networkChecks++; false })
        OnlineLyricProvider.entries.filter { it.supportsTrackId }.forEach { provider ->
            val id = if (provider == OnlineLyricProvider.Kugou) "0123456789abcdef0123456789abcdef" else "12345"
            assertNull(fetcher.fetchLyricsById(provider, id))
        }
        assertEquals(7, networkChecks)
    }

    @Test
    fun invalidAndUnsupportedIdsStopBeforeAnyNetworkCheck() = runBlocking {
        var networkChecks = 0
        val fetcher = OnlineLyricFetcher(networkAllowed = { networkChecks++; false })
        OnlineLyricProvider.entries.forEach { provider ->
            assertNull(fetcher.fetchLyricsById(provider, "https://example.com/song/12345"))
        }
        assertNull(fetcher.fetchLyricsById(OnlineLyricProvider.LrcApi, "12345"))
        assertEquals(0, networkChecks)
    }
}
