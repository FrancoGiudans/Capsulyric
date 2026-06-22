package com.example.islandlyrics.ui.overlay.display
import android.graphics.Bitmap
import androidx.palette.graphics.Palette

internal class AlbumArtColorExtractor(
    private val defaultColor: Int
) {
    var currentColor: Int = defaultColor
        private set

    private var lastExtractedArtHash = 0

    fun reset() {
        currentColor = defaultColor
        lastExtractedArtHash = 0
    }

    fun extract(
        bitmap: Bitmap?,
        currentBitmapHashProvider: () -> Int?
    ) {
        if (bitmap == null) {
            reset()
            return
        }

        val artHash = bitmap.hashCode()
        if (artHash == lastExtractedArtHash) return

        Palette.from(bitmap).generate { palette ->
            if (artHash != currentBitmapHashProvider()) return@generate
            if (palette != null) {
                currentColor = palette.getVibrantColor(
                    palette.getMutedColor(
                        palette.getDominantColor(defaultColor)
                    )
                )
                lastExtractedArtHash = artHash
            }
        }
    }
}
