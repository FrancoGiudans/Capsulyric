/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.example.islandlyrics.ui.overlay.floating

import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.example.islandlyrics.ui.overlay.model.LyricPresentation
import com.example.islandlyrics.ui.overlay.model.SecondaryTextMode
import com.example.islandlyrics.ui.overlay.model.SecondaryTextResolver
import com.example.islandlyrics.ui.overlay.model.UIState
import com.example.islandlyrics.ui.overlay.model.WordProgressCalculator
import com.example.islandlyrics.ui.overlay.views.OutlineTextView
import kotlin.math.max

internal class FloatingLyricsContentView(context: Context) : LinearLayout(context) {
    private val upperLyric = OutlineTextView(context)
    private val lowerLyric = OutlineTextView(context)
    private var activeLyric = upperLyric
    private var activeGravity = Gravity.CENTER
    private var alternating = false
    private var mainText = ""
    private var mainColor = Color.WHITE
    private var lastWordProgress: LyricPresentation.WordProgress? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        addView(upperLyric, matchWidth())
        addView(lowerLyric, matchWidth())
    }

    fun render(
        state: UIState,
        style: FloatingLyricsStyle,
        displayConfig: FloatingLyricsDisplayConfig,
        textColor: Int,
        fallbackText: String
    ) {
        val presentation = state.lyricPresentation
        val currentLine = presentation.currentLine

        val secondary = if (displayConfig.showSecondLine) {
            SecondaryTextResolver.resolveWithMode(
                modes = displayConfig.secondaryTextModes,
                translation = currentLine?.translation,
                romanization = currentLine?.romanization,
                nextLyric = presentation.nextLine?.text
            )
        } else {
            null
        }

        alternating = displayConfig.showSecondLine &&
            displayConfig.neighborAlignment == FloatingLyricsNeighborAlignment.SPLIT_START_END &&
            SecondaryTextMode.NEXT_LYRIC in displayConfig.secondaryTextModes
        val upcomingFirstLine = currentLine == null && secondary?.mode == SecondaryTextMode.NEXT_LYRIC
        val activeOnEnd = alternating && (upcomingFirstLine || presentation.currentLineIndex % 2 == 1)
        val maxLines = if (alternating) 1 else 2
        activeLyric = if (activeOnEnd) lowerLyric else upperLyric
        val inactiveLyric = if (activeOnEnd) upperLyric else lowerLyric

        activeGravity = when {
            activeOnEnd -> Gravity.END
            alternating -> Gravity.START
            else -> Gravity.CENTER
        }
        activeLyric.setStyle(textColor, style.textSizeSp, style.textWeight.weight, activeGravity, style.enableTextStroke, maxLines)
        activeLyric.minimumHeight = 0
        activeLyric.visibility = if (upcomingFirstLine && alternating) INVISIBLE else VISIBLE
        mainText = currentLine?.text ?: fallbackText.ifBlank { "♪" }
        mainColor = textColor
        lastWordProgress = presentation.wordProgress.takeIf { currentLine != null && displayConfig.wordHighlight }
        updateActiveFill(lastWordProgress)

        if (secondary != null || alternating) {
            val sameSize = alternating && (secondary == null || secondary.mode == SecondaryTextMode.NEXT_LYRIC)
            val inactiveSize = if (sameSize) style.textSizeSp else max(10f, style.textSizeSp * 0.72f)
            val inactiveGravity = when {
                activeOnEnd -> Gravity.START
                alternating -> Gravity.END
                else -> Gravity.CENTER
            }
            inactiveLyric.setStyle(
                withAlpha(textColor, 0.74f), inactiveSize, style.textWeight.weight,
                inactiveGravity, style.enableTextStroke, maxLines
            )
            inactiveLyric.minimumHeight = if (alternating) activeLyric.singleLineHeight else 0
            inactiveLyric.setLine(secondary?.text.orEmpty())
        }
        inactiveLyric.visibility = when {
            secondary != null -> VISIBLE
            alternating -> INVISIBLE
            else -> GONE
        }
    }

    fun renderWordProgress(progress: LyricPresentation.WordProgress?) {
        if (lastWordProgress == progress) return
        lastWordProgress = progress
        updateActiveFill(progress)
    }

    fun lyricAnchorIn(container: View): Point? {
        val anchorView = if (alternating) this else activeLyric
        if (!isShown || !anchorView.isLaidOut || anchorView.width == 0) return null
        val location = IntArray(2)
        anchorView.getLocationInWindow(location)
        val alignment = Gravity.getAbsoluteGravity(
            if (alternating) Gravity.CENTER else activeGravity,
            anchorView.layoutDirection
        )
        val alignmentOffset = when (alignment and Gravity.HORIZONTAL_GRAVITY_MASK) {
            Gravity.LEFT -> 0
            Gravity.RIGHT -> anchorView.width
            else -> anchorView.width / 2
        }
        val anchor = Point(location[0] + alignmentOffset, location[1])
        container.getLocationInWindow(location)
        anchor.offset(-location[0], -location[1])
        return anchor
    }

    private fun updateActiveFill(progress: LyricPresentation.WordProgress?) {
        activeLyric.setLine(mainText, progress?.let { highlightedText(mainText, it, mainColor) } ?: mainText)
    }

    private fun highlightedText(
        text: String,
        progress: LyricPresentation.WordProgress,
        textColor: Int
    ): CharSequence {
        val syllableProgresses = progress.syllables
        if (progress.isFullySung()) return text
        if (syllableProgresses.isEmpty()) {
            // 无逐字数据回退：按已唱字符前缀高亮
            val sungLength = progress.sungText.length.coerceIn(0, text.length)
            if (text.isEmpty()) return text
            return SpannableString(text).apply {
                if (sungLength < text.length) {
                    setSpan(
                        ForegroundColorSpan(withAlpha(textColor, WordProgressCalculator.UNSUNG_ALPHA)),
                        sungLength,
                        text.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }

        // 逐音节平滑过渡：已唱完全亮，当前音节按 progress 插值（smoothstep 缓动），未唱保持暗色
        return SpannableString(text).apply {
            var lastEnd = 0
            syllableProgresses.forEach { syllable ->
                val start = syllable.charStart.coerceIn(0, text.length)
                val end = syllable.charEnd.coerceIn(start, text.length)
                if (end <= start) return@forEach
                val alpha = WordProgressCalculator.highlightAlpha(syllable.progress)
                setSpan(
                    ForegroundColorSpan(withAlpha(textColor, alpha)),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                lastEnd = end
            }
            if (lastEnd < text.length) {
                setSpan(
                    ForegroundColorSpan(withAlpha(textColor, WordProgressCalculator.UNSUNG_ALPHA)),
                    lastEnd,
                    text.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    private fun matchWidth(): LayoutParams {
        return LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        return Color.argb(
            (255 * alpha).toInt().coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

}
