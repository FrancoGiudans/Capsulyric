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
    fun hiraganaTitleMatchesRomanizedTitle() {
        val candidate = Candidate(
            matchedTitle = "さよなら",
            matchedArtist = "Example Artist",
            matchedAlbum = null,
            matchedDurationMs = null
        )

        assertEquals(
            candidate,
            CandidateMatcher.pickBest(
                candidates = listOf(candidate),
                title = "SAYONARA",
                artist = "Example Artist"
            )
        )
    }

    @Test
    fun katakanaTitleMatchesHiraganaTitle() {
        assertEquals(
            24,
            CandidateMatcher.scoreTitleMatch("サヨナラ", "さよなら")
        )
    }

    @Test
    fun commonRomanizationVariantsMatchKana() {
        assertEquals(24, CandidateMatcher.scoreTitleMatch("ちいさなつ", "tiisana tu"))
    }

    @Test
    fun unrelatedRomanizedTitleDoesNotMatchKana() {
        assertEquals(-30, CandidateMatcher.scoreTitleMatch("さよなら", "arigatou"))
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
    fun japaneseOffVocalCandidateIsRejectedForVocalTarget() {
        val vocal = Candidate(
            matchedTitle = "ただ風を追いかけて",
            matchedArtist = "ロビン, HOYO-MiX & Chevy",
            matchedAlbum = "ただ風を追いかけて",
            matchedDurationMs = 123_871L,
            isrc = "FRX452677440"
        )
        val offVocal = vocal.copy(
            matchedTitle = "ただ風を追いかけて (オフボーカル音源)",
            matchedArtist = "HOYO-MiX",
            isrc = "FRX452677441"
        )

        assertEquals(
            vocal,
            CandidateMatcher.pickBestWithMargin(
                candidates = listOf(vocal, offVocal),
                title = "ただ風を追いかけて",
                artist = "Robin, HOYO-MiX & Chevy",
                album = "ただ風を追いかけて",
                durationMs = 123_871L
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

    @Test
    fun searchFallbackRequiresClearWinner() {
        val clearWinner = Candidate(
            matchedTitle = "唯有追逐风的时候",
            matchedArtist = "示例歌手",
            matchedAlbum = "示例专辑",
            matchedDurationMs = 181_000L
        )
        val closeRunnerUp = clearWinner.copy(matchedDurationMs = 184_000L)

        assertNull(
            CandidateMatcher.pickBestWithMargin(
                candidates = listOf(clearWinner, closeRunnerUp),
                title = "唯有追逐风的时候",
                artist = "示例歌手",
                album = "示例专辑",
                durationMs = 181_000L
            )
        )
    }

    @Test
    fun duplicateCatalogRowsWithSameIsrcDoNotCreateFalseAmbiguity() {
        val winner = Candidate(
            matchedTitle = "ただ風を追いかけて",
            matchedArtist = "Example Artist",
            matchedAlbum = "Example Album",
            matchedDurationMs = 181_000L,
            providerTrackId = "song-1",
            isrc = "JP-ABC-26-12345"
        )
        val duplicateRelease = winner.copy(
            matchedAlbum = "Example Album (Deluxe)",
            providerTrackId = "song-2",
            isrc = "JPABC2612345"
        )

        assertEquals(
            winner,
            CandidateMatcher.pickBestWithMargin(
                candidates = listOf(winner, duplicateRelease),
                title = winner.matchedTitle,
                artist = winner.matchedArtist,
                album = winner.matchedAlbum.orEmpty(),
                durationMs = winner.matchedDurationMs ?: 0L
            )
        )
    }

    @Test
    fun recoveryAndDiscoveryAlbumsAreNotFalselyRejectedAsCoverOrLive() {
        val recoveryTrack = Candidate(
            matchedTitle = "Love The Way You Lie",
            matchedArtist = "Eminem / Rihanna",
            matchedAlbum = "Recovery",
            matchedDurationMs = 263_000L
        )
        assertEquals(
            recoveryTrack,
            CandidateMatcher.pickBest(
                candidates = listOf(recoveryTrack),
                title = "Love The Way You Lie",
                artist = "Eminem, Rihanna",
                album = "",
                durationMs = 263_000L
            )
        )

        val aliveTrack = Candidate(
            matchedTitle = "Stayin' Alive",
            matchedArtist = "Bee Gees",
            matchedAlbum = "Discovery",
            matchedDurationMs = 285_000L
        )
        assertEquals(
            aliveTrack,
            CandidateMatcher.pickBest(
                candidates = listOf(aliveTrack),
                title = "Stayin’ Alive",
                artist = "Bee Gees",
                album = "",
                durationMs = 285_000L
            )
        )
    }

    @Test
    fun traditionalChineseAndMultiplicationArtistMatchesSimplifiedOriginal() {
        val original = Candidate(
            matchedTitle = "打上花火",
            matchedArtist = "DAOKO/米津玄师",
            matchedAlbum = "打上花火",
            matchedDurationMs = 289_000L
        )
        val unlabeledCover = Candidate(
            matchedTitle = "打上花火",
            matchedArtist = "某翻唱歌手",
            matchedAlbum = "打上花火",
            matchedDurationMs = 289_000L
        )

        assertEquals(
            original,
            CandidateMatcher.pickBest(
                candidates = listOf(unlabeledCover, original),
                title = "打上花火",
                artist = "DAOKO × 米津玄師",
                album = "打上花火",
                durationMs = 289_000L
            )
        )
        assertNull(
            CandidateMatcher.pickBest(
                candidates = listOf(unlabeledCover),
                title = "打上花火",
                artist = "DAOKO × 米津玄師",
                album = "打上花火",
                durationMs = 289_000L
            )
        )
    }

    @Test
    fun sameLanguageDifferentSongOnSameAlbumIsNotBridgedByDuration() {
        val wrongSongSameAlbum = Candidate(
            matchedTitle = "阴天",
            matchedArtist = "示例歌手",
            matchedAlbum = "示例专辑",
            matchedDurationMs = 210_000L
        )

        assertNull(
            CandidateMatcher.pickBest(
                candidates = listOf(wrongSongSameAlbum),
                title = "晴天",
                artist = "示例歌手",
                album = "示例专辑",
                durationMs = 211_000L
            )
        )
    }

    @Test
    fun kanaTitleWithPunctuationOrSpacesStillMatchesRomaji() {
        assertEquals(24, CandidateMatcher.scoreTitleMatch("さよなら、またね", "sayonara matane"))
        assertEquals(24, CandidateMatcher.scoreTitleMatch("さよなら バイバイ", "sayonara baibai"))
    }

    @Test
    fun cleanTitlePreservesNormalWordsAndStripsFullWidthBrackets() {
        assertEquals("Alive", CandidateMatcher.cleanTitle("Alive"))
        assertEquals("Discovery", CandidateMatcher.cleanTitle("Discovery"))
        assertEquals("HOYO-MiX", CandidateMatcher.cleanTitle("HOYO-MiX"))
        assertEquals("打上花火", CandidateMatcher.cleanTitle("打上花火（动画电影《烟花》主题曲）"))
        // CJK corner quotes 「...」 / 『...』 belong to the core song title and must not be stripped!
        assertEquals("「僕は...」", CandidateMatcher.cleanTitle("「僕は...」 (「我...」)"))
    }

    @Test
    fun japaneseCornerQuotedTitleWithTranslatedSubtitleReachesStrongMatch() {
        val assessment = CandidateMatcher.assessIdentity(
            targetTitle = "「僕は...」 (「我...」)",
            targetArtist = "あたらよ",
            targetAlbum = "",
            targetDurationMs = 239_000L,
            matchedTitle = "「僕は...」",
            matchedArtist = "あたらよ",
            matchedAlbum = "「僕は...」",
            matchedDurationMs = 239_761L
        )

        assertEquals(30, assessment.titleScore)
        assertEquals(18, assessment.artistScore)
        assertEquals(20, assessment.durationScore)
        assertEquals(3, assessment.tier)
        assertEquals(true, assessment.acceptable)
    }

    @Test
    fun bilingualTitleMatchesPrimaryCjkTitleAtHighConfidence() {
        val assessment = CandidateMatcher.assessIdentity(
            targetTitle = "无中生有 Ex Nihilo",
            targetArtist = "HOYO-MiX/當山みれい",
            targetAlbum = "",
            targetDurationMs = 205_000L,
            matchedTitle = "无中生有",
            matchedArtist = "HOYO-MiX / 當山みれい",
            matchedAlbum = "崩坏星穹铁道",
            matchedDurationMs = 205_400L
        )

        assertEquals(28, assessment.titleScore)
        assertEquals(3, assessment.tier)
        assertEquals(true, assessment.acceptable)
    }

    @Test
    fun bilingualAndCrossScriptArtistNamesMatchExactTitleAndDuration() {
        val gemCandidate = Candidate(
            matchedTitle = "光年之外",
            matchedArtist = "G.E.M. 邓紫棋",
            matchedAlbum = "光年之外",
            matchedDurationMs = 235_000L
        )
        assertEquals(
            gemCandidate,
            CandidateMatcher.pickBest(
                candidates = listOf(gemCandidate),
                title = "光年之外",
                artist = "G.E.M.",
                album = "",
                durationMs = 235_000L
            )
        )

        val zhouShenCandidate = Candidate(
            matchedTitle = "大鱼",
            matchedArtist = "周深",
            matchedAlbum = "大鱼",
            matchedDurationMs = 313_000L
        )
        assertEquals(
            zhouShenCandidate,
            CandidateMatcher.pickBest(
                candidates = listOf(zhouShenCandidate),
                title = "大鱼",
                artist = "Zhou Shen",
                album = "大鱼",
                durationMs = 313_000L
            )
        )
    }

    @Test
    fun theFirstTakeRejectsIdenticalDurationStudioCandidateAndMatchesParenthesizedTft() {
        // 残響散歌 - From THE FIRST TAKE and studio 残響散歌 both have ~184s duration,
        // but TFT is a live re-recording with completely different lyric timing.
        val studio = Candidate(
            matchedTitle = "残響散歌",
            matchedArtist = "Aimer",
            matchedAlbum = "残響散歌 / 朝が来る",
            matchedDurationMs = 184_000L
        )
        val parenthesizedTft = Candidate(
            matchedTitle = "残響散歌 (From \"THE FIRST TAKE\")",
            matchedArtist = "Aimer",
            matchedAlbum = "残響散歌 - From THE FIRST TAKE",
            matchedDurationMs = 184_952L
        )

        assertNull(
            CandidateMatcher.pickBest(
                candidates = listOf(studio),
                title = "残響散歌 - From THE FIRST TAKE",
                artist = "Aimer",
                album = "",
                durationMs = 184_952L
            )
        )
        assertEquals(
            parenthesizedTft,
            CandidateMatcher.pickBest(
                candidates = listOf(studio, parenthesizedTft),
                title = "残響散歌",
                artist = "Aimer",
                album = "",
                durationMs = 184_952L,
                versionReferenceTitle = "残響散歌 - From THE FIRST TAKE"
            )
        )
        // Reverse direction: studio target must also reject TFT candidate.
        assertNull(
            CandidateMatcher.pickBest(
                candidates = listOf(parenthesizedTft),
                title = "残響散歌",
                artist = "Aimer",
                album = "",
                durationMs = 184_952L
            )
        )
    }

    private data class Candidate(
        override val matchedTitle: String,
        override val matchedArtist: String,
        override val matchedAlbum: String?,
        override val matchedDurationMs: Long?,
        override val providerTrackId: String? = null,
        override val isrc: String? = null
    ) : SearchCandidate
}
