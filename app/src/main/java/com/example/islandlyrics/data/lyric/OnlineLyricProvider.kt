package com.example.islandlyrics.data.lyric

enum class OnlineLyricProvider(
    val id: String,
    @param:androidx.annotation.StringRes val nameResId: Int
) {
    QQMusic("qq_music", com.example.islandlyrics.R.string.provider_qq_music),
    Kugou("kugou", com.example.islandlyrics.R.string.provider_kugou_music),
    SodaMusic("soda_music", com.example.islandlyrics.R.string.provider_soda_music),
    Lrclib("lrclib", com.example.islandlyrics.R.string.provider_lrclib),
    Netease("netease", com.example.islandlyrics.R.string.provider_netease_music),
    LrcApi("lrc_api", com.example.islandlyrics.R.string.provider_lrcapi);

    companion object {
        fun fromId(id: String?): OnlineLyricProvider? {
            if (id.isNullOrBlank()) return null
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
        }

        fun defaultOrder(): List<OnlineLyricProvider> = listOf(QQMusic, Netease, Kugou, SodaMusic, LrcApi, Lrclib)

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

    fun displayName(context: android.content.Context): String = context.getString(nameResId)
}
