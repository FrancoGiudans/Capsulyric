package com.example.islandlyrics.ui.overlay.capsule.render

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import com.example.islandlyrics.core.logging.LogManager
import com.example.islandlyrics.data.mediadata.TitleParser
import com.example.islandlyrics.lyrics.state.LyricRepository

internal data class LyricCapsuleIconFrame(
    val text: String,
    val fontSize: Float? = null
)

internal class LyricCapsuleDynamicIconCache(
    private val context: Context
) {
    private var cachedIconKey = ""
    private var cachedIconBitmap: Bitmap? = null

    fun release() {
        replace("", null)
    }

    fun inject(notification: Notification, iconStyle: String, iconFrame: LyricCapsuleIconFrame) {
        val bitmap = resolveBitmap(iconStyle, iconFrame)
        bitmap?.let { bmp ->
            if (bmp.isRecycled) return@let
            try {
                val icon = android.graphics.drawable.Icon.createWithBitmap(bmp)
                val field = Notification::class.java.getDeclaredField("mSmallIcon")
                field.isAccessible = true
                field.set(notification, icon)
            } catch (e: Exception) {
                LogManager.getInstance().e(context, TAG, "Failed to inject Dynamic Icon: $e")
            }
        }
    }

    private fun resolveBitmap(iconStyle: String, iconFrame: LyricCapsuleIconFrame): Bitmap? {
        return when (iconStyle) {
            "advanced" -> {
                val metadata = LyricRepository.getInstance().liveMetadata.value
                val albumArt = LyricRepository.getInstance().liveAlbumArt.value
                val realTitle = metadata?.title ?: ""
                val realArtist = metadata?.artist ?: ""
                val cacheKey = "advanced|$realTitle|$realArtist|${albumArt?.hashCode()}"
                if (cachedIconKey != cacheKey) {
                    val parsedTitle = TitleParser.parse(realTitle)
                    replace(cacheKey, AdvancedIconRenderer.render(albumArt, parsedTitle, realArtist, context))
                }
                cachedIconBitmap
            }
            "album_art" -> {
                val albumArt = LyricRepository.getInstance().liveAlbumArt.value
                val cacheKey = "album_art|${albumArt?.hashCode()}"
                if (cachedIconKey != cacheKey) {
                    replace(cacheKey, albumArt?.let { scaleAlbumArtForIcon(it) })
                }
                cachedIconBitmap
            }
            else -> {
                val cacheKey = "classic|${iconFrame.text}|${iconFrame.fontSize}"
                if (cachedIconKey != cacheKey) {
                    replace(cacheKey, textToBitmap(iconFrame.text, iconFrame.fontSize))
                }
                cachedIconBitmap
            }
        }
    }

    private fun replace(key: String, bitmap: Bitmap?) {
        val old = cachedIconBitmap
        cachedIconBitmap = bitmap
        cachedIconKey = key
        if (old != null && old !== bitmap && !old.isRecycled) {
            old.recycle()
        }
    }

    private fun scaleAlbumArtForIcon(albumArt: Bitmap): Bitmap? {
        return try {
            val density = context.resources.displayMetrics.density
            val size = (120f * density).toInt()
            Bitmap.createScaledBitmap(albumArt, size, size, true)
        } catch (e: Exception) {
            LogManager.getInstance().e(context, TAG, "Failed to scale album art for icon: $e")
            null
        }
    }

    private fun textToBitmap(text: String, forceFontSize: Float? = null): Bitmap? {
        return try {
            val density = context.resources.displayMetrics.density
            val fontSize = (forceFontSize ?: 20f) * density
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.textSize = fontSize
                color = android.graphics.Color.WHITE
                textAlign = android.graphics.Paint.Align.LEFT
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                isSubpixelText = true
                isLinearText = true
            }
            val baseline = -paint.ascent()
            val width = (paint.measureText(text) + 10 * density).toInt()
            val height = (baseline + paint.descent() + 5 * density).toInt()
            if (width <= 0 || height <= 0) return null

            val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(image)
            canvas.drawText(text, 5 * density, baseline, paint)
            image
        } catch (e: Exception) {
            LogManager.getInstance().e(context, TAG, "Failed to generate text bitmap: $e")
            null
        }
    }

    private companion object {
        const val TAG = "LyricCapsuleIcon"
    }
}
