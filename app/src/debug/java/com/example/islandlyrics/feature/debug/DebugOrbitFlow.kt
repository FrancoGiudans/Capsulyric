/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.debug

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas as BitmapCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.islandlyrics.R
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.Text

private const val TextureSide = 6
private const val TileSide = 10
private const val SourceSampleSide = TextureSide * TileSide
private const val AccentChromaThreshold = 30
private const val FrameIntervalNanos = 33_333_333L
private const val FlowCycleNanos = 1_026_000_000_000L

internal data class DebugOrbitCover(val texture: Bitmap, val baseColor: Int)

internal fun prepareDebugOrbitCover(cover: Bitmap): DebugOrbitCover? {
    if (cover.isRecycled) return null
    val source = if (cover.config == Bitmap.Config.HARDWARE) {
        cover.copy(Bitmap.Config.ARGB_8888, false) ?: return null
    } else {
        cover
    }
    val sample = Bitmap.createBitmap(SourceSampleSide, SourceSampleSide, Bitmap.Config.ARGB_8888)
    val samplePixels = IntArray(SourceSampleSide * SourceSampleSide)
    try {
        BitmapCanvas(sample).apply {
            drawColor(AndroidColor.BLACK)
            drawBitmap(source, null, Rect(0, 0, SourceSampleSide, SourceSampleSide), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        sample.getPixels(samplePixels, 0, SourceSampleSide, 0, 0, SourceSampleSide, SourceSampleSide)
    } finally {
        sample.recycle()
        if (source !== cover) source.recycle()
    }

    val hsl = FloatArray(3)
    val neutralTiles = mutableListOf<Int>()
    val tiles = IntArray(TextureSide * TextureSide) { index ->
        val color = chromaWeightedTile(samplePixels, index % TextureSide, index / TextureSide)
        val red = AndroidColor.red(color)
        val green = AndroidColor.green(color)
        val blue = AndroidColor.blue(color)
        ColorUtils.RGBToHSL(red, green, blue, hsl)
        val chroma = maxOf(red, green, blue) - minOf(red, green, blue)
        if (chroma >= AccentChromaThreshold) {
            hsl[1] = hsl[1].coerceIn(0.65f, 0.90f)
            hsl[2] = hsl[2].coerceIn(0.36f, 0.52f)
            ColorUtils.HSLToColor(hsl)
        } else {
            hsl[1] = hsl[1].coerceAtMost(0.14f)
            if (hsl[2] > 0f) hsl[2] = hsl[2].coerceIn(0.08f, 0.16f)
            ColorUtils.HSLToColor(hsl).also(neutralTiles::add)
        }
    }
    val texture = Bitmap.createBitmap(tiles, TextureSide, TextureSide, Bitmap.Config.ARGB_8888)
    return DebugOrbitCover(texture, darkBaseColor(neutralTiles.ifEmpty { tiles.asList() }))
}

private fun chromaWeightedTile(pixels: IntArray, column: Int, row: Int): Int {
    var redSum = 0
    var greenSum = 0
    var blueSum = 0
    var totalChroma = 0
    var graySum = 0
    val start = row * TileSide * SourceSampleSide + column * TileSide
    for (y in 0 until TileSide) {
        val rowStart = start + y * SourceSampleSide
        for (x in 0 until TileSide) {
            val color = pixels[rowStart + x]
            val red = AndroidColor.red(color)
            val green = AndroidColor.green(color)
            val blue = AndroidColor.blue(color)
            val chroma = maxOf(red, green, blue) - minOf(red, green, blue)
            redSum += red * chroma
            greenSum += green * chroma
            blueSum += blue * chroma
            totalChroma += chroma
            graySum += red
        }
    }
    if (totalChroma == 0) {
        val gray = graySum / (TileSide * TileSide)
        return AndroidColor.rgb(gray, gray, gray)
    }
    return AndroidColor.rgb(redSum / totalChroma, greenSum / totalChroma, blueSum / totalChroma)
}

private fun darkBaseColor(tiles: List<Int>): Int {
    val hsl = FloatArray(3)
    ColorUtils.RGBToHSL(
        tiles.sumOf { AndroidColor.red(it) } / tiles.size,
        tiles.sumOf { AndroidColor.green(it) } / tiles.size,
        tiles.sumOf { AndroidColor.blue(it) } / tiles.size,
        hsl,
    )
    hsl[1] = hsl[1].coerceAtMost(0.24f)
    hsl[2] = (hsl[2] * 0.75f).coerceIn(0.08f, 0.16f)
    return ColorUtils.HSLToColor(hsl)
}

@Composable
internal fun DebugOrbitFlow(cover: DebugOrbitCover, modifier: Modifier) {
    val hardwareAccelerated = LocalView.current.isHardwareAccelerated
    val shader = remember(cover, hardwareAccelerated) {
        if (!hardwareAccelerated) return@remember null
        try {
            RuntimeShader(OrbitFlowShader).apply {
                val texture = BitmapShader(cover.texture, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
                texture.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
                setInputShader("uCover", texture)
                setColorUniform("uBaseColor", cover.baseColor)
                setFloatUniform("uTextureSide", TextureSide.toFloat())
            }
        } catch (error: IllegalArgumentException) {
            Log.e("BackgroundBlurDebug", "Could not initialize 6×6 orbit shader", error)
            null
        }
    }
    if (shader == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.debug_background_blur_shader_unavailable))
        }
        return
    }

    val time = rememberDebugBlurTime(FlowCycleNanos)
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(modifier) {
        shader.setFloatUniform("uSize", size.width, size.height)
        shader.setFloatUniform("uTime", time.longValue / 1_000_000_000f)
        drawRect(brush)
    }
}

@Composable
internal fun rememberDebugBlurTime(cycleNanos: Long): LongState {
    val time = remember(cycleNanos) { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, cycleNanos) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val accumulatedTime = time.longValue
            val startedAt = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { frameTime ->
                    val elapsed = (frameTime - startedAt) / FrameIntervalNanos * FrameIntervalNanos
                    time.longValue = (accumulatedTime + elapsed) % cycleNanos
                }
            }
        }
    }
    return time
}

private const val OrbitFlowShader = """
    uniform shader uCover;
    layout(color) uniform half4 uBaseColor;
    uniform float uTextureSide;
    uniform float2 uSize;
    uniform float uTime;

    const float3 lumaWeights = float3(0.2126, 0.7152, 0.0722);

    float3 sampleCubicBSpline(float2 texel) {
        float2 grid = texel - 0.5;
        float2 cell = floor(grid);
        float2 fraction = grid - cell;
        float2 squared = fraction * fraction;
        float2 cubed = squared * fraction;
        float2 inverse = 1.0 - fraction;
        float2 w0 = inverse * inverse * inverse / 6.0;
        float2 w1 = (4.0 - 6.0 * squared + 3.0 * cubed) / 6.0;
        float2 w2 = (1.0 + 3.0 * fraction + 3.0 * squared - 3.0 * cubed) / 6.0;
        float2 w3 = cubed / 6.0;

        // Merge each pair of taps into one bilinear sample per axis.
        float2 upperWeight = w2 + w3;
        float2 lower = cell - 0.5 + w1 / (w0 + w1);
        float2 upper = cell + 1.5 + w3 / upperWeight;
        float3 topLeft = float3(uCover.eval(lower).rgb);
        float3 topRight = float3(uCover.eval(float2(upper.x, lower.y)).rgb);
        float3 bottomLeft = float3(uCover.eval(float2(lower.x, upper.y)).rgb);
        float3 bottomRight = float3(uCover.eval(upper).rgb);
        return mix(
            mix(topLeft, topRight, upperWeight.x),
            mix(bottomLeft, bottomRight, upperWeight.x),
            upperWeight.y
        );
    }

    float3 orbitSample(float2 uv, float phase, float2 center, float2 axes, float angleOffset, float zoom) {
        float2 wave = sin(uv.yx * float2(2.7, 2.1) + float2(phase, angleOffset - phase));
        float2 warped = uv + float2(0.046, 0.040) * wave;
        float angle = angleOffset + phase + 0.20 * sin(phase);
        float2 unit = float2(cos(angle), sin(angle));
        float2 rotated = float2(warped.x * unit.x - warped.y * unit.y, warped.x * unit.y + warped.y * unit.x);
        float2 orbit = center + axes * float2(cos(phase), sin(phase));
        float2 texel = (rotated / zoom + orbit + 0.5) * uTextureSide;
        float3 rgb = sampleCubicBSpline(texel);
        float low = min(rgb.r, min(rgb.g, rgb.b));
        float high = max(rgb.r, max(rgb.g, rgb.b));
        // The base supplies the gray tones, so neutral samples add little light through Screen.
        return rgb * mix(0.12, 1.0, smoothstep(0.02, 0.16, high - low));
    }

    float3 compressHighlights(float3 rgb) {
        const float knee = 0.13;
        const float peak = 0.26;
        const float shoulder = peak - knee;
        float3 linearRgb = toLinearSrgb(rgb);
        float luma = dot(linearRgb, lumaWeights);
        float excess = max(luma - knee, 0.0);
        float compressed = luma - excess + shoulder * excess / (shoulder + excess);
        return fromLinearSrgb(linearRgb * (compressed / max(luma, 0.00001)));
    }

    half4 main(float2 position) {
        const float tau = 6.2831853;
        float2 uv = (position - uSize * 0.5) / max(min(uSize.x, uSize.y), 1.0);
        float3 a = orbitSample(uv, uTime * (tau / 18.0), float2(-0.24, -0.16), float2(0.36, 0.40), 0.0, 1.55);
        float3 b = orbitSample(uv, -uTime * (tau / 27.0) + 2.1, float2(0.26, -0.06), float2(0.32, 0.36), 0.9, 1.85);
        float3 c = orbitSample(uv, uTime * (tau / 38.0) + 4.2, float2(0.02, 0.24), float2(0.39, 0.35), 2.3, 2.2);
        float3 remaining = (1.0 - float3(uBaseColor.rgb)) * (1.0 - a * 0.38) * (1.0 - b * 0.30) * (1.0 - c * 0.24);
        return half4(compressHighlights(1.0 - remaining), 1.0);
    }
"""
