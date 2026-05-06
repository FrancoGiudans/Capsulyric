package com.example.islandlyrics.data.lyric

import android.content.Context
import com.example.islandlyrics.data.LyricRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class OnlineLyricCacheStore(context: Context) {

    data class MatchOverride(
        val title: String?,
        val artist: String?,
        val updatedAt: Long
    )

    data class CachedLyricHit(
        val result: OnlineLyricFetcher.LyricResult,
        val cachedAt: Long,
        val updatedAt: Long,
        val queryTitle: String,
        val queryArtist: String,
        val hasCustomMatch: Boolean
    )

    data class LyricCacheEntrySummary(
        val id: String,
        val packageName: String,
        val title: String,
        val artist: String,
        val queryTitle: String,
        val queryArtist: String,
        val providerLabel: String,
        val hasCustomMatch: Boolean,
        val hasTranslation: Boolean,
        val hasRomanization: Boolean,
        val cachedAt: Long,
        val updatedAt: Long,
        val sizeBytes: Long
    )

    data class LyricCacheStats(
        val entryCount: Int = 0,
        val totalBytes: Long = 0L,
        val lastUpdatedAt: Long? = null
    )

    data class CurrentSongCacheState(
        val effectiveTitle: String,
        val effectiveArtist: String,
        val querySource: QuerySource,
        val matchOverride: MatchOverride?,
        val cachedLyricUpdatedAt: Long? = null,
        val cachedProviderLabel: String? = null
    )

    enum class QuerySource {
        DEFAULT_METADATA,
        RAW_METADATA,
        CUSTOM_OVERRIDE
    }

    private data class CacheEntry(
        val id: String,
        val packageName: String,
        val title: String,
        val artist: String,
        val rawTitle: String,
        val rawArtist: String,
        val duration: Long,
        val overrideTitle: String? = null,
        val overrideArtist: String? = null,
        val overrideUpdatedAt: Long? = null,
        val queryTitle: String? = null,
        val queryArtist: String? = null,
        val api: String? = null,
        val providerId: String? = null,
        val lyrics: String? = null,
        val hasSyllable: Boolean = false,
        val matchedTitle: String? = null,
        val matchedArtist: String? = null,
        val translationLyrics: String? = null,
        val romanLyrics: String? = null,
        val hasTranslation: Boolean = false,
        val hasRomanization: Boolean = false,
        val parsedLines: List<OnlineLyricFetcher.LyricLine> = emptyList(),
        val cachedAt: Long? = null,
        val updatedAt: Long = System.currentTimeMillis()
    )

    private val appContext = context.applicationContext
    private val storeFile = File(File(appContext.filesDir, "cache_store"), "online_lyric_cache.json")
    private val lock = Any()

    fun resolveQuery(
        mediaInfo: LyricRepository.MediaInfo,
        fallbackTitle: String,
        fallbackArtist: String,
        useRawMetadata: Boolean
    ): Pair<String, String> {
        val currentState = getCurrentSongState(mediaInfo, fallbackTitle, fallbackArtist, useRawMetadata)
        return currentState.effectiveTitle to currentState.effectiveArtist
    }

    fun getCurrentSongState(
        mediaInfo: LyricRepository.MediaInfo,
        fallbackTitle: String,
        fallbackArtist: String,
        useRawMetadata: Boolean
    ): CurrentSongCacheState {
        val entry = synchronized(lock) {
            readEntries().firstMatching(mediaInfo)
        }
        val matchOverride = entry?.overrideTitle
            ?.let {
                MatchOverride(
                    title = entry.overrideTitle.takeIf { value -> value.isNotBlank() },
                    artist = entry.overrideArtist?.takeIf { value -> value.isNotBlank() },
                    updatedAt = entry.overrideUpdatedAt ?: entry.updatedAt
                )
            }
            ?: entry?.overrideArtist?.let {
                MatchOverride(
                    title = entry.overrideTitle?.takeIf { value -> value.isNotBlank() },
                    artist = entry.overrideArtist.takeIf { value -> value.isNotBlank() },
                    updatedAt = entry.overrideUpdatedAt ?: entry.updatedAt
                )
            }

        val rawFallbackTitle = mediaInfo.rawTitle.ifBlank { fallbackTitle }
        val rawFallbackArtist = mediaInfo.rawArtist.ifBlank { fallbackArtist }

        val effective = when {
            matchOverride != null && (!matchOverride.title.isNullOrBlank() || !matchOverride.artist.isNullOrBlank()) -> {
                Triple(
                    matchOverride.title ?: rawFallbackTitle,
                    matchOverride.artist ?: rawFallbackArtist,
                    QuerySource.CUSTOM_OVERRIDE
                )
            }
            useRawMetadata && (mediaInfo.rawTitle.isNotBlank() || mediaInfo.rawArtist.isNotBlank()) -> {
                Triple(
                    rawFallbackTitle,
                    rawFallbackArtist,
                    QuerySource.RAW_METADATA
                )
            }
            else -> Triple(fallbackTitle, fallbackArtist, QuerySource.DEFAULT_METADATA)
        }

        val hasCachedLyric = entry != null && !entry.lyrics.isNullOrBlank() && entry.parsedLines.isNotEmpty()
        return CurrentSongCacheState(
            effectiveTitle = effective.first,
            effectiveArtist = effective.second,
            querySource = effective.third,
            matchOverride = matchOverride,
            cachedLyricUpdatedAt = if (hasCachedLyric) entry.cachedAt else null,
            cachedProviderLabel = if (hasCachedLyric) entry.api else null
        )
    }

    fun getCachedLyric(
        mediaInfo: LyricRepository.MediaInfo,
        queryTitle: String,
        queryArtist: String
    ): CachedLyricHit? = synchronized(lock) {
        val entries = readEntries()
        val entry = entries.firstMatching(mediaInfo) ?: return null
        if (entry.queryTitle != queryTitle || entry.queryArtist != queryArtist) return null
        if (entry.lyrics.isNullOrBlank() || entry.parsedLines.isEmpty()) return null

        val now = System.currentTimeMillis()
        val updatedEntry = entry.copy(updatedAt = now)
        writeEntries(entries.replace(updatedEntry))

        CachedLyricHit(
            result = OnlineLyricFetcher.LyricResult(
                api = entry.api ?: "Cache",
                lyrics = entry.lyrics,
                parsedLines = entry.parsedLines,
                hasSyllable = entry.hasSyllable,
                provider = OnlineLyricProvider.fromId(entry.providerId) ?: OnlineLyricProvider.LrcApi,
                matchedTitle = entry.matchedTitle,
                matchedArtist = entry.matchedArtist,
                translationLyrics = entry.translationLyrics,
                romanLyrics = entry.romanLyrics
            ),
            cachedAt = entry.cachedAt ?: now,
            updatedAt = now,
            queryTitle = entry.queryTitle.orEmpty(),
            queryArtist = entry.queryArtist.orEmpty(),
            hasCustomMatch = !entry.overrideTitle.isNullOrBlank() || !entry.overrideArtist.isNullOrBlank()
        )
    }

    fun saveLyricResult(
        mediaInfo: LyricRepository.MediaInfo,
        queryTitle: String,
        queryArtist: String,
        result: OnlineLyricFetcher.LyricResult
    ) = synchronized(lock) {
        val entries = readEntries().toMutableList()
        val id = buildEntryId(mediaInfo)
        val existing = entries.firstMatching(mediaInfo)
        val now = System.currentTimeMillis()
        val merged = CacheEntry(
            id = id,
            packageName = mediaInfo.packageName,
            title = mediaInfo.title,
            artist = mediaInfo.artist,
            rawTitle = mediaInfo.rawTitle,
            rawArtist = mediaInfo.rawArtist,
            duration = mediaInfo.duration,
            overrideTitle = existing?.overrideTitle,
            overrideArtist = existing?.overrideArtist,
            overrideUpdatedAt = existing?.overrideUpdatedAt,
            queryTitle = queryTitle,
            queryArtist = queryArtist,
            api = result.api,
            providerId = result.provider.id,
            lyrics = result.lyrics,
            hasSyllable = result.hasSyllable,
            matchedTitle = result.matchedTitle,
            matchedArtist = result.matchedArtist,
            translationLyrics = result.translationLyrics,
            romanLyrics = result.romanLyrics,
            hasTranslation = !result.translationLyrics.isNullOrBlank(),
            hasRomanization = !result.romanLyrics.isNullOrBlank(),
            parsedLines = result.parsedLines.orEmpty(),
            cachedAt = now,
            updatedAt = now
        )
        writeEntries(entries.replace(merged))
    }

    fun saveMatchOverride(mediaInfo: LyricRepository.MediaInfo, title: String, artist: String) = synchronized(lock) {
        val sanitizedTitle = title.trim()
        val sanitizedArtist = artist.trim()
        val entries = readEntries().toMutableList()
        val id = buildEntryId(mediaInfo)
        val existing = entries.firstMatching(mediaInfo)
        val now = System.currentTimeMillis()
        val merged = CacheEntry(
            id = id,
            packageName = mediaInfo.packageName,
            title = mediaInfo.title,
            artist = mediaInfo.artist,
            rawTitle = mediaInfo.rawTitle,
            rawArtist = mediaInfo.rawArtist,
            duration = mediaInfo.duration,
            overrideTitle = sanitizedTitle,
            overrideArtist = sanitizedArtist,
            overrideUpdatedAt = now,
            updatedAt = now
        )
        writeEntries(entries.replace(merged))
    }

    fun clearMatchOverride(mediaInfo: LyricRepository.MediaInfo) {
        synchronized(lock) {
            val entries = readEntries().toMutableList()
            val id = buildEntryId(mediaInfo)
            val existing = entries.firstMatching(mediaInfo) ?: return
            val cleared = existing.copy(
                overrideTitle = null,
                overrideArtist = null,
                overrideUpdatedAt = null,
                queryTitle = null,
                queryArtist = null,
                api = null,
                providerId = null,
                lyrics = null,
                hasSyllable = false,
                matchedTitle = null,
                matchedArtist = null,
                translationLyrics = null,
                romanLyrics = null,
                hasTranslation = false,
                hasRomanization = false,
                parsedLines = emptyList(),
                cachedAt = null,
                updatedAt = System.currentTimeMillis()
            )
            writeEntries(entries.replace(cleared))
        }
    }

    fun getLyricCacheSummaries(): List<LyricCacheEntrySummary> = synchronized(lock) {
        readEntries()
            .filter {
                (!it.lyrics.isNullOrBlank() && it.parsedLines.isNotEmpty()) ||
                    (!it.overrideTitle.isNullOrBlank() && !it.overrideArtist.isNullOrBlank())
            }
            .sortedByDescending { it.updatedAt }
            .map { entry ->
                LyricCacheEntrySummary(
                    id = entry.id,
                    packageName = entry.packageName,
                    title = entry.title,
                    artist = entry.artist,
                    queryTitle = entry.queryTitle.orEmpty(),
                    queryArtist = entry.queryArtist.orEmpty(),
                    providerLabel = entry.api.orEmpty(),
                    hasCustomMatch = !entry.overrideTitle.isNullOrBlank() || !entry.overrideArtist.isNullOrBlank(),
                    hasTranslation = entry.hasTranslation,
                    hasRomanization = entry.hasRomanization,
                    cachedAt = entry.cachedAt ?: entry.updatedAt,
                    updatedAt = entry.updatedAt,
                    sizeBytes = estimateEntrySize(entry)
                )
            }
    }

    fun getLyricCacheStats(): LyricCacheStats = synchronized(lock) {
        val summaries = getLyricCacheSummaries()
        LyricCacheStats(
            entryCount = summaries.size,
            totalBytes = summaries.sumOf { it.sizeBytes },
            lastUpdatedAt = summaries.maxOfOrNull { it.updatedAt }
        )
    }

    fun deleteLyricEntry(entryId: String) = synchronized(lock) {
        val updated = readEntries().filterNot { it.id == entryId }
        writeEntries(updated)
    }

    fun clearLyricCache() = synchronized(lock) {
        val retained = readEntries().mapNotNull { entry ->
            if (entry.overrideTitle.isNullOrBlank() && entry.overrideArtist.isNullOrBlank()) {
                null
            } else {
                entry.copy(
                    queryTitle = null,
                    queryArtist = null,
                    api = null,
                    providerId = null,
                    lyrics = null,
                    hasSyllable = false,
                    matchedTitle = null,
                    matchedArtist = null,
                    translationLyrics = null,
                    romanLyrics = null,
                    hasTranslation = false,
                    hasRomanization = false,
                    parsedLines = emptyList(),
                    cachedAt = null,
                    updatedAt = System.currentTimeMillis()
                )
            }
        }
        writeEntries(retained)
    }

    private fun buildEntryId(mediaInfo: LyricRepository.MediaInfo): String {
        val rawTitle = mediaInfo.rawTitle.ifBlank { mediaInfo.title }
        val rawArtist = mediaInfo.rawArtist.ifBlank { mediaInfo.artist }
        val payload = listOf(
            mediaInfo.packageName.trim().lowercase(),
            normalizeKey(rawTitle),
            normalizeKey(rawArtist)
        ).joinToString("|")
        return sha256(payload)
    }

    private fun buildLegacyEntryId(mediaInfo: LyricRepository.MediaInfo): String {
        val rawTitle = mediaInfo.rawTitle.ifBlank { mediaInfo.title }
        val rawArtist = mediaInfo.rawArtist.ifBlank { mediaInfo.artist }
        val payload = listOf(
            mediaInfo.packageName.trim().lowercase(),
            normalizeKey(rawTitle),
            normalizeKey(rawArtist),
            mediaInfo.duration.toString()
        ).joinToString("|")
        return sha256(payload)
    }

    private fun List<CacheEntry>.firstMatching(mediaInfo: LyricRepository.MediaInfo): CacheEntry? {
        val id = buildEntryId(mediaInfo)
        val legacyId = buildLegacyEntryId(mediaInfo)
        return firstOrNull { it.id == id } ?: firstOrNull { it.id == legacyId }
    }

    private fun estimateEntrySize(entry: CacheEntry): Long {
        return entry.toJson().toString().toByteArray(Charsets.UTF_8).size.toLong()
    }

    private fun readEntries(): List<CacheEntry> {
        if (!storeFile.exists()) return emptyList()
        return runCatching {
            val root = JSONObject(storeFile.readText(Charsets.UTF_8))
            val array = root.optJSONArray("entries") ?: JSONArray()
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toEntry())
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeEntries(entries: List<CacheEntry>) {
        storeFile.parentFile?.mkdirs()
        val root = JSONObject().apply {
            put("version", 1)
            put("entries", JSONArray(entries.map { it.toJson() }))
        }
        storeFile.writeText(root.toString(), Charsets.UTF_8)
    }

    private fun List<CacheEntry>.replace(entry: CacheEntry): List<CacheEntry> {
        val updated = filterNot { it.id == entry.id }.toMutableList()
        updated.add(entry)
        return updated
    }

    private fun CacheEntry.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("packageName", packageName)
        put("title", title)
        put("artist", artist)
        put("rawTitle", rawTitle)
        put("rawArtist", rawArtist)
        put("duration", duration)
        put("overrideTitle", overrideTitle)
        put("overrideArtist", overrideArtist)
        put("overrideUpdatedAt", overrideUpdatedAt)
        put("queryTitle", queryTitle)
        put("queryArtist", queryArtist)
        put("api", api)
        put("providerId", providerId)
        put("lyrics", lyrics)
        put("hasSyllable", hasSyllable)
        put("matchedTitle", matchedTitle)
        put("matchedArtist", matchedArtist)
        put("translationLyrics", translationLyrics)
        put("romanLyrics", romanLyrics)
        put("hasTranslation", hasTranslation)
        put("hasRomanization", hasRomanization)
        put("cachedAt", cachedAt)
        put("updatedAt", updatedAt)
        put(
            "parsedLines",
            JSONArray(parsedLines.map { line ->
                JSONObject().apply {
                    put("startTime", line.startTime)
                    put("endTime", line.endTime)
                    put("text", line.text)
                    put("translation", line.translation)
                    put("roma", line.roma)
                    put(
                        "syllables",
                        JSONArray(line.syllables.orEmpty().map { syllable ->
                            JSONObject().apply {
                                put("startTime", syllable.startTime)
                                put("endTime", syllable.endTime)
                                put("text", syllable.text)
                            }
                        })
                    )
                }
            })
        )
    }

    private fun JSONObject.toEntry(): CacheEntry {
        val parsedLinesArray = optJSONArray("parsedLines") ?: JSONArray()
        val parsedLines = buildList {
            for (index in 0 until parsedLinesArray.length()) {
                val line = parsedLinesArray.getJSONObject(index)
                val syllablesArray = line.optJSONArray("syllables") ?: JSONArray()
                val syllables = buildList {
                    for (syllableIndex in 0 until syllablesArray.length()) {
                        val syllable = syllablesArray.getJSONObject(syllableIndex)
                        add(
                            OnlineLyricFetcher.SyllableInfo(
                                startTime = syllable.optLong("startTime"),
                                endTime = syllable.optLong("endTime"),
                                text = syllable.optString("text")
                            )
                        )
                    }
                }
                add(
                    OnlineLyricFetcher.LyricLine(
                        startTime = line.optLong("startTime"),
                        endTime = line.optLong("endTime"),
                        text = line.optString("text"),
                        syllables = syllables.ifEmpty { null },
                        translation = line.optNullableString("translation"),
                        roma = line.optNullableString("roma")
                    )
                )
            }
        }

        val translationLyrics = optNullableString("translationLyrics")
        val romanLyrics = optNullableString("romanLyrics")

        return CacheEntry(
            id = optString("id"),
            packageName = optString("packageName"),
            title = optString("title"),
            artist = optString("artist"),
            rawTitle = optString("rawTitle", optString("title")),
            rawArtist = optString("rawArtist", optString("artist")),
            duration = optLong("duration"),
            overrideTitle = optNullableString("overrideTitle"),
            overrideArtist = optNullableString("overrideArtist"),
            overrideUpdatedAt = optNullableLong("overrideUpdatedAt"),
            queryTitle = optNullableString("queryTitle"),
            queryArtist = optNullableString("queryArtist"),
            api = optNullableString("api"),
            providerId = optNullableString("providerId"),
            lyrics = optNullableString("lyrics"),
            hasSyllable = optBoolean("hasSyllable", false),
            matchedTitle = optNullableString("matchedTitle"),
            matchedArtist = optNullableString("matchedArtist"),
            translationLyrics = translationLyrics,
            romanLyrics = romanLyrics,
            hasTranslation = optBoolean("hasTranslation", !translationLyrics.isNullOrBlank()),
            hasRomanization = optBoolean("hasRomanization", !romanLyrics.isNullOrBlank()),
            parsedLines = parsedLines,
            cachedAt = optNullableLong("cachedAt"),
            updatedAt = optLong("updatedAt", System.currentTimeMillis())
        )
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotEmpty() }
    }

    private fun JSONObject.optNullableLong(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return optLong(key)
    }

    private fun normalizeKey(value: String): String {
        return value.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
