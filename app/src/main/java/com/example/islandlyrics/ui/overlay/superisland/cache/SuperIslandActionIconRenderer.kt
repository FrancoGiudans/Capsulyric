package com.example.islandlyrics.ui.overlay.superisland.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.islandlyrics.R

internal class SuperIslandActionIconRenderer(
    private val context: Context
) {
    fun render(
        isPlaying: Boolean,
        showPrevButton: Boolean
    ): SuperIslandActionIconBitmaps {
        val prevIconBitmap = if (showPrevButton) {
            renderButtonIcon(R.drawable.ic_skip_previous, 96, 0.5f, Color.parseColor("#FF111111"))
        } else {
            null
        }
        val prevIconBitmapDark = if (showPrevButton) {
            renderButtonIcon(R.drawable.ic_skip_previous, 96, 0.5f, Color.WHITE)
        } else {
            null
        }
        val playPauseResId = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        return SuperIslandActionIconBitmaps(
            prev = prevIconBitmap,
            prevDark = prevIconBitmapDark,
            playPause = renderButtonIcon(playPauseResId, 96, 0.42f, Color.parseColor("#FF111111")),
            playPauseDark = renderButtonIcon(playPauseResId, 96, 0.42f, Color.WHITE),
            next = renderButtonIcon(R.drawable.ic_skip_next, 96, 0.5f, Color.parseColor("#FF111111")),
            nextDark = renderButtonIcon(R.drawable.ic_skip_next, 96, 0.5f, Color.WHITE)
        )
    }

    private fun renderButtonIcon(
        resourceId: Int,
        size: Int,
        iconScale: Float,
        iconTint: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val drawable = context.getDrawable(resourceId) ?: return bitmap
        drawable.mutate().setTint(iconTint)
        val iconSize = (size * iconScale).toInt()
        val margin = (size - iconSize) / 2
        drawable.setBounds(margin, margin, margin + iconSize, margin + iconSize)
        drawable.draw(canvas)
        return bitmap
    }
}

internal data class SuperIslandActionIconBitmaps(
    val prev: Bitmap?,
    val prevDark: Bitmap?,
    val playPause: Bitmap,
    val playPauseDark: Bitmap,
    val next: Bitmap,
    val nextDark: Bitmap
)
