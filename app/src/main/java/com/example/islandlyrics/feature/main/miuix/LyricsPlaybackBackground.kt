/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.main.miuix

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.example.islandlyrics.core.theme.ensureColorContrast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.min

internal const val PlaybackCoverSampleSize = 64
private const val CoverBlurRadius = 6
private const val CoverHueGroups = 12
private const val BackgroundContrast = 8.0

internal data class PlaybackCoverAppearance(
    val colors: List<Int?>,
    val flowTexture: Bitmap?,
    val scrimAlpha: Float,
)

@Composable
internal fun rememberPlaybackCoverAppearance(cover: Bitmap?): State<PlaybackCoverAppearance?> =
    produceState<PlaybackCoverAppearance?>(null, cover) {
        value = if (cover == null) null else withContext(Dispatchers.Default) {
            try {
                preparePlaybackCover(cover)
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: IllegalStateException) {
                null
            }
        }
    }

internal fun playbackAlbumAccent(colors: List<Int?>?): Color {
    val hsl = FloatArray(3)
    val sample = colors?.filterNotNull()?.maxByOrNull {
        ColorUtils.colorToHSL(it, hsl)
        hsl[1]
    } ?: return Color.White
    // Match the brightest background that the palette can produce against white.
    val brightestBackground = 0xFF515151.toInt()
    return Color(ensureColorContrast(sample, brightestBackground, Color.White.toArgb(), 4.5, 0.24f))
}

@Composable
internal fun StaticPlaybackBackground(colors: List<Int?>?, modifier: Modifier = Modifier) {
    val surface = MiuixTheme.colorScheme.surface
    val textColor = Color.White
    val palette = remember(colors, surface, textColor) {
        List(4) { index ->
            val sampled = colors?.get(index) ?: return@List surface
            Color(ensureColorContrast(sampled, textColor.toArgb(), surface.toArgb(), BackgroundContrast, 0.06f))
        }
    }
    val topLeft by animateColorAsState(palette[0], tween(450), label = "cover top left")
    val topRight by animateColorAsState(palette[1], tween(450), label = "cover top right")
    val bottomLeft by animateColorAsState(palette[2], tween(450), label = "cover bottom left")
    val bottomRight by animateColorAsState(palette[3], tween(450), label = "cover bottom right")
    Box(modifier.fillMaxSize().drawWithCache {
        val gradient = Brush.horizontalGradient(listOf(bottomLeft, bottomRight))
        onDrawBehind { drawRect(gradient) }
    }) {
        Box(
            Modifier.matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val gradient = Brush.horizontalGradient(listOf(topLeft, topRight))
                    val mask = Brush.verticalGradient(listOf(Color.Black, Color.Transparent))
                    onDrawBehind {
                        drawRect(gradient)
                        drawRect(mask, blendMode = BlendMode.DstIn)
                    }
                },
        )
    }
}

private fun preparePlaybackCover(bitmap: Bitmap): PlaybackCoverAppearance? {
    val software = if (bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
    } else {
        bitmap
    }
    val size = PlaybackCoverSampleSize
    val sample = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(size * size)
    try {
        val side = min(software.width, software.height)
        val left = (software.width - side) / 2
        val top = (software.height - side) / 2
        Canvas(sample).drawBitmap(
            software,
            Rect(left, top, left + side, top + side),
            Rect(0, 0, size, size),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        sample.getPixels(pixels, 0, size, 0, 0, size, size)
    } finally {
        sample.recycle()
        if (software !== bitmap) software.recycle()
    }
    val colors = sampleCoverColors(pixels)
    if (colors.all { it == null }) return null
    val texture = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val blurred = blurPlaybackCover(pixels, size)
        Bitmap.createBitmap(blurred, size, size, Bitmap.Config.ARGB_8888)
    } else {
        null
    }
    val meanLuminance = colors.filterNotNull().map(ColorUtils::calculateLuminance).average()
    val scrimAlpha = 0.28f + ((meanLuminance - 0.35) / 0.5).coerceIn(0.0, 1.0).toFloat() * 0.10f
    return PlaybackCoverAppearance(colors, texture, scrimAlpha)
}

private fun sampleCoverColors(pixels: IntArray): List<Int?> =
    List(4) { quadrant ->
        val half = PlaybackCoverSampleSize / 2
        val xStart = (quadrant % 2) * half
        val yStart = (quadrant / 2) * half
        val average = CoverColorGroup()
        val hues = Array(CoverHueGroups) { CoverColorGroup() }
        val hsl = FloatArray(3)
        for (y in yStart until yStart + half) {
            for (x in xStart until xStart + half) {
                val pixel = pixels[y * PlaybackCoverSampleSize + x]
                val alpha = android.graphics.Color.alpha(pixel).toFloat()
                if (alpha == 0f) continue
                average.add(pixel, alpha)
                ColorUtils.colorToHSL(pixel, hsl)
                val saturation = hsl[1]
                if (saturation >= 0.24f && hsl[2] in 0.12f..0.9f) {
                    val index = (hsl[0] / (360f / CoverHueGroups)).toInt().coerceIn(0, CoverHueGroups - 1)
                    hues[index].add(pixel, alpha * saturation * saturation)
                }
            }
        }
        val dominant = hues.maxBy { it.weight }
        if (dominant.weight > average.weight * 0.025f) {
            dominant.color()?.let {
                ColorUtils.colorToHSL(it, hsl)
                hsl[1] = (hsl[1] * 1.16f).coerceAtMost(1f)
                ColorUtils.HSLToColor(hsl)
            }
        } else {
            average.color()
        }
    }

internal fun blurPlaybackCover(pixels: IntArray, size: Int): IntArray {
    val horizontal = IntArray(pixels.size)
    val blurred = IntArray(pixels.size)
    val taps = 2 * CoverBlurRadius + 1
    for (y in 0 until size) {
        for (x in 0 until size) {
            var red = 0
            var green = 0
            var blue = 0
            for (tap in -CoverBlurRadius..CoverBlurRadius) {
                val pixel = pixels[y * size + (x + tap).coerceIn(0, size - 1)]
                val alpha = pixel ushr 24
                red += (pixel shr 16 and 255) * alpha
                green += (pixel shr 8 and 255) * alpha
                blue += (pixel and 255) * alpha
            }
            val divisor = taps * 255
            horizontal[y * size + x] = (red / divisor shl 16) or (green / divisor shl 8) or (blue / divisor)
        }
    }
    for (y in 0 until size) {
        for (x in 0 until size) {
            var red = 0
            var green = 0
            var blue = 0
            for (tap in -CoverBlurRadius..CoverBlurRadius) {
                val pixel = horizontal[(y + tap).coerceIn(0, size - 1) * size + x]
                red += pixel shr 16 and 255
                green += pixel shr 8 and 255
                blue += pixel and 255
            }
            blurred[y * size + x] = (255 shl 24) or (red / taps shl 16) or (green / taps shl 8) or (blue / taps)
        }
    }
    return blurred
}

private class CoverColorGroup {
    var weight = 0f
        private set
    private var red = 0f
    private var green = 0f
    private var blue = 0f

    fun add(pixel: Int, contribution: Float) {
        weight += contribution
        red += android.graphics.Color.red(pixel) * contribution
        green += android.graphics.Color.green(pixel) * contribution
        blue += android.graphics.Color.blue(pixel) * contribution
    }

    fun color(): Int? = if (weight > 0f) {
        android.graphics.Color.rgb((red / weight).toInt(), (green / weight).toInt(), (blue / weight).toInt())
    } else {
        null
    }
}
