package com.example.islandlyrics.data.lyric

enum class OnlineLyricProvider(
    val id: String,
    val displayName: String
) {
    QQMusic("qq_music", "QQ音乐"),
    Kugou("kugou", "酷狗"),
    SodaMusic("soda_music", "汽水音乐"),
    Lrclib("lrclib", "LRCLIB"),
    Netease("netease", "网易云"),
    LrcApi("lrc_api", "LrcApi");

    companion object {
        fun fromId(id: String?): OnlineLyricProvider? {
            if (id.isNullOrBlank()) return null
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
        }

        fun defaultOrder(): List<OnlineLyricProvider> = listOf(QQMusic, Kugou, SodaMusic, Lrclib, LrcApi, Netease)

        fun defaultIds(): List<String> = defaultOrder().map { it.id }

        fun normalizeOrder(ids: List<String>?): List<OnlineLyricProvider> {
            val resolved = ids.orEmpty()
                .mapNotNull(::fromId)
                .distinct()
                .toMutableList()

            for (provider in defaultOrder()) {
                if (provider !in resolved) {
                    resolved += provider
                }
            }
            return resolved
        }
    }
}
