package com.example.islandlyrics.rules

import com.example.islandlyrics.lyrics.online.selection.CandidateMatcher
import com.example.islandlyrics.lyrics.online.selection.VersionTag
import com.example.islandlyrics.lyrics.online.selection.VersionTagDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserRuleMetadataResolutionTest {

    private val titleArtistRule = ParserRule(
        packageName = "com.apple.android.music",
        enabled = true,
        usesCarProtocol = false,
        separatorPattern = " - ",
        fieldOrder = FieldOrder.TITLE_ARTIST
    )

    private val tightHyphenRule = ParserRule(
        packageName = "com.tencent.qqmusic",
        enabled = true,
        usesCarProtocol = true,
        separatorPattern = "-",
        fieldOrder = FieldOrder.TITLE_ARTIST
    )

    @Test
    fun preservesPlayerArtistWhenTitleHasFromTheFirstTakeSubtitle() {
        val resolved = ParserRuleHelper.resolveTitleMetadata(
            rawTitle = "夜に駆ける - From THE FIRST TAKE",
            rawArtist = "YOASOBI",
            rule = titleArtistRule
        )

        assertFalse(resolved.parsed)
        assertEquals("夜に駆ける - From THE FIRST TAKE", resolved.title)
        assertEquals("YOASOBI", resolved.artist)
    }

    @Test
    fun preservesPlayerArtistForCommonDashSubtitlesAndHyphenatedTitles() {
        val remastered = ParserRuleHelper.resolveTitleMetadata(
            rawTitle = "Bohemian Rhapsody - Remastered 2011",
            rawArtist = "Queen",
            rule = titleArtistRule
        )
        assertFalse(remastered.parsed)
        assertEquals("Bohemian Rhapsody - Remastered 2011", remastered.title)
        assertEquals("Queen", remastered.artist)

        val antiHero = ParserRuleHelper.resolveTitleMetadata(
            rawTitle = "Anti-Hero",
            rawArtist = "Taylor Swift",
            rule = tightHyphenRule
        )
        assertFalse(antiHero.parsed)
        assertEquals("Anti-Hero", antiHero.title)
        assertEquals("Taylor Swift", antiHero.artist)
    }

    @Test
    fun stripsRedundantArtistFromTitleWhenItMatchesPlayerArtist() {
        val resolved = ParserRuleHelper.resolveTitleMetadata(
            rawTitle = "夜に駆ける - YOASOBI",
            rawArtist = "YOASOBI",
            rule = titleArtistRule
        )

        assertTrue(resolved.parsed)
        assertEquals("夜に駆ける", resolved.title)
        assertEquals("YOASOBI", resolved.artist)
    }

    @Test
    fun splitsTitleAndArtistWhenPlayerArtistIsMissingExceptForSubtitles() {
        val normalBluetooth = ParserRuleHelper.resolveTitleMetadata(
            rawTitle = "夜に駆ける - YOASOBI",
            rawArtist = "",
            rule = titleArtistRule
        )
        assertTrue(normalBluetooth.parsed)
        assertEquals("夜に駆ける", normalBluetooth.title)
        assertEquals("YOASOBI", normalBluetooth.artist)

        val subtitleWithoutArtist = ParserRuleHelper.resolveTitleMetadata(
            rawTitle = "夜に駆ける - From THE FIRST TAKE",
            rawArtist = "",
            rule = titleArtistRule
        )
        assertFalse(subtitleWithoutArtist.parsed)
        assertEquals("夜に駆ける - From THE FIRST TAKE", subtitleWithoutArtist.title)
        assertEquals("", subtitleWithoutArtist.artist)
    }

    @Test
    fun cleanTitleAndVersionTagHandleFromTheFirstTakeProperly() {
        assertEquals("夜に駆ける", CandidateMatcher.cleanTitle("夜に駆ける - From THE FIRST TAKE"))
        assertTrue(
            VersionTag.THE_FIRST_TAKE in VersionTagDetector.detect("夜に駆ける - From THE FIRST TAKE", "")
        )
        assertTrue(
            VersionTag.THE_FIRST_TAKE in VersionTagDetector.detect("夜に駆ける (From \"THE FIRST TAKE\")", "")
        )
    }
}
