package com.example.islandlyrics.ui.overlay.superisland.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas

internal class SuperIslandAppIconLoader(
    private val context: Context
) {
    fun load(packageName: String?): Bitmap? {
        if (packageName == null) return null
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
