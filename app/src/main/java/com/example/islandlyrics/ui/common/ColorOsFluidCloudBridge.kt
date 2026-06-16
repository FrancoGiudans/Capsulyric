package com.example.islandlyrics.ui.common

import android.content.Context
import com.example.islandlyrics.core.logging.LogManager
import org.json.JSONObject

/**
 * Reflection wrapper around OPPO SeedlingSupportSDK.
 *
 * The SDK is not available from the normal public Gradle repositories used by
 * this project, so this class keeps ColorOS Fluid Cloud optional at runtime.
 */
class ColorOsFluidCloudBridge(private val context: Context) {

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

    fun buildMediaBusinessData(state: UIState): JSONObject {
        val shortLyric = trimCapsuleText(state.displayLyric.ifBlank { state.title })
        val songName = state.title.ifBlank { state.fullLyric.ifBlank { shortLyric } }
        val artist = state.artist.ifBlank { context.packageName }
        val progress = state.progressCurrent.takeIf { it >= 0 } ?: 0

        return JSONObject().apply {
            put("index", "0")
            put("playerArr", org.json.JSONArray().put(JSONObject().apply {
                put("smallPic", "@drawable/ic_music_note")
                put("title", shortLyric)
                put("logo", "@mipmap/ic_launcher")
                put("songName", songName)
                put("picture", "@drawable/ic_music_note")
                put("artist", artist)
                put("value", progress)
                put("duration", state.progressMax.coerceAtLeast(100))
                put("mediaId", state.mediaPackage.ifBlank { "capsulyric" })
                put("spectrumData", org.json.JSONArray(listOf(12, 42, 28, 52, 20)))
            }))
            put("voiceLabel", state.fullLyric.ifBlank { songName })
        }
    }

    fun updateExistingCards(state: UIState): Boolean {
        val clazz = seedlingToolClass ?: return false
        return runCatching {
            val cardMap = clazz.getMethod("getSeedlingCardMap").invoke(null) as? Map<*, *>
            val cards = cardMap?.values
                ?.flatMap { value -> (value as? Iterable<*>)?.toList().orEmpty() }
                .orEmpty()
            if (cards.isEmpty()) return false

            val data = buildMediaBusinessData(state)
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
        if (clean.length <= CAPSULE_TEXT_LIMIT) return clean
        return clean.take(CAPSULE_TEXT_LIMIT)
    }

    companion object {
        private const val TAG = "ColorOsFluidCloudBridge"
        const val CAPSULE_TEXT_LIMIT = 5
    }
}
