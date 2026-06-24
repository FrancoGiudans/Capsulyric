package com.example.islandlyrics.ui.overlay.floating

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.example.islandlyrics.ui.overlay.model.LyricPresentation
import com.example.islandlyrics.ui.overlay.model.UIState
import com.example.islandlyrics.ui.overlay.views.OutlineTextView
import kotlin.math.max

internal class FloatingLyricsContentView(context: Context) : LinearLayout(context) {
    private val topNeighborTv = lyricTextView()
    private val romanizationTv = lyricTextView()
    private val mainLyricTv = lyricTextView()
    private val translationTv = lyricTextView()
    private val bottomNeighborTv = lyricTextView()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        addView(topNeighborTv, matchWidth())
        addView(romanizationTv, matchWidth())
        addView(mainLyricTv, matchWidth())
        addView(translationTv, matchWidth())
        addView(bottomNeighborTv, matchWidth())
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
        applyTextStyle(mainLyricTv, style, textColor, style.textSizeSp, Gravity.CENTER, alpha = 1f)

        if (currentLine == null) {
            mainLyricTv.text = fallbackText.ifBlank { "♪" }
            return
        }

        val pair = resolveLinePair(presentation, displayConfig)
        val currentGravity = resolveCurrentGravity(pair, displayConfig)

        pair.topNeighbor?.let {
            renderNeighbor(topNeighborTv, it, style, textColor, pair.topGravity)
        }

        renderCurrentLine(
            currentLine = currentLine,
            presentation = presentation,
            style = style,
            displayConfig = displayConfig,
            textColor = textColor,
            gravity = currentGravity
        )

        pair.bottomNeighbor?.let {
            renderNeighbor(bottomNeighborTv, it, style, textColor, pair.bottomGravity)
        }
    }

    private fun renderCurrentLine(
        currentLine: LyricPresentation.DisplayLine,
        presentation: LyricPresentation,
        style: FloatingLyricsStyle,
        displayConfig: FloatingLyricsDisplayConfig,
        textColor: Int,
        gravity: Int
    ) {
        val sidecarSize = max(10f, style.textSizeSp * 0.72f)

        if (displayConfig.displayMode == FloatingLyricsDisplayMode.ROMANIZATION) {
            renderSidecar(romanizationTv, currentLine.romanization, style, textColor, sidecarSize, gravity)
        }

        applyTextStyle(mainLyricTv, style, textColor, style.textSizeSp, gravity, alpha = 1f)
        mainLyricTv.text = if (displayConfig.wordHighlight && presentation.wordProgress != null) {
            highlightedText(currentLine.text, presentation.wordProgress, textColor)
        } else {
            currentLine.text
        }

        if (displayConfig.displayMode == FloatingLyricsDisplayMode.TRANSLATION) {
            renderSidecar(translationTv, currentLine.translation, style, textColor, sidecarSize, gravity)
        }
    }

    private fun renderNeighbor(
        target: OutlineTextView,
        line: LyricPresentation.DisplayLine,
        style: FloatingLyricsStyle,
        textColor: Int,
        gravity: Int
    ) {
        applyTextStyle(target, style, textColor, max(10f, style.textSizeSp * 0.86f), gravity, alpha = 0.68f)
        target.text = line.text
        target.visibility = View.VISIBLE
    }

    private fun renderSidecar(
        target: OutlineTextView,
        text: String?,
        style: FloatingLyricsStyle,
        textColor: Int,
        textSize: Float,
        gravity: Int
    ) {
        if (text.isNullOrBlank()) return
        applyTextStyle(target, style, textColor, textSize, gravity, alpha = 0.74f)
        target.text = text
        target.visibility = View.VISIBLE
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
        target.textAlignment = View.TEXT_ALIGNMENT_GRAVITY
        target.textSize = textSize
        target.setTextColor(withAlpha(textColor, alpha))
        target.setStroke(style.enableTextStroke)
        target.visibility = View.VISIBLE
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
        topNeighborTv.visibility = View.GONE
        romanizationTv.visibility = View.GONE
        translationTv.visibility = View.GONE
        bottomNeighborTv.visibility = View.GONE
    }

    private fun resolveLinePair(
        presentation: LyricPresentation,
        displayConfig: FloatingLyricsDisplayConfig
    ): LinePair {
        val center = Gravity.CENTER
        if (displayConfig.displayMode != FloatingLyricsDisplayMode.NEIGHBOR_LINE || !presentation.canShowNeighborLine) {
            return LinePair(topGravity = center, bottomGravity = center)
        }

        val splitAlignment = displayConfig.neighborAlignment == FloatingLyricsNeighborAlignment.SPLIT_START_END
        val topGravity = if (splitAlignment) Gravity.START else center
        val bottomGravity = if (splitAlignment) Gravity.END else center
        val next = presentation.nextLine
        return if (next != null) {
            LinePair(
                bottomNeighbor = next,
                currentOnTop = true,
                topGravity = topGravity,
                bottomGravity = bottomGravity
            )
        } else {
            LinePair(
                topNeighbor = presentation.previousLine,
                currentOnTop = false,
                topGravity = topGravity,
                bottomGravity = bottomGravity
            )
        }
    }

    private fun resolveCurrentGravity(pair: LinePair, displayConfig: FloatingLyricsDisplayConfig): Int {
        if (displayConfig.displayMode != FloatingLyricsDisplayMode.NEIGHBOR_LINE ||
            displayConfig.neighborAlignment != FloatingLyricsNeighborAlignment.SPLIT_START_END
        ) {
            return Gravity.CENTER
        }
        return if (pair.currentOnTop) pair.topGravity else pair.bottomGravity
    }

    private fun lyricTextView(): OutlineTextView {
        return OutlineTextView(context).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_GRAVITY
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

    private data class LinePair(
        val topNeighbor: LyricPresentation.DisplayLine? = null,
        val bottomNeighbor: LyricPresentation.DisplayLine? = null,
        val currentOnTop: Boolean = true,
        val topGravity: Int,
        val bottomGravity: Int
    )
}
