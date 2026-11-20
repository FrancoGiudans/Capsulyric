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

package com.example.islandlyrics.ui.overlay.views

import android.content.Context
import android.content.res.Configuration
import android.graphics.Paint
import android.text.TextUtils
import android.view.Gravity
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatTextView

/**
 * A TextView that supports an optional text outline (stroke) effect.
 * Useful for making floating text legible on variable backgrounds.
 */
internal class OutlineTextView(context: Context) : FrameLayout(context) {
    private val outline = label().apply {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        visibility = GONE
    }
    private val fill = label()
    private var outlineText: String = ""
    val singleLineHeight: Int
        get() = fill.lineHeight + fill.paddingTop + fill.paddingBottom

    init {
        addView(outline, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL))
        addView(fill, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL))
        updateOutlineColor(resources.configuration)
    }

    fun setStyle(color: Int, sizeSp: Float, alignment: Int, stroke: Boolean, maxLines: Int) {
        for (label in listOf(outline, fill)) {
            label.gravity = alignment
            label.textSize = sizeSp
            label.maxLines = maxLines
        }
        fill.setTextColor(color)
        outline.visibility = if (stroke) VISIBLE else GONE
        val density = resources.displayMetrics.density
        outline.paint.strokeWidth = minOf(density * 0.65f, fill.textSize * 0.045f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateOutlineColor(resources.configuration)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateOutlineColor(newConfig)
    }

    private fun updateOutlineColor(configuration: Configuration) {
        val dark = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        outline.setTextColor(if (dark) 0x40FFFFFF else 0x40000000)
    }

    fun setLine(text: String, fillText: CharSequence = text) {
        if (text != outlineText) {
            outlineText = text
            outline.text = text
        }
        if (fill.text != fillText) fill.text = fillText
    }

    private fun label() = AppCompatTextView(context).apply {
        textAlignment = TEXT_ALIGNMENT_GRAVITY
        includeFontPadding = false
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setPadding(1, 1, 1, 1)
    }
}
