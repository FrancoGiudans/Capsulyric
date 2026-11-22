/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.main.miuix

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.Typeface
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import androidx.annotation.RequiresApi
import kotlin.math.roundToInt

internal data class LyricShareCard(
    val title: String,
    val artist: String,
    val cover: Bitmap?,
    val appearance: PlaybackCoverAppearance?,
    val lines: List<LyricShareLine>,
    val appName: String,
    val surfaceColor: Int,
)

internal object LyricShareCardRenderer {
    private const val Width = 1080
    private const val MaxHeight = 4800
    private const val Inset = 84
    private const val CoverSize = 140
    private const val ContentTop = Inset + CoverSize + 76
    private const val FooterHeight = 152
    private const val ContentWidth = Width - 2 * Inset

    // Call from a worker thread. The caller owns the returned bitmap.
    fun render(card: LyricShareCard): Bitmap {
        require(card.lines.size in 1..LyricShareLineLimit && card.lines.all { it.original.isNotBlank() } &&
            card.lines.sumOf(LyricShareLine::characterCount) <= LyricShareCharacterLimit)
        var scale = 1f
        var lyrics = layoutLyrics(card.lines, scale)
        while (lyrics.height > MaxHeight - ContentTop - FooterHeight && scale > 0.2f) {
            scale *= 0.8f
            lyrics = layoutLyrics(card.lines, scale)
        }
        val height = ContentTop + lyrics.height + FooterHeight
        check(height <= MaxHeight)
        val bitmap = Bitmap.createBitmap(Width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            drawBackground(canvas, card, height)
            drawHeader(canvas, card)
            var top = ContentTop.toFloat()
            lyrics.lines.forEach { (original, translation) ->
                canvas.drawTextLayout(original, Inset.toFloat(), top)
                top += original.height
                if (translation != null) {
                    top += lyrics.translationGap
                    canvas.drawTextLayout(translation, Inset.toFloat(), top)
                    top += translation.height
                }
                top += lyrics.lineGap
            }
            val divider = height - FooterHeight + 16f
            canvas.drawLine(Inset.toFloat(), divider, (Width - Inset).toFloat(), divider,
                Paint().apply { color = 0x42FFFFFF; strokeWidth = 2f })
            canvas.drawTextLayout(
                layout(card.appName, ContentWidth, 26f, 0xAAFFFFFF.toInt(), maxLines = 1),
                Inset.toFloat(), divider + 26f,
            )
            return bitmap
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
    }

    private data class LyricalLayout(
        val lines: List<Pair<StaticLayout, StaticLayout?>>,
        val translationGap: Int,
        val lineGap: Int,
        val height: Int,
    )

    private fun layoutLyrics(lines: List<LyricShareLine>, scale: Float): LyricalLayout {
        val translationGap = (8 * scale).roundToInt()
        val lineGap = (34 * scale).roundToInt().coerceAtLeast(3)
        val layouts = lines.map { line ->
            layout(line.original, ContentWidth, (54 * scale).coerceAtLeast(12f), Color.WHITE, bold = true) to
                line.translation?.let {
                    layout(it, ContentWidth, (35 * scale).coerceAtLeast(10f), 0xCCFFFFFF.toInt())
                }
        }
        val height = layouts.sumOf { (original, translation) ->
            original.height + if (translation == null) 0 else translationGap + translation.height
        } + lineGap * (layouts.size - 1)
        return LyricalLayout(layouts, translationGap, lineGap, height)
    }

    private fun layout(
        text: String,
        width: Int,
        size: Float,
        color: Int,
        bold: Boolean = false,
        maxLines: Int = Int.MAX_VALUE,
    ): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setMaxLines(maxLines)
            .apply { if (maxLines != Int.MAX_VALUE) setEllipsize(TextUtils.TruncateAt.END) }
            .build()
    }

    private fun drawHeader(canvas: Canvas, card: LyricShareCard) {
        val bounds = RectF(Inset.toFloat(), Inset.toFloat(), (Inset + CoverSize).toFloat(), (Inset + CoverSize).toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = 0x35FFFFFF
            setShadowLayer(11f, 0f, 6f, 0x55000000)
        }
        canvas.drawRoundRect(bounds, 23f, 23f, paint)
        val cover = card.cover?.takeUnless(Bitmap::isRecycled)
        val software = if (cover?.config == Bitmap.Config.HARDWARE) cover.copy(Bitmap.Config.ARGB_8888, false) else cover
        if (software != null) {
            try {
                paint.clearShadowLayer()
                paint.alpha = 255
                val side = minOf(software.width, software.height)
                val left = (software.width - side) / 2
                val top = (software.height - side) / 2
                val clip = Path().apply { addRoundRect(bounds, 23f, 23f, Path.Direction.CW) }
                val save = canvas.save()
                canvas.clipPath(clip)
                canvas.drawBitmap(software, Rect(left, top, left + side, top + side), bounds, paint)
                canvas.restoreToCount(save)
            } finally {
                if (software !== cover) software.recycle()
            }
        }
        val textLeft = Inset + CoverSize + 34f
        val textWidth = Width - Inset - textLeft.toInt()
        val title = layout(card.title, textWidth, 43f, Color.WHITE, bold = true, maxLines = 2)
        val artist = card.artist.takeIf(String::isNotBlank)?.let {
            layout(it, textWidth, 32f, 0xBBFFFFFF.toInt(), maxLines = 1)
        }
        val gap = if (artist == null) 0 else 4
        val top = Inset + (CoverSize - title.height - gap - (artist?.height ?: 0)) / 2f
        canvas.drawTextLayout(title, textLeft, top)
        artist?.let { canvas.drawTextLayout(it, textLeft, top + title.height + gap) }
    }

    private fun Canvas.drawTextLayout(layout: StaticLayout, x: Float, y: Float) {
        val saved = save()
        translate(x, y)
        layout.draw(this)
        restoreToCount(saved)
    }

    private fun drawBackground(canvas: Canvas, card: LyricShareCard, height: Int) {
        val appearance = card.appearance
        val flow = if (appearance?.flowTexture != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                // The flowing backdrop is already soft; draw the text and cover at full resolution.
                renderFlowFrame(appearance, Width / 2, (height + 1) / 2)
            } catch (error: RuntimeException) {
                Log.w("LyricShareCardRenderer", "Unable to render the flowing background", error)
                null
            } catch (error: OutOfMemoryError) {
                Log.w("LyricShareCardRenderer", "Unable to allocate the flowing background", error)
                null
            }
        } else {
            null
        }
        val bounds = RectF(0f, 0f, Width.toFloat(), height.toFloat())
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        if (flow != null) {
            try {
                canvas.drawBitmap(flow, null, bounds, paint)
            } finally {
                flow.recycle()
            }
            return
        }
        val (topLeft, topRight, bottomLeft, bottomRight) = playbackBackgroundPalette(appearance?.colors, card.surfaceColor)
        paint.shader = LinearGradient(0f, 0f, bounds.right, 0f, bottomLeft, bottomRight, Shader.TileMode.CLAMP)
        canvas.drawRect(bounds, paint)
        val layer = canvas.saveLayer(bounds, null)
        paint.shader = LinearGradient(0f, 0f, bounds.right, 0f, topLeft, topRight, Shader.TileMode.CLAMP)
        canvas.drawRect(bounds, paint)
        paint.shader = LinearGradient(0f, 0f, 0f, bounds.bottom, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        paint.blendMode = BlendMode.DST_IN
        canvas.drawRect(bounds, paint)
        canvas.restoreToCount(layer)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun renderFlowFrame(appearance: PlaybackCoverAppearance, width: Int, height: Int): Bitmap? {
        val shader = createPlaybackFlowShader(appearance) ?: return null
        shader.setPlaybackFlowFrame(width.toFloat(), height.toFloat(), seconds = 8.0)
        val node = RenderNode("LyricShareBackground")
        val usage = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1, usage).use { reader ->
            val renderer = HardwareRenderer()
            try {
                node.setPosition(0, 0, width, height)
                val recording = node.beginRecording()
                try {
                    recording.drawPaint(Paint().apply { this.shader = shader })
                } finally {
                    node.endRecording()
                }
                renderer.setSurface(reader.surface)
                renderer.setContentRoot(node)
                renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
                val image = reader.acquireNextImage() ?: return null
                image.use {
                    val buffer = image.hardwareBuffer ?: return null
                    buffer.use {
                        val gpuBitmap = Bitmap.wrapHardwareBuffer(buffer, null) ?: return null
                        try {
                            return gpuBitmap.copy(Bitmap.Config.ARGB_8888, false)
                        } finally {
                            gpuBitmap.recycle()
                        }
                    }
                }
            } finally {
                renderer.destroy()
                node.discardDisplayList()
            }
        }
    }
}
