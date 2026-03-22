package com.example.islandlyrics.ui.common.views

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
