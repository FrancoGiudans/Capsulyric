package com.example.islandlyrics.ui.overlay.config

import com.example.islandlyrics.ui.overlay.display.OverlayDisplayConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapsuleRenderModeTest {
    @Test
    fun fullLyricMode_onlyUsesSuperIslandSelection() {
        val config = fullLyricConfig()
        assertTrue(config.isSuperIslandFullLyricMode)
        assertFalse(config.copy(superIslandLyricMode = "standard").isSuperIslandFullLyricMode)
        assertFalse(config.copy(capsuleRenderMode = CapsuleRenderMode.LIVE_UPDATE).isSuperIslandFullLyricMode)
    }

    @Test
    fun fullLyrics_useBothColumnsAndActualCoverAvailability() {
        val config = fullLyricConfig()
        assertEquals(26, config.maxDisplayWeight(16, hasAlbumArt = true))
        assertEquals(30, config.maxDisplayWeight(16, hasAlbumArt = false))
        assertEquals(12, config.fullLyricLeftTextWeight(hasAlbumArt = true))
        assertEquals(16, config.fullLyricLeftTextWeight(hasAlbumArt = false))
    }

    @Test
    fun fullLyrics_withoutCoverUseNoCoverBudgetEvenWhenArtworkExists() {
        val config = fullLyricConfig().copy(superIslandFullLyricShowLeftCover = false)
        assertEquals(30, config.maxDisplayWeight(16, hasAlbumArt = true))
    }

    @Test
    fun liveUpdate_keepsItsOwnBudgetWhenFullLyricsAreSaved() {
        val config = fullLyricConfig().copy(capsuleRenderMode = CapsuleRenderMode.LIVE_UPDATE)
        assertEquals(10, config.maxDisplayWeight(16, hasAlbumArt = true))
    }

    private fun fullLyricConfig() = OverlayDisplayConfig(
        disableScrolling = false,
        lyricTextDisplayMode = LyricTextDisplayMode.LYRIC,
        capsuleRenderMode = CapsuleRenderMode.XIAOMI_SUPER_ISLAND,
        superIslandLyricMode = "full",
        superIslandRightTextWeight = 14,
        superIslandFullLyricShowLeftCover = true,
        superIslandLeftWithCoverTextWeight = 12,
        superIslandLeftNoCoverTextWeight = 16,
        liveUpdateTextLimitsEnabled = true,
        liveUpdateTextWeight = 10,
        lyricVisualCenterEnabled = true
    )
}
