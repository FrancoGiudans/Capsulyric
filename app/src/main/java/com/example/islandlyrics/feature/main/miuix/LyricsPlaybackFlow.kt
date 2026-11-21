/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.main.miuix

import android.animation.ValueAnimator
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val FlowFrameIntervalNanos = 33_333_333L

@Composable
internal fun LyricsPlaybackBackground(
    appearance: PlaybackCoverAppearance?,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        !LocalView.current.isHardwareAccelerated || !ValueAnimator.areAnimatorsEnabled()
    ) {
        StaticPlaybackBackground(appearance?.colors, modifier)
        return
    }

    val flowTime = remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (playing && appearance?.flowTexture != null) {
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val accumulatedTime = flowTime.longValue
                val startTime = withFrameNanos { it }
                while (isActive) {
                    withFrameNanos { frameTime ->
                        val tick = (frameTime - startTime) / FlowFrameIntervalNanos
                        flowTime.longValue = accumulatedTime + tick * FlowFrameIntervalNanos
                    }
                }
            }
        }
    }
    Crossfade(appearance, modifier.fillMaxSize(), tween(450), label = "playback cover flow") { target ->
        if (target?.flowTexture != null) {
            FlowPlaybackBackground(target, flowTime)
        } else {
            StaticPlaybackBackground(target?.colors)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun FlowPlaybackBackground(appearance: PlaybackCoverAppearance, time: LongState) {
    val shader = remember(appearance) {
        val texture = appearance.flowTexture ?: return@remember null
        try {
            RuntimeShader(PlaybackFlowShader).apply {
                val bitmapShader = BitmapShader(texture, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
                bitmapShader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
                setInputShader("cover", bitmapShader)
                setFloatUniform("textureSide", PlaybackCoverSampleSize.toFloat())
                setFloatUniform("scrim", appearance.scrimAlpha)
            }
        } catch (_: IllegalArgumentException) {
            null
        }
    }
    if (shader == null) {
        StaticPlaybackBackground(appearance.colors)
        return
    }
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(Modifier.fillMaxSize()) {
        val seconds = time.longValue / 1_000_000_000.0
        val inverseShortSide = 1f / minOf(size.width, size.height).coerceAtLeast(1f)
        shader.setFloatUniform("viewport", size.width / 2f, size.height / 2f, inverseShortSide)
        shader.setFlowLayer("layerA", seconds, period = 18.0, zoom = 1.6, direction = 1, initialAngle = 0.0)
        shader.setFlowLayer("layerB", seconds, period = 27.0, zoom = 1.9, direction = -1, initialAngle = 1.1)
        shader.setFlowLayer("layerC", seconds, period = 38.0, zoom = 2.2, direction = 1, initialAngle = 2.4)
        drawRect(brush)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun RuntimeShader.setFlowLayer(
    name: String,
    seconds: Double,
    period: Double,
    zoom: Double,
    direction: Int,
    initialAngle: Double,
) {
    val phase = seconds % period / period * 2.0 * PI
    val angle = initialAngle + direction * (phase + 0.22 * sin(phase))
    setFloatUniform(
        name,
        (cos(angle) / zoom).toFloat(),
        (sin(angle) / zoom).toFloat(),
        (0.21 * cos(phase + initialAngle)).toFloat(),
        (0.17 * sin(phase + initialAngle)).toFloat(),
    )
}

private const val PlaybackFlowShader = """
    uniform shader cover;
    uniform float textureSide;
    uniform float3 viewport;
    uniform float4 layerA;
    uniform float4 layerB;
    uniform float4 layerC;
    uniform float scrim;

    float3 sampleCover(float2 position, float4 transform) {
        float2 rotated = float2(
            position.x * transform.x - position.y * transform.y,
            position.x * transform.y + position.y * transform.x
        );
        return float3(cover.eval((rotated + transform.zw + 0.5) * textureSide).rgb);
    }

    half4 main(float2 position) {
        float2 uv = (position - viewport.xy) * viewport.z;
        float3 rgb = sampleCover(uv, layerA) * 0.45
                   + sampleCover(uv, layerB) * 0.33
                   + sampleCover(uv, layerC) * 0.22;
        const float3 lumaWeights = float3(0.2126, 0.7152, 0.0722);
        float3 linearRgb = toLinearSrgb(rgb);
        linearRgb = clamp(mix(float3(dot(linearRgb, lumaWeights)), linearRgb, 1.35), 0.0, 1.0);
        linearRgb *= 1.0 - scrim;

        // Compress bright colors without desaturating them against the lyric text.
        float luma = dot(linearRgb, lumaWeights);
        float highlights = max(0.0, luma - 0.025);
        float limited = min(luma, 0.025) + 0.021 * highlights / (0.021 + highlights);
        linearRgb *= limited / max(luma, 0.0001);

        float noise = fract(52.9829189 * fract(dot(floor(position), float2(0.06711056, 0.00583715)))) - 0.5;
        return half4(clamp(fromLinearSrgb(linearRgb) + noise / 255.0, 0.0, 1.0), 1.0);
    }
"""
