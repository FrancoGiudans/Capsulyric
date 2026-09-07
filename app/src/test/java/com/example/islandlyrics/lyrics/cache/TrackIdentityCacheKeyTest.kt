package com.example.islandlyrics.lyrics.cache

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackIdentityCacheKeyTest {

    @Test
    fun mediaIdTakesPrecedenceOverLocalizedText() {
        val first = TrackIdentityCacheKey.build(
            packageName = "com.apple.android.music",
            title = "ただ風を追いかけて",
            artist = "アーティスト",
            album = "アルバム",
            durationMs = 181_000L,
            mediaId = "song:123",
            mediaUri = "https://music.apple.com/jp/song/example/123"
        )
        val localized = TrackIdentityCacheKey.build(
            packageName = "com.apple.android.music",
            title = "唯有追逐风的时候",
            artist = "艺术家",
            album = "专辑",
            durationMs = 181_000L,
            mediaId = "song:123",
            mediaUri = "https://music.apple.com/cn/song/example/123"
        )

        assertEquals(first, localized)
    }

    @Test
    fun fallbackKeyUsesPackageAndDurationBucket() {
        val sameBucket = TrackIdentityCacheKey.build(
            "player", "Title", "Artist", "Album", 181_100L, "", ""
        )
        val nearDuration = TrackIdentityCacheKey.build(
            "player", "title", "artist", "album", 181_900L, "", ""
        )
        val differentPlayer = TrackIdentityCacheKey.build(
            "other-player", "title", "artist", "album", 181_900L, "", ""
        )

        assertEquals(sameBucket, nearDuration)
        assertNotEquals(sameBucket, differentPlayer)
    }
}
