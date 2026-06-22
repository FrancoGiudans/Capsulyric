package com.example.islandlyrics.lyrics.cache

import android.content.Context
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
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

    data class LyricCacheExportData(
        val title: String,
        val artist: String,
        val queryTitle: String,
        val queryArtist: String,
        val lines: List<OnlineLyricFetcher.LyricLine>,
        val matchOverride: MatchOverride?
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

    // ── Index entry: lightweight metadata stored in index.json ──
    private data class IndexEntry(
        val packageName: String,
        val title: String,
        val artist: String,
        val queryTitle: String?,
        val queryArtist: String?,
        val api: String?,
        val providerId: String?,
        val hasSyllable: Boolean,
        val hasTranslation: Boolean,
        val hasRomanization: Boolean,
        val overrideTitle: String?,
        val overrideArtist: String?,
        val overrideUpdatedAt: Long?,
        val cachedAt: Long?,
        val updatedAt: Long,
        val sizeBytes: Long
    )

    // ── Full cache entry: stored in individual lyrics/{id}.json files ──
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

    // ── v2 file layout ──
    private val storeDir = File(appContext.filesDir, "cache_store")
    private val entriesDir = File(storeDir, "lyrics")
    private val indexFile = File(storeDir, "index.json")

    // ── v1 legacy file (for migration) ──
    private val legacyStoreFile = File(storeDir, "online_lyric_cache.json")

    private val lock = Any()

    @Volatile
    private var migrated = false

    // ── Public API (unchanged) ───────────────────────────────────────

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
            ensureMigrated()
            val id = buildEntryId(mediaInfo)
            val legacyId = buildLegacyEntryId(mediaInfo)
            val index = readIndex()
            val matchId = if (index.containsKey(id)) id
                else if (index.containsKey(legacyId)) legacyId
                else null
            matchId?.let { readEntryFile(it) }
        }
        val matchOverride = entry?.toMatchOverride()

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
        ensureMigrated()
        val id = buildEntryId(mediaInfo)
        val legacyId = buildLegacyEntryId(mediaInfo)
        val index = readIndex()
        val matchId = if (index.containsKey(id)) id
            else if (index.containsKey(legacyId)) legacyId
            else return null
        val entry = readEntryFile(matchId) ?: return null
        if (entry.queryTitle != queryTitle || entry.queryArtist != queryArtist) return null
        if (entry.lyrics.isNullOrBlank() || entry.parsedLines.isEmpty()) return null

        val now = System.currentTimeMillis()
        val updatedEntry = entry.copy(updatedAt = now)
        writeEntryFile(updatedEntry)

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
            queryTitle = entry.queryTitle,
            queryArtist = entry.queryArtist,
            hasCustomMatch = !entry.overrideTitle.isNullOrBlank() || !entry.overrideArtist.isNullOrBlank()
        )
    }

    fun saveLyricResult(
        mediaInfo: LyricRepository.MediaInfo,
        queryTitle: String,
        queryArtist: String,
        result: OnlineLyricFetcher.LyricResult
    ) = synchronized(lock) {
        ensureMigrated()
        val id = buildEntryId(mediaInfo)
        val existing = readEntryFile(id)
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
        writeEntryFile(merged)
    }

    fun saveMatchOverride(mediaInfo: LyricRepository.MediaInfo, title: String, artist: String) = synchronized(lock) {
        ensureMigrated()
        val sanitizedTitle = title.trim()
        val sanitizedArtist = artist.trim()
        val id = buildEntryId(mediaInfo)
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
        writeEntryFile(merged)
    }

    fun clearMatchOverride(mediaInfo: LyricRepository.MediaInfo) {
        synchronized(lock) {
            ensureMigrated()
            val id = buildEntryId(mediaInfo)
            val existing = readEntryFile(id) ?: return
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
            writeEntryFile(cleared)
        }
    }

    fun getLyricCacheSummaries(): List<LyricCacheEntrySummary> = synchronized(lock) {
        ensureMigrated()
        val index = readIndex()
        index.entries
            .filter { (_, ie) ->
                ie.cachedAt != null ||
                    !ie.overrideTitle.isNullOrBlank() ||
                    !ie.overrideArtist.isNullOrBlank()
            }
            .sortedByDescending { (_, ie) -> ie.updatedAt }
            .map { (id, ie) ->
                LyricCacheEntrySummary(
                    id = id,
                    packageName = ie.packageName,
                    title = ie.title,
                    artist = ie.artist,
                    queryTitle = ie.queryTitle.orEmpty(),
                    queryArtist = ie.queryArtist.orEmpty(),
                    providerLabel = ie.api.orEmpty(),
                    hasCustomMatch = !ie.overrideTitle.isNullOrBlank() || !ie.overrideArtist.isNullOrBlank(),
                    hasTranslation = ie.hasTranslation,
                    hasRomanization = ie.hasRomanization,
                    cachedAt = ie.cachedAt ?: ie.updatedAt,
                    updatedAt = ie.updatedAt,
                    sizeBytes = ie.sizeBytes
                )
            }
    }

    fun getLyricCacheStats(): LyricCacheStats = synchronized(lock) {
        ensureMigrated()
        val summaries = getLyricCacheSummaries()
        LyricCacheStats(
            entryCount = summaries.size,
            totalBytes = summaries.sumOf { it.sizeBytes },
            lastUpdatedAt = summaries.maxOfOrNull { it.updatedAt }
        )
    }

    fun deleteLyricEntry(entryId: String) = synchronized(lock) {
        ensureMigrated()
        deleteEntryFile(entryId)
    }

    fun getEntryExportData(entryId: String): LyricCacheExportData? = synchronized(lock) {
        ensureMigrated()
        val entry = readEntryFile(entryId) ?: return null
        val lines = entry.parsedLines.takeIf { it.isNotEmpty() } ?: return null
        LyricCacheExportData(
            title = entry.title,
            artist = entry.artist,
            queryTitle = entry.queryTitle.orEmpty(),
            queryArtist = entry.queryArtist.orEmpty(),
            lines = lines,
            matchOverride = entry.toMatchOverride()
        )
    }

    fun clearLyricCache() = synchronized(lock) {
        ensureMigrated()
        val index = readIndex()
        val idsToDelete = mutableListOf<String>()
        for ((id, ie) in index) {
            if (ie.overrideTitle.isNullOrBlank() && ie.overrideArtist.isNullOrBlank()) {
                // Fully delete: remove entry file
                entryFile(id).delete()
                idsToDelete.add(id)
            } else {
                // Retain override metadata, clear lyrics data
                val existing = readEntryFile(id)
                if (existing != null) {
                    val cleared = existing.copy(
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
                    writeEntryFile(cleared)
                }
            }
        }
        for (id in idsToDelete) {
            index.remove(id)
        }
        writeIndex(index)
    }

    // ── Backup: export / import cache to/from a directory ───────────

    /**
     * Export the entire lyric cache to [outputDir]/lyric_cache/.
     * Preserves the v2 multi-file layout: index.json + lyrics/{xx}/{id}.json.
     * Thread-safe, callable from any coroutine context.
     *
     * @return number of entries exported.
     */
    fun exportCacheToDir(outputDir: File): Int = synchronized(lock) {
        ensureMigrated()
        val cacheDir = File(outputDir, "lyric_cache")
        cacheDir.mkdirs()

        // Copy index.json
        if (indexFile.exists()) {
            indexFile.copyTo(File(cacheDir, "index.json"), overwrite = true)
        }

        // Copy all entry files, preserving subdirectory structure
        val lyricsDir = File(cacheDir, "lyrics")
        var count = 0
        if (entriesDir.exists()) {
            entriesDir.walkTopDown().forEach { file ->
                if (file.isFile && file.extension == "json") {
                    val relativePath = file.relativeTo(entriesDir)
                    val targetFile = File(lyricsDir, relativePath.path)
                    targetFile.parentFile?.mkdirs()
                    file.copyTo(targetFile, overwrite = true)
                    if (runCatching {
                        JSONObject(file.readText(Charsets.UTF_8)).toEntry().hasLyricContent()
                    }.getOrDefault(false)) {
                        count++
                    }
                }
            }
        }
        android.util.Log.i("OnlineLyricCache", "Exported $count cache entries to $cacheDir")
        count
    }

    /**
     * Import lyric cache from [inputDir]/lyric_cache/ into the current cache.
     * Reads index.json and entry files, merging with existing cache.
     * Entries with newer updatedAt overwrite existing; older entries are skipped.
     * Existing entries without lyric content do not block imported lyric content.
     * Thread-safe.
     *
     * @return number of entries imported (new or updated).
     */
    fun importCacheFromDir(inputDir: File): Int = synchronized(lock) {
        ensureMigrated()
        val cacheDir = File(inputDir, "lyric_cache")
        val importedIndexFile = File(cacheDir, "index.json")
        if (!importedIndexFile.exists()) {
            android.util.Log.w("OnlineLyricCache", "No lyric_cache/index.json found in $inputDir")
            return 0
        }

        val importedLyricsDir = File(cacheDir, "lyrics")
        var count = 0

        runCatching {
            val root = JSONObject(importedIndexFile.readText(Charsets.UTF_8))
            val entriesJson = root.optJSONObject("entries") ?: return 0

            val existingIndex = readIndex()

            for (key in entriesJson.keys()) {
                val importedIdxEntry = entriesJson.getJSONObject(key)
                val importedUpdatedAt = importedIdxEntry.optLong("updatedAt", 0L)

                // Read the full entry from imported lyrics dir
                val subDir = if (key.length >= 2) key.substring(0, 2) else "xx"
                val importedEntryFile = File(importedLyricsDir, "$subDir/$key.json")
                if (!importedEntryFile.exists()) continue

                val importedEntry = runCatching {
                    JSONObject(importedEntryFile.readText(Charsets.UTF_8)).toEntry()
                }.getOrNull() ?: continue
                val importedHasLyrics = importedEntry.hasLyricContent()

                // Merge: skip if existing lyric content is newer. A cleared cache entry may still
                // keep custom match metadata with a fresh updatedAt, but it must not block restore.
                val existing = existingIndex[key]
                if (existing != null) {
                    val shouldSkip = if (importedHasLyrics) {
                        existing.hasLyricContent() && existing.updatedAt >= importedUpdatedAt
                    } else {
                        existing.updatedAt >= importedUpdatedAt
                    }
                    if (shouldSkip) continue
                }

                // Write entry file and update index
                writeEntryFile(importedEntry)
                if (importedHasLyrics) count++
            }
        }.onFailure {
            android.util.Log.e("OnlineLyricCache", "Import failed: ${it.message}", it)
        }

        android.util.Log.i("OnlineLyricCache", "Imported $count cache entries from $cacheDir")
        count
    }

    // ── Migration ────────────────────────────────────────────────────

    /** Migrate from v1 (single JSON file) to v2 (split files + index) if needed. */
    private fun ensureMigrated() {
        if (migrated) return
        if (!legacyStoreFile.exists()) {
            migrated = true
            return
        }
        try {
            val root = JSONObject(legacyStoreFile.readText(Charsets.UTF_8))
            val array = root.optJSONArray("entries") ?: JSONArray()
            val index = mutableMapOf<String, IndexEntry>()

            entriesDir.mkdirs()

            for (i in 0 until array.length()) {
                val entry = array.getJSONObject(i).toEntry()
                writeEntryFileInternal(entry)
                index[entry.id] = entry.toIndexEntry()
            }

            writeIndex(index)

            // Migration complete — remove old file
            legacyStoreFile.delete()

            android.util.Log.i(
                "OnlineLyricCache",
                "Migrated ${index.size} entries from v1 → v2 (split files). Old cache file removed."
            )
        } catch (e: Exception) {
            android.util.Log.e("OnlineLyricCache", "Migration failed: ${e.message}", e)
            // If migration fails, leave old file untouched so it can be retried
        }
        migrated = true
    }

    // ── Index I/O ────────────────────────────────────────────────────

    private fun readIndex(): MutableMap<String, IndexEntry> {
        if (!indexFile.exists()) return mutableMapOf()
        return runCatching {
            val root = JSONObject(indexFile.readText(Charsets.UTF_8))
            val entriesJson = root.optJSONObject("entries") ?: return mutableMapOf()
            val map = mutableMapOf<String, IndexEntry>()
            for (key in entriesJson.keys()) {
                val obj = entriesJson.getJSONObject(key)
                map[key] = IndexEntry(
                    packageName = obj.optString("packageName"),
                    title = obj.optString("title"),
                    artist = obj.optString("artist"),
                    queryTitle = obj.optNullableString("queryTitle"),
                    queryArtist = obj.optNullableString("queryArtist"),
                    api = obj.optNullableString("api"),
                    providerId = obj.optNullableString("providerId"),
                    hasSyllable = obj.optBoolean("hasSyllable", false),
                    hasTranslation = obj.optBoolean("hasTranslation", false),
                    hasRomanization = obj.optBoolean("hasRomanization", false),
                    overrideTitle = obj.optNullableString("overrideTitle"),
                    overrideArtist = obj.optNullableString("overrideArtist"),
                    overrideUpdatedAt = obj.optNullableLong("overrideUpdatedAt"),
                    cachedAt = obj.optNullableLong("cachedAt"),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                    sizeBytes = obj.optLong("sizeBytes", 0L)
                )
            }
            map
        }.getOrDefault(mutableMapOf())
    }

    private fun writeIndex(index: MutableMap<String, IndexEntry>) {
        storeDir.mkdirs()
        val entriesJson = JSONObject()
        for ((id, ie) in index) {
            entriesJson.put(id, JSONObject().apply {
                put("packageName", ie.packageName)
                put("title", ie.title)
                put("artist", ie.artist)
                ie.queryTitle?.let { put("queryTitle", it) }
                ie.queryArtist?.let { put("queryArtist", it) }
                ie.api?.let { put("api", it) }
                ie.providerId?.let { put("providerId", it) }
                put("hasSyllable", ie.hasSyllable)
                put("hasTranslation", ie.hasTranslation)
                put("hasRomanization", ie.hasRomanization)
                ie.overrideTitle?.let { put("overrideTitle", it) }
                ie.overrideArtist?.let { put("overrideArtist", it) }
                ie.overrideUpdatedAt?.let { put("overrideUpdatedAt", it) }
                ie.cachedAt?.let { put("cachedAt", it) }
                put("updatedAt", ie.updatedAt)
                put("sizeBytes", ie.sizeBytes)
            })
        }
        val root = JSONObject().apply {
            put("version", 2)
            put("entries", entriesJson)
        }
        indexFile.writeText(root.toString(), Charsets.UTF_8)
    }

    // ── Entry file I/O ───────────────────────────────────────────────

    private fun entryFile(id: String): File {
        // Use first 2 hex chars as subdirectory to avoid too many files in one dir
        val subDir = if (id.length >= 2) id.substring(0, 2) else "xx"
        return File(File(entriesDir, subDir), "$id.json")
    }

    private fun readEntryFile(id: String): CacheEntry? {
        val file = entryFile(id)
        if (!file.exists()) return null
        return runCatching {
            JSONObject(file.readText(Charsets.UTF_8)).toEntry()
        }.getOrNull()
    }

    /** Writes entry file AND updates index. */
    private fun writeEntryFile(entry: CacheEntry) {
        writeEntryFileInternal(entry)
        val index = readIndex()
        index[entry.id] = entry.toIndexEntry()
        writeIndex(index)
    }

    /** Writes only the entry file (no index update). Used during migration. */
    private fun writeEntryFileInternal(entry: CacheEntry) {
        val file = entryFile(entry.id)
        file.parentFile?.mkdirs()
        file.writeText(entry.toJson().toString(), Charsets.UTF_8)
    }

    /** Deletes entry file AND removes from index. */
    private fun deleteEntryFile(id: String) {
        entryFile(id).delete()
        val index = readIndex()
        index.remove(id)
        writeIndex(index)
    }

    // ── ID computation ───────────────────────────────────────────────

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

    // ── Conversion helpers ───────────────────────────────────────────

    private fun CacheEntry.toIndexEntry(): IndexEntry {
        return IndexEntry(
            packageName = packageName,
            title = title,
            artist = artist,
            queryTitle = queryTitle,
            queryArtist = queryArtist,
            api = api,
            providerId = providerId,
            hasSyllable = hasSyllable,
            hasTranslation = hasTranslation,
            hasRomanization = hasRomanization,
            overrideTitle = overrideTitle,
            overrideArtist = overrideArtist,
            overrideUpdatedAt = overrideUpdatedAt,
            cachedAt = cachedAt,
            updatedAt = updatedAt,
            sizeBytes = estimateEntrySize(this)
        )
    }

    private fun CacheEntry.toMatchOverride(): MatchOverride? {
        val title = overrideTitle?.takeIf { it.isNotBlank() }
        val artist = overrideArtist?.takeIf { it.isNotBlank() }
        if (title == null && artist == null) return null
        return MatchOverride(
            title = title,
            artist = artist,
            updatedAt = overrideUpdatedAt ?: updatedAt
        )
    }

    private fun CacheEntry.hasLyricContent(): Boolean {
        return cachedAt != null && !lyrics.isNullOrBlank() && parsedLines.isNotEmpty()
    }

    private fun estimateEntrySize(entry: CacheEntry): Long {
        return entry.toJson().toString().toByteArray(Charsets.UTF_8).size.toLong()
    }

    // ── JSON serialization ───────────────────────────────────────────

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

    private fun IndexEntry.hasLyricContent(): Boolean {
        return cachedAt != null && (!queryTitle.isNullOrBlank() || !queryArtist.isNullOrBlank())
    }

    private fun normalizeKey(value: String): String {
        return value.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

