/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.main.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.SyllableInfo
import com.example.islandlyrics.ui.overlay.model.WordProgressCalculator
import top.yukonga.miuix.kmp.basic.Text

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
                    val syllables = if (layout == null) emptyList() else {
                        var offset = 0
                        line.syllables.orEmpty().map { syllable ->
                            val start = runs.sweepDistance(layout, offset, width)
                            offset += syllable.text.length
                            val end = runs.sweepDistance(layout, offset, width)
                            PlaybackSweepSyllable(syllable, start, end)
                        }
                    }
                    val endTime = syllables.maxOfOrNull { it.timing.endTime } ?: 0L
                    val glyphCount = Character.codePointCount(line.text, 0, line.text.length).coerceAtLeast(1)
                    val feather = width / glyphCount * 1.25f
                    onDrawWithContent {
                        drawContent()
                        if (!needsSweep.value) return@onDrawWithContent
                        val strength = sweepStrength()
                        if (strength == 0f) return@onDrawWithContent
                        val playbackMs = position.value ?: return@onDrawWithContent
                        if (width <= 0f || playbackMs >= line.endTime || playbackMs >= endTime) {
                            return@onDrawWithContent
                        }

                        // Keep one front across shaped runs, including across syllable boundaries.
                        var front = 0f
                        for (index in syllables.lastIndex downTo 0) {
                            val syllable = syllables[index]
                            val progress = WordProgressCalculator.syllableFraction(syllable.timing, playbackMs)
                            if (progress > 0f) {
                                front = lerp(syllable.start, syllable.end, progress)
                                break
                            }
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

private data class PlaybackSweepSyllable(
    val timing: SyllableInfo,
    val start: Float,
    val end: Float,
)

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
