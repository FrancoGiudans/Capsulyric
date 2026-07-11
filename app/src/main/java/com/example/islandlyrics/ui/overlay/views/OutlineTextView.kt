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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * A TextView that supports an optional text outline (stroke) effect.
 * Useful for making floating text legible on variable backgrounds.
 */
class OutlineTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var enableStroke = false
    private var strokeColor = Color.parseColor("#80000000") // Semi-transparent black
    private var strokeWidthPx = 3f

    fun setStroke(enabled: Boolean, widthPx: Float = 3f, color: Int = strokeColor) {
        enableStroke = enabled
        strokeWidthPx = widthPx
        strokeColor = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (enableStroke) {
            val originalColor = textColors
            val paint = paint
            
            // Draw Outline
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeMiter = 10f
            this.setTextColor(strokeColor)
            paint.strokeWidth = strokeWidthPx
            super.onDraw(canvas)

            // Draw Inner Text
            paint.style = Paint.Style.FILL
            this.setTextColor(originalColor)
        }
        super.onDraw(canvas)
    }
}
