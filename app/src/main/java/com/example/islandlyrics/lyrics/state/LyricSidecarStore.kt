package com.example.islandlyrics.lyrics.state

internal class LyricSidecarStore(
    private val maxSize: Int = DEFAULT_MAX_SIZE
) {
    data class Entry(
        val translation: String? = null,
        val roma: String? = null
    )

    private val entries = LinkedHashMap<String, Entry>()

    fun find(apiPath: String, lyric: String): Entry? {
        if (lyric.isBlank()) return null
        return entries[key(apiPath, lyric)]
    }

    fun put(
        apiPath: String,
        lyric: String,
        translation: String?,
        roma: String?
    ) {
        if (lyric.isBlank()) return
        val key = key(apiPath, lyric)
        val old = entries[key]
        entries[key] = Entry(
            translation = translation ?: old?.translation,
            roma = roma ?: old?.roma
        )
        trimToMaxSize()
    }

    fun putAll(
        apiPath: String,
        sidecars: Map<String, Pair<String?, String?>>
    ) {
        sidecars.forEach { (lyric, values) ->
            put(
                apiPath = apiPath,
                lyric = lyric,
                translation = values.first?.takeIf { it.isNotBlank() },
                roma = values.second?.takeIf { it.isNotBlank() }
            )
        }
    }

    private fun trimToMaxSize() {
        while (entries.size > maxSize) {
            val firstKey = entries.keys.firstOrNull() ?: break
            entries.remove(firstKey)
        }
    }

    private fun key(apiPath: String, lyric: String): String = "$apiPath\u0000$lyric"

    private companion object {
        private const val DEFAULT_MAX_SIZE = 300
    }
}
