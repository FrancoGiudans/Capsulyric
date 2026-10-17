package com.example.islandlyrics.ui.overlay.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapsuleRenderModeTest {
    @Test
    fun shouldDisableLyricScrolling_onlyForSuperIslandFullLyrics() {
        assertTrue(
            CapsuleRenderMode.shouldDisableLyricScrolling(
                CapsuleRenderMode.XIAOMI_SUPER_ISLAND,
                "full"
            )
        )
        assertFalse(
            CapsuleRenderMode.shouldDisableLyricScrolling(
                CapsuleRenderMode.XIAOMI_SUPER_ISLAND,
                "standard"
            )
        )
        assertFalse(
            CapsuleRenderMode.shouldDisableLyricScrolling(
                CapsuleRenderMode.LIVE_UPDATE,
                "full"
            )
        )
    }
}
