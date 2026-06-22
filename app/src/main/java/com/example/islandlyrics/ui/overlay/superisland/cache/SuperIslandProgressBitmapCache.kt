package com.example.islandlyrics.ui.overlay.superisland.cache
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

internal class SuperIslandProgressBitmapCache(
    private val context: Context
) {
    private val cache = LinkedHashMap<String, Bitmap>(12, 0.75f, true)

    fun createWide(progressPercent: Int, progressColor: String, darkMode: Boolean): Bitmap {
        return create(
            cachePrefix = "wide",
            progressPercent = progressPercent,
            progressColor = progressColor,
            darkMode = darkMode,
            width = dpToPx(240),
            height = dpToPx(6)
        )
    }

    fun createTiny(progressPercent: Int, progressColor: String, darkMode: Boolean): Bitmap {
        return create(
            cachePrefix = "tiny",
            progressPercent = progressPercent,
            progressColor = progressColor,
            darkMode = darkMode,
            width = dpToPx(44),
            height = dpToPx(4)
        )
    }

    fun clear() {
        cache.values.forEach { recycleBitmap(it) }
        cache.clear()
    }

    private fun create(
        cachePrefix: String,
        progressPercent: Int,
        progressColor: String,
        darkMode: Boolean,
        width: Int,
        height: Int
    ): Bitmap {
        val cacheKey = "$cachePrefix:$progressPercent:$progressColor:$darkMode"
        cache[cacheKey]?.let { return it }

        val radius = height / 2f
        val progress = progressPercent.coerceIn(0, 100)
        val progressWidth = if (progress <= 0) 0f else (width * (progress / 100f)).coerceAtLeast(radius * 2)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())

        paint.color = Color.parseColor(if (darkMode) "#33FFFFFF" else "#26000000")
        canvas.drawRoundRect(rect, radius, radius, paint)

        if (progressWidth > 0f) {
            paint.color = Color.parseColor(progressColor)
            canvas.drawRoundRect(RectF(0f, 0f, progressWidth, height.toFloat()), radius, radius, paint)
        }

        put(cacheKey, bitmap)
        return bitmap
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    private fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)?.let { old ->
            if (old !== bitmap) recycleBitmap(old)
        }
        while (cache.size > MAX_ENTRIES) {
            cache.remove(cache.entries.first().key)?.let { recycleBitmap(it) }
        }
    }

    private fun recycleBitmap(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    private companion object {
        const val MAX_ENTRIES = 12
    }
}
