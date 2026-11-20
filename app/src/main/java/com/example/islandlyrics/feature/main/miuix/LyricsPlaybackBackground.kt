/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.main.miuix

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.example.islandlyrics.core.theme.ensureColorContrast
import com.example.islandlyrics.ui.miuix.blur.LocalMiuixBlurEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.min

private const val CoverSampleSize = 32
private const val CoverHueGroups = 12

@Composable
internal fun LyricsPlaybackBackground(cover: Bitmap?, modifier: Modifier = Modifier) {
    val surface = MiuixTheme.colorScheme.surface
    val textColor = MiuixTheme.colorScheme.onSurface
    val colors by produceState<List<Int?>?>(null, cover) {
        value = if (cover == null) null else withContext(Dispatchers.Default) {
            try {
                sampleCoverColors(cover)
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: IllegalStateException) {
                null
            }
        }
    }
    val palette = remember(colors, surface, textColor) {
        List(4) { index ->
            val sampled = colors?.get(index) ?: return@List surface
            Color(ensureColorContrast(sampled, textColor.toArgb(), surface.toArgb(), 4.5, 0.06f))
        }
    }
    val topLeft by animateColorAsState(palette[0], tween(450), label = "cover top left")
    val topRight by animateColorAsState(palette[1], tween(450), label = "cover top right")
    val bottomLeft by animateColorAsState(palette[2], tween(450), label = "cover bottom left")
    val bottomRight by animateColorAsState(palette[3], tween(450), label = "cover bottom right")
    val blurEnabled = LocalMiuixBlurEnabled.current && isRenderEffectSupported()
    val backdrop = rememberLayerBackdrop()
    val mix = remember(surface) {
        BlurColors(blendColors = listOf(BlendColorEntry(surface.copy(alpha = 0.045f), BlurBlendMode.SrcOver)))
    }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .then(if (blurEnabled) Modifier.layerBackdrop(backdrop) else Modifier)
                .drawWithCache {
                    val gradient = Brush.horizontalGradient(listOf(bottomLeft, bottomRight))
                    onDrawBehind { drawRect(gradient) }
                }
        ) {
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
                    }
            )
        }
        if (blurEnabled) {
            Box(
                Modifier.matchParentSize().textureBlur(
                    backdrop = backdrop,
                    shape = RectangleShape,
                    blurRadius = 32f,
                    colors = mix,
                )
            )
        }
    }
}

private fun sampleCoverColors(bitmap: Bitmap): List<Int?>? {
    val software = if (bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
    } else {
        bitmap
    }
    val side = min(software.width, software.height)
    val crop = Bitmap.createBitmap(software, (software.width - side) / 2, (software.height - side) / 2, side, side)
    val sample = Bitmap.createScaledBitmap(crop, CoverSampleSize, CoverSampleSize, true)
    val pixels = IntArray(CoverSampleSize * CoverSampleSize)
    try {
        sample.getPixels(pixels, 0, CoverSampleSize, 0, 0, CoverSampleSize, CoverSampleSize)
    } finally {
        if (sample !== crop) sample.recycle()
        if (crop !== software) crop.recycle()
        if (software !== bitmap) software.recycle()
    }

    return List(4) { quadrant ->
        val half = CoverSampleSize / 2
        val xStart = (quadrant % 2) * half
        val yStart = (quadrant / 2) * half
        val average = CoverColorGroup()
        val hues = Array(CoverHueGroups) { CoverColorGroup() }
        val hsl = FloatArray(3)
        for (y in yStart until yStart + half) {
            for (x in xStart until xStart + half) {
                val pixel = pixels[y * CoverSampleSize + x]
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
