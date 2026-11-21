package com.example.islandlyrics.feature.main.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsPlaybackBackgroundTest {
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
