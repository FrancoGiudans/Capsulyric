/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.main.miuix

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import com.example.islandlyrics.ui.overlay.model.WordProgressCalculator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun Modifier.blurLyricsAroundBars(
    top: Dp,
    bottomFade: State<Dp>,
    bottomWithControls: Dp,
    reach: Dp,
): Modifier {
    var height by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val horizontal = remember { RuntimeShader(PlaybackGaussianShader) }
    val vertical = remember { RuntimeShader(PlaybackGaussianShader) }
    val effect by remember(height, top, bottomFade, bottomWithControls, reach, density) {
        derivedStateOf {
            with(density) {
                if (height <= 0 || reach <= 0.dp) return@with null
                val firstVisibleY = top.toPx()
                val lastVisibleY = (height - bottomFade.value.toPx()).coerceAtLeast(firstVisibleY)
                val bottomBlurEdgeY = (height - bottomWithControls.toPx()).coerceAtLeast(firstVisibleY)
                // Use the same blur ramp in both passes and normalize the weights for premultiplied text.
                for (shader in listOf(horizontal, vertical)) {
                    shader.setFloatUniform("visibleBand", firstVisibleY, lastVisibleY)
                    shader.setFloatUniform("bottomBlurEdgeY", bottomBlurEdgeY)
                    shader.setFloatUniform("reach", reach.toPx())
                    shader.setFloatUniform("maxSigma", 6.dp.toPx().coerceAtMost(16f))
                }
                horizontal.setFloatUniform("axis", 1f, 0f)
                vertical.setFloatUniform("axis", 0f, 1f)
                RenderEffect.createChainEffect(
                    RenderEffect.createRuntimeShaderEffect(vertical, "lyrics"),
                    RenderEffect.createRuntimeShaderEffect(horizontal, "lyrics"),
                ).asComposeRenderEffect()
            }
        }
    }
    return onSizeChanged { height = it.height }.graphicsLayer { renderEffect = effect }
}

private const val PlaybackGaussianShader = """
    uniform shader lyrics;
    uniform float2 visibleBand;
    uniform float2 axis;
    uniform float bottomBlurEdgeY;
    uniform float reach;
    uniform float maxSigma;

    half4 main(float2 position) {
        float distance = min(position.y - visibleBand.x, visibleBand.y - position.y);
        if (distance < -3.0 * maxSigma || (axis.y != 0.0 && distance <= 0.0)) return half4(0.0);
        float blurDistance = min(position.y - visibleBand.x, bottomBlurEdgeY - position.y);
        float sigma = maxSigma * (1.0 - smoothstep(0.0, reach, blurDistance));
        if (sigma < 0.2) return lyrics.eval(position);

        float cutoff = ceil(3.0 * sigma);
        float exponent = -0.5 / (sigma * sigma);
        float ratioStep = exp(2.0 * exponent);
        float wa = exp(exponent);
        float ratio = wa * ratioStep;
        float4 sum = float4(lyrics.eval(position));
        float weight = 1.0;
        for (int tap = 1; tap < 48; tap += 2) {
            float a = float(tap);
            if (a > cutoff) break;
            float b = a + 1.0;
            float nextWeight = wa * ratio;
            float wb = b <= cutoff ? nextWeight : 0.0;
            float pair = wa + wb;
            float2 step = axis * ((a * wa + b * wb) / pair);
            sum += (float4(lyrics.eval(position + step)) + float4(lyrics.eval(position - step))) * pair;
            weight += 2.0 * pair;
            wa = nextWeight * ratio * ratioStep;
            ratio *= ratioStep * ratioStep;
        }
        return half4(sum / weight);
    }
"""

internal fun Modifier.fadeLyricsAroundBars(top: Dp, bottom: State<Dp>, feather: Dp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            if (size.height <= 0f) return@drawWithCache onDrawWithContent { drawContent() }
            val upperBar = (top.toPx() / size.height).coerceIn(0f, 1f)
            val lowerBar = (1f - bottom.value.toPx() / size.height).coerceIn(upperBar, 1f)
            val edge = (feather.toPx() / size.height).coerceIn(0f, (lowerBar - upperBar) / 2f)
            val mask = Brush.verticalGradient(
                upperBar to Color.Transparent,
                upperBar + edge to Color.Black,
                lowerBar - edge to Color.Black,
                lowerBar to Color.Transparent,
            )
            onDrawWithContent {
                drawContent()
                drawRect(mask, blendMode = BlendMode.DstIn)
            }
        }

@Composable
internal fun PlaybackLyricText(
    line: LyricLine,
    current: Boolean,
    position: State<Long?>,
    color: Color,
    sweepStrength: () -> Float = { 1f },
) {
    val timed = !line.syllables.isNullOrEmpty()
    val needsSweep = rememberUpdatedState(current)
    val textLayout = remember(line.text) { mutableStateOf<TextLayoutResult?>(null) }
    val onTextLayout = remember(textLayout) { { layout: TextLayoutResult -> textLayout.value = layout } }
    Text(
        text = line.text,
        modifier = if (timed) {
            Modifier.graphicsLayer {
                compositingStrategy = if (needsSweep.value) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
                .drawWithCache {
                    val layout = textLayout.value
                    val maskPaint = Paint().apply { blendMode = BlendMode.DstIn }
                    val feather = 7.dp.toPx()
                    onDrawWithContent {
                        drawContent()
                        if (!needsSweep.value) return@onDrawWithContent
                        val strength = sweepStrength()
                        if (strength == 0f) return@onDrawWithContent
                        val playbackMs = position.value ?: return@onDrawWithContent
                        val progress = WordProgressCalculator.compute(
                            line.text, line.startTime, line.endTime, line.syllables, playbackMs,
                        ) ?: return@onDrawWithContent
                        if (layout == null || playbackMs >= line.endTime || progress.syllables.all { it.progress >= 1f }) {
                            return@onDrawWithContent
                        }

                        // Punch the sweep into the shaped glyphs so both passes share the same text layout.
                        drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), maskPaint) }
                        val unsungAlpha = 1f - (1f - WordProgressCalculator.UNSUNG_ALPHA) * strength
                        drawRect(Color.White.copy(alpha = unsungAlpha))
                        var sungEnd = 0
                        for (syllable in progress.syllables) {
                            if (syllable.progress < 1f) break
                            sungEnd = syllable.charEnd.coerceAtMost(line.text.length)
                        }
                        if (sungEnd > 0) drawPath(layout.getPathForRange(0, sungEnd), Color.White)
                        for (syllable in progress.syllables) {
                            val start = syllable.charStart.coerceIn(sungEnd, line.text.length)
                            val end = syllable.charEnd.coerceIn(start, line.text.length)
                            if (end > start && syllable.progress > 0f) {
                                drawSyllableSweep(layout, start, end, syllable.progress, feather)
                            }
                        }
                        drawIntoCanvas { it.restore() }
                    }
                }
        } else {
            Modifier
        },
        style = MiuixTheme.textStyles.title2,
        fontWeight = FontWeight.SemiBold,
        color = color,
        onTextLayout = onTextLayout.takeIf { timed },
    )
}

private fun DrawScope.drawSyllableSweep(layout: TextLayoutResult, start: Int, end: Int, fraction: Float, feather: Float) {
    if (fraction >= 1f) {
        drawPath(layout.getPathForRange(start, end), Color.White)
        return
    }

    val firstLine = layout.getLineForOffset(start)
    val lastLine = layout.getLineForOffset(end - 1)
    val segments = (firstLine..lastLine).mapNotNull { line ->
        val from = maxOf(start, layout.getLineStart(line))
        val to = minOf(end, layout.getLineEnd(line, visibleEnd = true))
        if (to <= from) return@mapNotNull null
        val path = layout.getPathForRange(from, to)
        path to (layout.getBidiRunDirection(from) == ResolvedTextDirection.Rtl)
    }
    val width = segments.sumOf { (path, _) -> path.getBounds().width.toDouble() }.toFloat()
    var swept = (width + feather) * fraction
    for ((path, rtl) in segments) {
        val bounds = path.getBounds()
        val startX = if (rtl) bounds.right else bounds.left
        val direction = if (rtl) -1f else 1f
        if (bounds.width > 0f && swept > 0f) {
            drawPath(
                path,
                Brush.horizontalGradient(
                    colors = listOf(Color.White, Color.Transparent),
                    startX = startX + direction * (swept - feather),
                    endX = startX + direction * swept,
                ),
            )
        }
        swept -= bounds.width
    }
}
