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
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.widget.LinearLayout
import com.example.islandlyrics.ui.overlay.model.LyricPresentation
import com.example.islandlyrics.ui.overlay.model.SecondaryTextResolver
import com.example.islandlyrics.ui.overlay.model.UIState
import com.example.islandlyrics.ui.overlay.views.OutlineTextView
import kotlin.math.max

internal class FloatingLyricsContentView(context: Context) : LinearLayout(context) {
    private val mainLyricTv = lyricTextView()
    private val secondaryTv = lyricTextView()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        addView(mainLyricTv, matchWidth())
        addView(secondaryTv, matchWidth())
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

        hideSecondaryViews()

        if (currentLine == null) {
            applyTextStyle(mainLyricTv, style, textColor, style.textSizeSp, Gravity.CENTER, alpha = 1f)
            mainLyricTv.text = fallbackText.ifBlank { "♪" }
            return
        }

        // 第二行歌词：采用模板2的多选优先级系统，仅显示第一个可用的选中内容
        val secondaryText = if (displayConfig.showSecondLine) {
            SecondaryTextResolver.resolve(
                modes = displayConfig.secondaryTextModes,
                translation = currentLine.translation,
                romanization = currentLine.romanization,
                nextLyric = presentation.nextLine?.text
            )
        } else {
            null
        }

        val splitAlignment =
            displayConfig.neighborAlignment == FloatingLyricsNeighborAlignment.SPLIT_START_END &&
                secondaryText != null
        val mainGravity = if (splitAlignment) Gravity.START else Gravity.CENTER
        val secondaryGravity = if (splitAlignment) Gravity.END else Gravity.CENTER

        applyTextStyle(mainLyricTv, style, textColor, style.textSizeSp, mainGravity, alpha = 1f)
        mainLyricTv.text = if (displayConfig.wordHighlight && presentation.wordProgress != null) {
            highlightedText(currentLine.text, presentation.wordProgress, textColor)
        } else {
            currentLine.text
        }

        if (secondaryText != null) {
            val sidecarSize = max(10f, style.textSizeSp * 0.72f)
            applyTextStyle(secondaryTv, style, textColor, sidecarSize, secondaryGravity, alpha = 0.74f)
            secondaryTv.text = secondaryText
        }
    }

    private fun applyTextStyle(
        target: OutlineTextView,
        style: FloatingLyricsStyle,
        textColor: Int,
        textSize: Float,
        gravity: Int,
        alpha: Float
    ) {
        target.gravity = gravity
        target.textAlignment = TEXT_ALIGNMENT_GRAVITY
        target.textSize = textSize
        target.setTextColor(withAlpha(textColor, alpha))
        target.setStroke(style.enableTextStroke)
        target.visibility = VISIBLE
    }

    private fun highlightedText(
        text: String,
        progress: LyricPresentation.WordProgress,
        textColor: Int
    ): CharSequence {
        val sungLength = progress.sungText.length.coerceIn(0, text.length)
        if (sungLength <= 0) return text

        return SpannableString(text).apply {
            setSpan(
                ForegroundColorSpan(textColor),
                0,
                sungLength,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (sungLength < text.length) {
                setSpan(
                    ForegroundColorSpan(withAlpha(textColor, 0.42f)),
                    sungLength,
                    text.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    private fun hideSecondaryViews() {
        mainLyricTv.visibility = GONE
        secondaryTv.visibility = GONE
    }

    private fun lyricTextView(): OutlineTextView {
        return OutlineTextView(context).apply {
            gravity = Gravity.CENTER
            textAlignment = TEXT_ALIGNMENT_GRAVITY
            includeFontPadding = false
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
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
