/*
 *
 *  * Copyright (c) 2026 Franco Giudance
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

package com.example.islandlyrics.ui.overlay.superisland.cache

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF

internal class SuperIslandBitmapScaler {
    private val scaledBitmapCache = LinkedHashMap<String, Bitmap>(8, 0.75f, true)

    fun scaleRound(src: Bitmap, targetSize: Int): Bitmap {
        if (src.isRecycled) {
            return Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        }
        val cacheKey = "${System.identityHashCode(src)}:${src.width}x${src.height}:$targetSize"
        scaledBitmapCache[cacheKey]?.let { return it }

        val needsScale = src.width != targetSize || src.height != targetSize
        val scaled = if (!needsScale) {
            src
        } else {
            Bitmap.createScaledBitmap(src, targetSize, targetSize, true)
        }

        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, targetSize.toFloat(), targetSize.toFloat())
        val cornerRadius = targetSize * 0.2f

        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, 0f, 0f, paint)

        if (needsScale) {
            scaled.recycle()
        }

        putBitmapCacheEntry(cacheKey, output, maxEntries = 8)
        return output
    }

    fun clear() {
        scaledBitmapCache.values.forEach { recycleBitmap(it) }
        scaledBitmapCache.clear()
    }

    private fun putBitmapCacheEntry(
        key: String,
        bitmap: Bitmap,
        maxEntries: Int
    ) {
        scaledBitmapCache.put(key, bitmap)?.let { old ->
            if (old !== bitmap) recycleBitmap(old)
        }
        while (scaledBitmapCache.size > maxEntries) {
            scaledBitmapCache.remove(scaledBitmapCache.entries.first().key)?.let { recycleBitmap(it) }
        }
    }

    private fun recycleBitmap(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}
