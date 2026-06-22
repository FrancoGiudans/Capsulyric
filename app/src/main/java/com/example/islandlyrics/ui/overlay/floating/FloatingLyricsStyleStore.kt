package com.example.islandlyrics.ui.overlay.floating
import android.content.Context
import android.graphics.Color
import androidx.core.content.edit
import com.example.islandlyrics.core.settings.AppPreferences

internal data class FloatingLyricsStyle(
    val textSizeSp: Float,
    val baseTextColor: Int,
    val followAlbumColor: Boolean,
    val showAlbumArt: Boolean,
    val enableTextStroke: Boolean,
    val enableTextBackground: Boolean
) {
    fun textColor(albumColor: Int): Int =
        if (followAlbumColor) albumColor else baseTextColor
}

internal class FloatingLyricsStyleStore(
    private val context: Context
) {
    var style: FloatingLyricsStyle = load()
        private set

    fun reload(): FloatingLyricsStyle {
        style = load()
        return style
    }

    fun adjustTextSize(delta: Float): FloatingLyricsStyle {
        val prefs = prefs()
        val size = (prefs.getFloat(KEY_TEXT_SIZE, SIZE_DEFAULT) + delta).coerceIn(10f, 32f)
        prefs.edit { putFloat(KEY_TEXT_SIZE, size) }
        style = style.copy(textSizeSp = size)
        return style
    }

    fun cycleColor(): FloatingLyricsStyle {
        val prefs = prefs()
        val current = prefs.getInt(KEY_TEXT_COLOR, Color.WHITE)
        val idx = PRESET_COLORS.indexOf(current)
        val nextIdx = if (idx == -1) 0 else (idx + 1) % PRESET_COLORS.size
        val newColor = PRESET_COLORS[nextIdx]

        prefs.edit {
            putInt(KEY_TEXT_COLOR, newColor)
            if (style.followAlbumColor) {
                putBoolean(KEY_FOLLOW_ALBUM_COLOR, false)
            }
        }
        style = style.copy(baseTextColor = newColor, followAlbumColor = false)
        return style
    }

    private fun load(): FloatingLyricsStyle {
        val prefs = prefs()
        return FloatingLyricsStyle(
            textSizeSp = prefs.getFloat(KEY_TEXT_SIZE, SIZE_DEFAULT),
            baseTextColor = prefs.getInt(KEY_TEXT_COLOR, Color.WHITE),
            followAlbumColor = prefs.getBoolean(KEY_FOLLOW_ALBUM_COLOR, true),
            showAlbumArt = prefs.getBoolean(KEY_SHOW_ALBUM_ART, true),
            enableTextStroke = prefs.getBoolean(KEY_TEXT_STROKE, true),
            enableTextBackground = prefs.getBoolean(KEY_TEXT_BACKGROUND, false)
        )
    }

    private fun prefs() = AppPreferences.of(context)

    companion object {
        const val KEY_TEXT_SIZE = AppPreferences.Keys.FLOATING_TEXT_SIZE_SP
        const val KEY_TEXT_COLOR = AppPreferences.Keys.FLOATING_TEXT_COLOR
        const val KEY_FOLLOW_ALBUM_COLOR = AppPreferences.Keys.FLOATING_FOLLOW_ALBUM_COLOR
        const val KEY_SHOW_ALBUM_ART = AppPreferences.Keys.FLOATING_SHOW_ALBUM_ART
        const val KEY_TEXT_STROKE = AppPreferences.Keys.FLOATING_TEXT_STROKE
        const val KEY_TEXT_BACKGROUND = AppPreferences.Keys.FLOATING_TEXT_BACKGROUND
        const val SIZE_DEFAULT = 15f

        private val PRESET_COLORS = intArrayOf(
            Color.WHITE,
            0xFFFFFFCC.toInt(),
            0xFFCCFFFF.toInt(),
            0xFFCCFFCC.toInt(),
            0xFFFFCC99.toInt(),
            0xFFFF99CC.toInt()
        )
    }
}
