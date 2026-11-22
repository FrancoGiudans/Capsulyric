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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
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
    AnimatedContent(
        targetState = appearance,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            // Keep an opaque background behind the incoming cover until it has fully faded in.
            (fadeIn(tween(450)) togetherWith ExitTransition.KeepUntilTransitionsFinished).using(null)
        },
        label = "playback cover flow",
    ) { target ->
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
                setFloatUniform("tone", appearance.tone.scrimAlpha, appearance.tone.brightnessBias)
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
        val shortSide = minOf(size.width, size.height)
        val longSide = maxOf(size.width, size.height)
        val inverseScale = 1f / (shortSide * 0.35f + longSide * 0.65f).coerceAtLeast(1f)
        shader.setFloatUniform("viewport", size.width / 2f, size.height / 2f, inverseScale)
        shader.setFloatUniform("wavePhase", ((seconds % 24.0) / 24.0 * 2.0 * PI).toFloat())
        // 宽模糊核已消除锐利边缘，zoom 回调至 1.55~1.95 获取更自然的色域过渡
        shader.setFlowLayer("layerA", seconds, period = 20.0, zoom = 1.55, direction = 1, initialAngle = 0.0)
        shader.setFlowLayer("layerB", seconds, period = 29.0, zoom = 1.75, direction = -1, initialAngle = 1.3)
        shader.setFlowLayer("layerC", seconds, period = 41.0, zoom = 1.95, direction = 1, initialAngle = 2.6)
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
    val angle = initialAngle + direction * (phase + 0.18 * sin(phase))
    setFloatUniform(
        name,
        (cos(angle) / zoom).toFloat(),
        (sin(angle) / zoom).toFloat(),
        (0.09 * cos(phase + initialAngle)).toFloat(),
        (0.08 * sin(phase + initialAngle)).toFloat(),
    )
}

private const val PlaybackFlowShader = """
    uniform shader cover;
    uniform float textureSide;
    uniform float3 viewport;
    uniform float wavePhase;
    uniform float4 layerA;
    uniform float4 layerB;
    uniform float4 layerC;
    uniform float2 tone;

    // 12-Tap 黄金角沃格尔圆盘高斯采样：连续覆盖宽半径且无网格/方向性，彻底化开高反差边缘
    float3 sampleCoverLinear(float2 position, float4 transform) {
        float2 rotated = float2(
            position.x * transform.x - position.y * transform.y,
            position.x * transform.y + position.y * transform.x
        );
        float2 center = rotated + transform.zw + 0.5;

        float3 sum = float3(0.0);
        float weightSum = 0.0;
        for (int i = 0; i < 12; i++) {
            float fi = float(i) + 0.5;
            float r = 0.18 * sqrt(fi / 12.0);
            float theta = float(i) * 2.39996323;
            float2 offset = float2(cos(theta), sin(theta)) * r;
            float w = exp(-2.2 * (fi / 12.0));
            sum += toLinearSrgb(float3(cover.eval((center + offset) * textureSide).rgb)) * w;
            weightSum += w;
        }
        return sum / weightSum;
    }

    half4 main(float2 position) {
        float2 rawUv = (position - viewport.xy) * viewport.z;
        float2 screenUv = clamp(position / max(viewport.xy * 2.0, float2(1.0)), 0.0, 1.0);

        float2 warp = float2(
            sin(rawUv.y * 1.6 + wavePhase) + 0.4 * cos(rawUv.x * 1.3 - wavePhase),
            cos(rawUv.x * 1.6 - wavePhase) + 0.4 * sin(rawUv.y * 1.3 + wavePhase)
        ) * 0.045;
        float2 uv = rawUv + warp;

        float3 colA = sampleCoverLinear(uv, layerA);
        float3 colB = sampleCoverLinear(uv, layerB);
        float3 colC = sampleCoverLinear(uv, layerC);

        // 全屏余弦平滑过渡（C∞ 连续），消除 smoothstep 边界处的二阶导数折带
        float diag = clamp(screenUv.x * 0.58 + screenUv.y * 0.42, 0.0, 1.0);
        float splitWeight = 0.5 - 0.5 * cos(diag * 3.14159265);
        float3 linearRgb = mix(colA, colB, splitWeight);

        float glowCurve = 0.5 - 0.5 * cos(screenUv.y * 3.14159265);
        float glowMask = mix(0.18, 0.36, glowCurve);
        float3 glow = colC * glowMask;
        linearRgb = 1.0 - (1.0 - linearRgb) * (1.0 - glow);

        const float3 lumaWeights = float3(0.2126, 0.7152, 0.0722);
        float rawLuma = dot(linearRgb, lumaWeights);
        linearRgb = clamp(mix(float3(rawLuma), linearRgb, 1.55), 0.0, 1.0);

        float luma = dot(linearRgb, lumaWeights);
        float maxCh = max(linearRgb.r, max(linearRgb.g, linearRgb.b));
        float metric = mix(luma, maxCh, 0.35);
        const float knee = 0.09;
        const float headroom = 0.16;
        float highlights = max(0.0, metric - knee);
        float limited = min(metric, knee) + headroom * highlights / (headroom + highlights);
        linearRgb *= limited / max(metric, 0.0001);

        // 正交双路 IGN 三角分布抖动（TPDF, ±1.5 LSB），消除平行条纹并抹平暗部 8-bit 色阶断层
        float2 pixel = floor(position);
        float ign1 = fract(52.9829189 * fract(dot(pixel, float2(0.06711056, 0.00583715))));
        float ign2 = fract(52.9829189 * fract(dot(pixel, float2(0.00583715, 0.06711056))));
        float dither = (ign1 - ign2) * (1.5 / 255.0);

        float3 outputRgb = (fromLinearSrgb(linearRgb) + tone.y) * (1.0 - tone.x);
        return half4(clamp(outputRgb + dither, 0.0, 1.0), 1.0);
    }
"""
