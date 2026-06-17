package com.example.islandlyrics.ui.common

import android.content.Context
import android.graphics.Color
import com.example.islandlyrics.core.logging.LogManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reflection wrapper around OPPO SeedlingSupportSDK.
 *
 * The SDK is not available from the normal public Gradle repositories used by
 * this project, so this class keeps ColorOS Fluid Cloud optional at runtime.
 */
class ColorOsFluidCloudBridge(private val context: Context) {

    data class RenderConfig(
        val lyricMode: String = LYRIC_MODE_STANDARD,
        val showLeftCoverInFullLyric: Boolean = true,
        val textColorEnabled: Boolean = false,
        val progressColorEnabled: Boolean = false,
        val colorSource: String = SuperIslandColorSource.ALBUM_ART,
        val customColor: Int = 0xFF3482FF.toInt()
    )

    private val seedlingToolClass: Class<*>? by lazy {
        runCatching { Class.forName("com.oplus.pantanal.seedling.util.SeedlingTool") }
            .getOrNull()
    }

    fun isSdkAvailable(): Boolean = seedlingToolClass != null

    fun isFluidCloudSupported(): Boolean {
        val clazz = seedlingToolClass ?: return false
        return runCatching {
            clazz.getMethod("isSupportFluidCloud", Context::class.java)
                .invoke(null, context) as? Boolean ?: false
        }.getOrElse {
            LogManager.getInstance().w(context, TAG, "Failed to query Fluid Cloud support: $it")
            false
        }
    }

    fun buildMediaBusinessData(state: UIState, config: RenderConfig = RenderConfig()): JSONObject {
        val lyric = resolvePrimaryLyricText(state)
        val compactLyric = resolveCompactLyricText(state)
        val capsuleRightText = trimCapsuleText(
            if (config.lyricMode == LYRIC_MODE_FULL) lyric else compactLyric
        )
        val title = state.title.ifBlank { lyric }
        val titleWithArtist = if (state.artist.isNotBlank() && title.isNotBlank()) {
            "$title - ${state.artist}"
        } else {
            title.ifBlank { context.packageName }
        }
        val songName = if (config.lyricMode == LYRIC_MODE_FULL && lyric.isNotBlank()) {
            lyric
        } else {
            title.ifBlank { lyric.ifBlank { capsuleRightText } }
        }
        val artist = when {
            config.lyricMode == LYRIC_MODE_FULL && state.artist.isNotBlank() && state.title.isNotBlank() ->
                "${state.artist} - ${state.title}"
            state.artist.isNotBlank() -> state.artist
            else -> context.packageName
        }
        val progress = state.progressCurrent.takeIf { it >= 0 } ?: 0
        val duration = state.progressMax.coerceAtLeast(100)
        val accentColor = SuperIslandColorSource.resolveColor(
            source = config.colorSource,
            albumColor = state.albumColor,
            customColor = config.customColor
        )
        val accentHex = colorToHex(accentColor)
        val coverResource = "@drawable/ic_music_note"

        return JSONObject().apply {
            put("index", "0")
            put("playerArr", JSONArray().put(JSONObject().apply {
                put("smallPic", coverResource)
                put("title", capsuleRightText)
                put("logo", "@mipmap/ic_launcher")
                put("songName", songName)
                put("picture", coverResource)
                put("artist", artist)
                put("value", progress)
                put("duration", duration)
                put("mediaId", state.mediaPackage.ifBlank { "capsulyric" })
                put("spectrumData", JSONArray(listOf(12, 42, 28, 52, 20)))
                put("lyric", lyric)
                put("fullLyric", lyric)
                put("compactLyric", compactLyric)
                put("titleWithArtist", titleWithArtist)
                put("showAlbumCover", config.lyricMode != LYRIC_MODE_FULL || config.showLeftCoverInFullLyric)
                put("colorize", config.textColorEnabled)
                put("progressColorize", config.progressColorEnabled)
                put("accentColor", accentHex)
                put("progressColor", accentHex)
            }))
            put("voiceLabel", lyric.ifBlank { songName })
            put("title", capsuleRightText)
            put("songName", songName)
            put("artist", artist)
            put("lyric", lyric)
            put("fullLyric", lyric)
            put("accentColor", accentHex)
        }
    }

    fun updateExistingCards(state: UIState, config: RenderConfig = RenderConfig()): Boolean {
        val clazz = seedlingToolClass ?: return false
        return runCatching {
            val cardMap = clazz.getMethod("getSeedlingCardMap").invoke(null) as? Map<*, *>
            val cards = cardMap?.values
                ?.flatMap { value -> (value as? Iterable<*>)?.toList().orEmpty() }
                .orEmpty()
            if (cards.isEmpty()) return false

            val data = buildMediaBusinessData(state, config)
            val updateMethod = clazz.methods.firstOrNull {
                it.name == "updateAllCardData" && it.parameterTypes.size == 3
            } ?: return false

            cards.forEach { card ->
                updateMethod.invoke(null, card, data, null)
            }
            true
        }.getOrElse {
            LogManager.getInstance().w(context, TAG, "Failed to update Fluid Cloud cards: $it")
            false
        }
    }

    private fun trimCapsuleText(text: String): String {
        val clean = text.trim()
        if (SuperIslandLyricLayout.calculateWeight(clean) <= CAPSULE_TEXT_WEIGHT_LIMIT) return clean
        return SuperIslandLyricLayout.takeByWeight(clean, CAPSULE_TEXT_WEIGHT_LIMIT)
            .ifBlank { clean.take(CAPSULE_TEXT_LIMIT) }
    }

    private fun resolvePrimaryLyricText(state: UIState): String {
        val candidates = if (state.isTimingGapPlaceholder && !state.preferMetadataLayout) {
            sequenceOf(state.fullLyric, state.displayLyric)
        } else {
            sequenceOf(state.fullLyric, state.displayLyric, state.title)
        }
        return candidates.firstOrNull { !isLyricPlaceholder(it) } ?: "♪"
    }

    private fun resolveCompactLyricText(state: UIState): String {
        val candidates = if (state.isTimingGapPlaceholder && !state.preferMetadataLayout) {
            sequenceOf(state.displayLyric)
        } else {
            sequenceOf(state.displayLyric, state.title)
        }
        return candidates.firstOrNull { !isLyricPlaceholder(it) } ?: "♪"
    }

    private fun isLyricPlaceholder(text: String): Boolean {
        return text.isBlank() || text.trim() == "♪"
    }

    private fun colorToHex(color: Int): String {
        return String.format("#%08X", Color.argb(Color.alpha(color), Color.red(color), Color.green(color), Color.blue(color)))
    }

    companion object {
        private const val TAG = "ColorOsFluidCloudBridge"
        private const val LYRIC_MODE_STANDARD = "standard"
        private const val LYRIC_MODE_FULL = "full"
        const val CAPSULE_TEXT_LIMIT = 5
        const val CAPSULE_TEXT_WEIGHT_LIMIT = CAPSULE_TEXT_LIMIT * 2
    }
}
