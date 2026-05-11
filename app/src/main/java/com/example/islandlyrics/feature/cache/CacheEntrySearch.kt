package com.example.islandlyrics.feature.cache

import com.example.islandlyrics.data.lyric.OnlineLyricCacheStore

internal fun List<OnlineLyricCacheStore.LyricCacheEntrySummary>.filterByCacheQuery(
    query: String
): List<OnlineLyricCacheStore.LyricCacheEntrySummary> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return this
    return filter { entry ->
        listOf(
            entry.id,
            entry.packageName,
            entry.title,
            entry.artist,
            entry.queryTitle,
            entry.queryArtist,
            entry.providerLabel
        ).any { it.contains(normalizedQuery, ignoreCase = true) }
    }
}
