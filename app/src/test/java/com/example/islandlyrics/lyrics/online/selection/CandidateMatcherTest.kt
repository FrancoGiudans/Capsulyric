package com.example.islandlyrics.lyrics.online.selection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CandidateMatcherTest {

    @Test
    fun exactTitleAndArtistIsAccepted() {
        val candidate = Candidate(
            matchedTitle = "唯有追逐风的时候",
            matchedArtist = "示例歌手",
            matchedAlbum = "示例专辑",
            matchedDurationMs = 181_000L
        )

        assertEquals(
            candidate,
            CandidateMatcher.pickBest(
                listOf(candidate),
                title = "唯有追逐风的时候",
                artist = "示例歌手",
                album = "示例专辑",
                durationMs = 181_000L
            )
        )
    }

    @Test
    fun titleMismatchCanBeRecoveredByArtistAlbumAndDuration() {
        val candidate = Candidate(
            matchedTitle = "ただ風を追いかけて",
            matchedArtist = "示例歌手",
            matchedAlbum = "示例专辑",
            matchedDurationMs = 181_000L
        )

        assertEquals(
            candidate,
            CandidateMatcher.pickBest(
                listOf(candidate),
                title = "唯有追逐风的时候",
                artist = "示例歌手",
                album = "示例专辑",
                durationMs = 181_000L
            )
        )
    }

    @Test
    fun localizedArtistCanBeRecoveredByAlbumAndDuration() {
        val candidate = Candidate(
            matchedTitle = "ただ風を追いかけて",
            matchedArtist = "示例歌手（日文名）",
            matchedAlbum = "示例专辑",
            matchedDurationMs = 181_000L
        )

        assertEquals(
            candidate,
            CandidateMatcher.pickBest(
                listOf(candidate),
                title = "唯有追逐风的时候",
                artist = "示例歌手",
                album = "示例专辑",
                durationMs = 181_000L
            )
        )
    }

    @Test
    fun unrelatedCandidateIsRejectedInsteadOfFallingBackToFirst() {
        val candidate = Candidate(
            matchedTitle = "完全不同的歌曲",
            matchedArtist = "其他歌手",
            matchedAlbum = "其他专辑",
            matchedDurationMs = 92_000L
        )

        assertNull(
            CandidateMatcher.pickBest(
                listOf(candidate),
                title = "唯有追逐风的时候",
                artist = "示例歌手",
                album = "示例专辑",
                durationMs = 181_000L
            )
        )
    }

    @Test
    fun versionWithLargeDurationConflictIsRejected() {
        val candidate = Candidate(
            matchedTitle = "唯有追逐风的时候",
            matchedArtist = "示例歌手",
            matchedAlbum = "示例专辑 (Live)",
            matchedDurationMs = 245_000L
        )

        assertNull(
            CandidateMatcher.pickBest(
                listOf(candidate),
                title = "唯有追逐风的时候",
                artist = "示例歌手",
                album = "示例专辑",
                durationMs = 181_000L
            )
        )
    }

    @Test
    fun remixCandidateIsRejectedForStudioTarget() {
        val candidate = Candidate(
            matchedTitle = "唯有追逐风的时候 (Remix)",
            matchedArtist = "示例歌手",
            matchedAlbum = "示例专辑",
            matchedDurationMs = 181_000L
        )

        assertNull(
            CandidateMatcher.pickBest(
                listOf(candidate),
                title = "唯有追逐风的时候",
                artist = "示例歌手",
                album = "示例专辑",
                durationMs = 181_000L
            )
        )
    }

    @Test
    fun matchingLiveVersionIsAcceptedWhenTargetIsLive() {
        val candidate = Candidate(
            matchedTitle = "唯有追逐风的时候 (Live)",
            matchedArtist = "示例歌手",
            matchedAlbum = "示例专辑 (Live)",
            matchedDurationMs = 181_000L
        )

        assertEquals(
            candidate,
            CandidateMatcher.pickBest(
                listOf(candidate),
                title = "唯有追逐风的时候 (Live)",
                artist = "示例歌手",
                album = "示例专辑 (Live)",
                durationMs = 181_000L
            )
        )
    }

    @Test
    fun coverCandidateIsRejectedForOriginalTarget() {
        val candidate = Candidate(
            matchedTitle = "唯有追逐风的时候 (Cover)",
            matchedArtist = "其他歌手",
            matchedAlbum = "示例专辑",
            matchedDurationMs = 181_000L
        )

        assertNull(
            CandidateMatcher.pickBest(
                listOf(candidate),
                title = "唯有追逐风的时候",
                artist = "示例歌手",
                album = "示例专辑",
                durationMs = 181_000L
            )
        )
    }

    private data class Candidate(
        override val matchedTitle: String,
        override val matchedArtist: String,
        override val matchedAlbum: String?,
        override val matchedDurationMs: Long?
    ) : SearchCandidate
}
