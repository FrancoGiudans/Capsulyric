package com.example.islandlyrics.feature.main.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsPlaybackBackgroundTest {
    @Test
    fun coverToneUsesPerceptualBrightnessAcrossAllSampledPixels() {
        val white = 0xFFFFFFFF.toInt()
        val red = 0xFFFF0000.toInt()
        val black = 0xFF000000.toInt()
        val light = playbackCoverTone(intArrayOf(white, white, white, red))
        val middle = playbackCoverTone(intArrayOf(white, red, black))
        val dark = playbackCoverTone(intArrayOf(red, black))

        assertTrue(light.scrimAlpha in 0.26f..0.30f)
        assertTrue(middle.scrimAlpha in 0.12f..0.16f)
        assertTrue(dark.scrimAlpha in 0.04f..0.08f)
        assertEquals(0f, light.brightnessBias, 0f)
        assertEquals(0f, middle.brightnessBias, 0f)
        assertTrue(dark.brightnessBias in (8f / 255f)..(12f / 255f))
    }

    @Test
    fun coverToneTreatsTransparentPixelsAsDark() {
        val tone = playbackCoverTone(intArrayOf(0x00FFFFFF))

        assertEquals(0.04f, tone.scrimAlpha, 0f)
        assertEquals(12f / 255f, tone.brightnessBias, 0f)
    }

    @Test
    fun coverPreblurPreservesASolidOpaqueColorToTheEdges() {
        val color = 0xFFA04020.toInt()
        val texture = blurPlaybackCover(IntArray(64 * 64) { color }, 64)

        assertTrue(texture.all { it == color })
    }

    @Test
    fun coverPreblurSoftensColorBoundariesWithoutLeakingHiddenRgb() {
        val size = 16
        val input = IntArray(size * size) { index ->
            when {
                index % size < size / 2 && index / size < size / 2 -> 0xFFFF0000.toInt()
                else -> 0x000000FF
            }
        }
        val texture = blurPlaybackCover(input, size)

        assertEquals(0xFFFF0000.toInt(), texture.first())
        assertEquals(0xFF000000.toInt(), texture.last())
        assertTrue(texture[size * 8 + 8] shr 16 and 255 in 1..254)
        assertTrue(texture.all { (it and 0xFFFF) == 0 && (it ushr 24) == 255 })
    }
}
