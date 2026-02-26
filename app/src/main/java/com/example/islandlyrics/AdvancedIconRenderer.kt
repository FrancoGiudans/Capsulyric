package com.example.islandlyrics

import android.content.Context
import android.graphics.*

/**
 * Advanced icon bitmap renderer with album art + dual-line text layout.
 * 
 * Canvas Layout:
 * - Total: 350×120dp (density-scaled)
 * - Left: 0-120px album art square
 * - Right: 130-350px text area (10px gap)
 * 
 * Text Layout:
 * - Line 1 (Top 70%): Primary title, bold, white, adaptive sizing with textScaleX compression
 * - Line 2 (Bottom 30%): Secondary info + artist, regular, 60% opacity, gradient fade only
 */
object AdvancedIconRenderer {
    
    private const val CANVAS_WIDTH_DP = 200f     // Reduced by 3x (from 600)
    private const val CANVAS_HEIGHT_DP = 40f     // Reduced by 3x (from 120)
    private const val ALBUM_ART_SIZE_DP = 40f    // Reduced by 3x (from 120)
    private const val ALBUM_ART_WIDTH_DP = 36f   // Reduced by 3x (from 108)
    private const val TEXT_START_DP = 38f        // Reduced by 3x (from 113)
    private const val TEXT_END_DP = 183f         // Reduced by 3x (from 550)
    
    private const val LINE1_BASE_SIZE_DP = 25f   // Reduced by 3x (from 75)
    private const val LINE2_BASE_SIZE_DP = 15f   // Reduced by ~3x (from 45.6)
    
    private const val MIN_TEXT_SCALE_X = 0.8f
    private const val MIN_TEXT_SIZE_FACTOR = 0.65f // Allow shrinking down to 65% of base size
    private const val CORNER_RADIUS_DP = 8f      // Reduced by 3x (from 24)
    
    /**
     * Render the advanced icon bitmap.
     * ...
     */
    fun render(
        albumArt: Bitmap?,
        parsedTitle: ParsedTitle,
        artist: String,
        context: Context
    ): Bitmap? {
        try {
            val density = context.resources.displayMetrics.density
            
            // Convert DP to pixels
            val canvasWidth = (CANVAS_WIDTH_DP * density).toInt()
            val canvasHeight = (CANVAS_HEIGHT_DP * density).toInt()
            val albumArtHeight = (ALBUM_ART_SIZE_DP * density).toInt()
            val albumArtWidth = (ALBUM_ART_WIDTH_DP * density).toInt()
            val textStart = TEXT_START_DP * density
            val textEnd = TEXT_END_DP * density
            val textWidth = textEnd - textStart
            val cornerRadius = CORNER_RADIUS_DP * density
            
            // Create canvas
            val bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // Background (transparent)
            canvas.drawColor(Color.TRANSPARENT)
            
            // --- ALBUM ART SECTION (0-108px) ---
            val artRect = RectF(0f, 0f, albumArtWidth.toFloat(), albumArtHeight.toFloat())
            val artPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            
            if (albumArt != null) {
                // Resize bitmap to fit height (standard 120dp) to maintain aspect ratio
                // We will center-crop this 120x120 image into the 108x120 rect
                val scaledArt = Bitmap.createScaledBitmap(albumArt, albumArtHeight, albumArtHeight, true)
                val shader = BitmapShader(scaledArt, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                
                // Center the image horizontally in the narrower rect
                val matrix = Matrix()
                matrix.setTranslate(-(albumArtHeight - albumArtWidth) / 2f, 0f)
                shader.setLocalMatrix(matrix)
                
                artPaint.shader = shader
                
                canvas.drawRoundRect(artRect, cornerRadius, cornerRadius, artPaint)
                
                // Cleanup intermediate bitmap if created
                if (scaledArt != albumArt) {
                    scaledArt.recycle()
                }
            } else {
                // Placeholder: dark gray rounded square
                artPaint.color = Color.parseColor("#424242")
                artPaint.style = Paint.Style.FILL
                canvas.drawRoundRect(artRect, cornerRadius, cornerRadius, artPaint)
            }
            
            // --- TEXT SECTION (130-350px) ---
            
            // Line 1: Primary title (bold, white)
            val line1Text = parsedTitle.primaryLine
            val line1Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.LEFT
                textSize = LINE1_BASE_SIZE_DP * density
            }

            // Alignment: Top of Line 1 aligns with Top of Canvas (0)
            // Use getTextBounds to align the VISIBLE top pixels to 0, ignoring font padding.
            val line1Bounds = Rect()
            line1Paint.getTextBounds(line1Text, 0, line1Text.length, line1Bounds)
            val line1Y = -line1Bounds.top.toFloat()
            
            drawAdaptiveText(canvas, line1Text, textStart, line1Y, textWidth, line1Paint, true)
            
            // Line 2: Secondary info + artist
            val line2Text = if (parsedTitle.secondaryInfo.isNotEmpty()) {
                "${parsedTitle.secondaryInfo} - $artist"
            } else {
                artist
            }
            
            val line2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#99FFFFFF")  // 60% opacity white
                typeface = Typeface.DEFAULT
                textAlign = Paint.Align.LEFT
                textSize = LINE2_BASE_SIZE_DP * density
            }
            
            // Alignment: Bottom of Line 2 aligns with Bottom of Canvas
            // Use getTextBounds to align the VISIBLE bottom pixels to canvasHeight.
            val line2Bounds = Rect()
            line2Paint.getTextBounds(line2Text, 0, line2Text.length, line2Bounds)
            val line2Y = canvasHeight.toFloat() - line2Bounds.bottom.toFloat()
            
            drawAdaptiveText(canvas, line2Text, textStart, line2Y, textWidth, line2Paint, false)
            
            return bitmap
            
        } catch (e: Exception) {
            LogManager.getInstance().e(context, "AdvancedIconRenderer", "Failed to render: $e")
            return null
        }
    }
    
    /**
     * Draw text with adaptive sizing to fit width.
     * 
     * Strategy:
     * 1. Check width.
     * 2. If too wide, compress horizontally (textScaleX down to 0.7).
     * 3. If still too wide, reduce textSize (down to 40%).
     * 4. If still too wide, apply gradient fade.
     * 
     * @param isTopLine If true, we maintain top-alignment visually when shrinking.
     */
    private fun drawAdaptiveText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        basePaint: Paint,
        isTopLine: Boolean
    ) {
        val paint = Paint(basePaint)
        var measuredWidth = paint.measureText(text)
        
        // Pass 1: Light Scale Compression (to 0.9)
        if (measuredWidth > maxWidth) {
            val neededScale = maxWidth / measuredWidth
            paint.textScaleX = neededScale.coerceAtLeast(0.9f)
            measuredWidth = paint.measureText(text)
        }
        
        // Pass 2: Font Size Reduction (Aggressive)
        if (measuredWidth > maxWidth) {
             // Calculate how much we need to shrink
             val ratio = maxWidth / measuredWidth
             val newSizeScale = ratio.coerceAtLeast(0.4f)
             paint.textSize = basePaint.textSize * newSizeScale
             
             // Reset textScaleX for now to recalculate cleanly
             paint.textScaleX = 1.0f
             measuredWidth = paint.measureText(text)
        }

        // Pass 3: Final Horizontal Compression
        if (measuredWidth > maxWidth) {
             val neededScale = maxWidth / measuredWidth
             paint.textScaleX = neededScale.coerceAtLeast(0.7f)
             measuredWidth = paint.measureText(text)
        }
        
        var drawY = y
        // Recalculate Y if font parameters changed
        if (paint.textSize != basePaint.textSize) {
            val newBounds = Rect()
            paint.getTextBounds(text, 0, text.length, newBounds)
            
            if (isTopLine) {
                 // Fix Top: Align visual top to 0
                 drawY = -newBounds.top.toFloat()
            } else {
                 // Fix Bottom: Maintain visual bottom aligned to canvasBottom
                 // We deduce the target visual bottom from the original parameters
                 val baseBounds = Rect()
                 basePaint.getTextBounds(text, 0, text.length, baseBounds)
                 val targetBottom = y + baseBounds.bottom
                 
                 drawY = targetBottom - newBounds.bottom
            }
        }

        // Pass 4: Gradient Fade (Last Resort)
        if (measuredWidth > maxWidth) {
            drawGradientFadeText(canvas, text, x, drawY, maxWidth, paint)
        } else {
            canvas.drawText(text, x, drawY, paint)
        }
    }
    
    /**
     * Draw text with gradient fade on the right edge.
     * This is used for Line 2 and as fallback for Line 1.
     */
    private fun drawGradientFadeText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        paint: Paint
    ) {
        // Draw text normally first
        canvas.drawText(text, x, y, paint)
        
        // If text exceeds max width, apply fade gradient
        val measuredWidth = paint.measureText(text)
        if (measuredWidth > maxWidth) {

            // Actually we have density in render() but not passed here. 
            // Let's assume standard 50dp approximate or pass density. 
            // To be safe and clean, let's use a percentage that approximates 50dp on typical width or just a hardcoded ratio that is much smaller (e.g. 0.9).
            // Better: use the 'x + maxWidth' as the end, and back off by a fixed amount if possible, or just use 90%.
            // Given the user wants "rightmost", 10-15% fade is safer than 30%.
            
            val fadeLength = maxWidth * 0.15f // Fade last 15%
            val fadeStartX = x + maxWidth - fadeLength
            val fadeEndX = x + maxWidth
            
            // Create a rect that clips the fade area
            val saveCount = canvas.save()
            
            // Clip accurately using ascent/descent from the current paint
            canvas.clipRect(fadeStartX, y + paint.ascent(), fadeEndX, y + paint.descent())
            
            // Draw fade overlay (inverted gradient as mask)
            val fadePaint = Paint().apply {
                this.shader = LinearGradient(
                    fadeStartX, 0f,
                    fadeEndX, 0f,
                    intArrayOf(Color.TRANSPARENT, Color.WHITE),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            }
            
            canvas.drawRect(fadeStartX, y + paint.ascent(), fadeEndX + 50, y + paint.descent(), fadePaint)
            canvas.restoreToCount(saveCount)
        }
    }
}
