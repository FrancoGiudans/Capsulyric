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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import com.example.islandlyrics.ui.overlay.model.WordProgressCalculator
import top.yukonga.miuix.kmp.basic.Text

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
                    shader.setFloatUniform("maxSigma", 3.dp.toPx().coerceAtMost(16f))
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

internal fun Modifier.fadeLyricsAroundBars(top: Dp, bottom: State<Dp>, feather: Dp, bottomFeather: Dp = feather): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            if (size.height <= 0f) return@drawWithCache onDrawWithContent { drawContent() }
            val upperBar = (top.toPx() / size.height).coerceIn(0f, 1f)
            val lowerBar = (1f - bottom.value.toPx() / size.height).coerceIn(upperBar, 1f)
            val featherLimit = (lowerBar - upperBar) / 2f
            val upperEdge = (feather.toPx() / size.height).coerceIn(0f, featherLimit)
            val lowerEdge = (bottomFeather.toPx() / size.height).coerceIn(0f, featherLimit)
            val shallow = Color.Black.copy(alpha = 0.16f)
            val middle = Color.Black.copy(alpha = 0.5f)
            val deep = Color.Black.copy(alpha = 0.84f)
            val mask = Brush.verticalGradient(
                upperBar to Color.Transparent,
                upperBar + upperEdge * 0.25f to shallow,
                upperBar + upperEdge * 0.5f to middle,
                upperBar + upperEdge * 0.75f to deep,
                upperBar + upperEdge to Color.Black,
                lowerBar - lowerEdge to Color.Black,
                lowerBar - lowerEdge * 0.75f to deep,
                lowerBar - lowerEdge * 0.5f to middle,
                lowerBar - lowerEdge * 0.25f to shallow,
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
    style: TextStyle,
    modifier: Modifier = Modifier,
    sweepStrength: () -> Float = { 1f },
) {
    val timed = !line.syllables.isNullOrEmpty()
    val needsSweep = rememberUpdatedState(current)
    val textLayout = remember(line.text) { mutableStateOf<TextLayoutResult?>(null) }
    val onTextLayout = remember(textLayout) { { layout: TextLayoutResult -> textLayout.value = layout } }
    Text(
        text = line.text,
        modifier = modifier.then(if (timed) {
            Modifier.graphicsLayer {
                compositingStrategy = if (needsSweep.value) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
                .drawWithCache {
                    val layout = textLayout.value
                    val maskPaint = Paint().apply { blendMode = BlendMode.DstIn }
                    val runs = layout?.sweepRuns().orEmpty()
                    val width = runs.lastOrNull()?.let { it.distance + it.bounds.width } ?: 0f
                    val glyphCount = Character.codePointCount(line.text, 0, line.text.length).coerceAtLeast(1)
                    val feather = width / glyphCount * 1.25f
                    onDrawWithContent {
                        drawContent()
                        if (!needsSweep.value) return@onDrawWithContent
                        val strength = sweepStrength()
                        if (strength == 0f) return@onDrawWithContent
                        val playbackMs = position.value ?: return@onDrawWithContent
                        val progress = WordProgressCalculator.compute(
                            line.text, line.startTime, line.endTime, line.syllables, playbackMs,
                        ) ?: return@onDrawWithContent
                        if (layout == null || width <= 0f || playbackMs >= line.endTime ||
                            progress.syllables.all { it.progress >= 1f }
                        ) {
                            return@onDrawWithContent
                        }

                        // Keep one front across shaped runs, including across syllable boundaries.
                        val syllable = progress.syllables.lastOrNull { it.progress > 0f }
                        val front = if (syllable == null) {
                            0f
                        } else {
                            val start = runs.sweepDistance(layout, syllable.charStart, width)
                            val end = runs.sweepDistance(layout, syllable.charEnd, width)
                            lerp(start, end, syllable.progress)
                        }
                        val swept = front / width * (width + feather)
                        drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), maskPaint) }
                        val unsungAlpha = 1f - (1f - WordProgressCalculator.UNSUNG_ALPHA) * strength
                        drawRect(Color.White.copy(alpha = unsungAlpha))
                        for (run in runs) drawSweepRun(run, swept - run.distance, feather)
                        drawIntoCanvas { it.restore() }
                    }
                }
        } else {
            Modifier
        }),
        style = style,
        color = color,
        onTextLayout = onTextLayout.takeIf { timed },
    )
}

private data class PlaybackSweepRun(
    val start: Int,
    val end: Int,
    val path: Path,
    val bounds: Rect,
    val rtl: Boolean,
    val distance: Float,
)

private fun TextLayoutResult.sweepRuns(): List<PlaybackSweepRun> = buildList {
    var distance = 0f
    for (line in 0 until lineCount) {
        var start = getLineStart(line)
        val lineEnd = getLineEnd(line, visibleEnd = true)
        while (start < lineEnd) {
            val direction = getBidiRunDirection(start)
            var end = start + 1
            while (end < lineEnd && getBidiRunDirection(end) == direction) end++
            val path = getPathForRange(start, end)
            val bounds = path.getBounds()
            add(PlaybackSweepRun(start, end, path, bounds, direction == ResolvedTextDirection.Rtl, distance))
            distance += bounds.width
            start = end
        }
    }
}

private fun List<PlaybackSweepRun>.sweepDistance(layout: TextLayoutResult, offset: Int, width: Float): Float {
    val run = firstOrNull { offset < it.end } ?: return width
    if (offset <= run.start) return run.distance
    val glyph = layout.getBoundingBox(offset)
    val advance = if (run.rtl) run.bounds.right - glyph.right else glyph.left - run.bounds.left
    return run.distance + advance.coerceIn(0f, run.bounds.width)
}

private fun DrawScope.drawSweepRun(run: PlaybackSweepRun, swept: Float, feather: Float) {
    if (swept <= 0f || run.bounds.width <= 0f) return
    if (swept >= run.bounds.width + feather) {
        drawPath(run.path, Color.White)
        return
    }
    val startX = if (run.rtl) run.bounds.right else run.bounds.left
    val direction = if (run.rtl) -1f else 1f
    drawPath(
        run.path,
        Brush.horizontalGradient(
            colors = listOf(Color.White, Color.Transparent),
            startX = startX + direction * (swept - feather),
            endX = startX + direction * swept,
        ),
    )
}
