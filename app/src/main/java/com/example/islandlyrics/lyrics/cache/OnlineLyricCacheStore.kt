/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.example.islandlyrics.lyrics.cache

import android.content.Context
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import com.example.islandlyrics.lyrics.online.provider.AppleMusicMediaRef
import com.example.islandlyrics.lyrics.importer.LyricifyDatabaseImporter
import com.example.islandlyrics.lyrics.importer.LyricifyImportMode
import com.example.islandlyrics.lyrics.importer.LyricifyImportSelection
import com.example.islandlyrics.lyrics.importer.LyricifyTrackIdentity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

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

    data class LyricCacheDetail(
        val id: String,
        val packageName: String,
        val title: String,
        val artist: String,
        val queryTitle: String,
        val queryArtist: String,
        val providerLabel: String,
        val isInstrumental: Boolean,
        val lyrics: String,
        val translationLyrics: String?,
        val romanLyrics: String?,
        val hasSyllable: Boolean,
        val hasCustomMatch: Boolean,
        val cachedAt: Long?,
        val updatedAt: Long
    )

    data class CurrentSongCacheState(
        val effectiveTitle: String,
        val effectiveArtist: String,
        val querySource: QuerySource,
        val matchOverride: MatchOverride?,
        val cachedLyricUpdatedAt: Long? = null,
        val cachedProviderLabel: String? = null,
        val isInstrumental: Boolean = false,
        val isAlbumInstrumental: Boolean = false
    )

    enum class QuerySource {
        DEFAULT_METADATA,
        RAW_METADATA,
        CUSTOM_OVERRIDE
    }

    // ── Index entry: lightweight metadata stored in index.json ──
    // ── Album-level instrumental mark (stored in album_instrumental.json) ──
    private data class AlbumInstrumentalMark(
        val album: String,
        val artist: String,
        val updatedAt: Long
    )

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
        val instrumental: Boolean,
        val instrumentalUpdatedAt: Long?,
        val cachedAt: Long?,
        val updatedAt: Long,
        val sizeBytes: Long,
        val lyricifyImported: Boolean = false
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
        val instrumental: Boolean = false,
        val instrumentalUpdatedAt: Long? = null,
        val queryTitle: String? = null,
        val queryArtist: String? = null,
        val api: String? = null,
        val providerId: String? = null,
        val lyrics: String? = null,
        val hasSyllable: Boolean = false,
        val matchedTitle: String? = null,
        val matchedArtist: String? = null,
        val matchedAlbum: String? = null,
        val matchedDurationMs: Long? = null,
        val providerTrackId: String? = null,
        val isrc: String? = null,
        val translationLyrics: String? = null,
        val romanLyrics: String? = null,
        val hasTranslation: Boolean = false,
        val hasRomanization: Boolean = false,
        val parsedLines: List<OnlineLyricFetcher.LyricLine> = emptyList(),
        val cachedAt: Long? = null,
        val updatedAt: Long = System.currentTimeMillis(),
        val lyricifyImported: Boolean = false,
        val lyricifyAliases: Map<String, String> = emptyMap(),
        val lyricifyCandidates: List<CacheEntry> = emptyList()
    )

    private val appContext = context.applicationContext

    // ── v2 file layout ──
    private val storeDir = File(appContext.filesDir, "cache_store")
    private val entriesDir = File(storeDir, "lyrics")
    private val indexFile = File(storeDir, "index.json")
    private val albumMarksFile = File(storeDir, "album_instrumental.json")
    private val importTransactionDir = File(storeDir, "lyricify-import-transaction")

    // ── v1 legacy file (for migration) ──
    private val legacyStoreFile = File(storeDir, "online_lyric_cache.json")

    private val lock = storeLock

    private companion object {
        val storeLock = Any()
    }

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

        val cacheEntry = entry
        val canUseCachedLyric = cacheEntry != null &&
            !cacheEntry.instrumental &&
            !cacheEntry.lyrics.isNullOrBlank() &&
            cacheEntry.parsedLines.isNotEmpty()
        val albumInstrumental = isAlbumInstrumental(mediaInfo)
        return CurrentSongCacheState(
            effectiveTitle = effective.first,
            effectiveArtist = effective.second,
            querySource = effective.third,
            matchOverride = matchOverride,
            cachedLyricUpdatedAt = if (canUseCachedLyric) cacheEntry.cachedAt else null,
            cachedProviderLabel = if (canUseCachedLyric) cacheEntry.api else null,
            isInstrumental = cacheEntry?.instrumental == true || albumInstrumental,
            isAlbumInstrumental = albumInstrumental
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
            else null
        val current = matchId?.let { readEntryFile(it) }
        if (current?.instrumental == true) return null
        val entry = current?.takeIf { !it.lyrics.isNullOrBlank() && it.parsedLines.isNotEmpty() }
            ?: findImportedEntry(mediaInfo, queryTitle, queryArtist, index) ?: return null
        if (entry.instrumental) return null
        val queryTitleMatches = entry.lyricifyImported || entry.queryTitle.isNullOrBlank() ||
            entry.queryTitle.trim().equals(queryTitle.trim(), ignoreCase = true)
        val queryArtistMatches = entry.lyricifyImported || entry.queryArtist.isNullOrBlank() ||
            entry.queryArtist.trim().equals(queryArtist.trim(), ignoreCase = true)
        if (!queryTitleMatches || !queryArtistMatches) return null
        if (entry.lyrics.isNullOrBlank() || entry.parsedLines.isEmpty()) return null

        val now = System.currentTimeMillis()
        val updatedEntry = entry.copy(updatedAt = now)
        writeEntryFile(updatedEntry)

        CachedLyricHit(
            result = entry.toLyricResult(),
            cachedAt = entry.cachedAt ?: now,
            updatedAt = now,
            queryTitle = entry.queryTitle ?: queryTitle,
            queryArtist = entry.queryArtist ?: queryArtist,
            hasCustomMatch = !entry.overrideTitle.isNullOrBlank() || !entry.overrideArtist.isNullOrBlank()
        )
    }

    fun getImportedCandidates(
        mediaInfo: LyricRepository.MediaInfo, queryTitle: String, queryArtist: String
    ): List<OnlineLyricFetcher.LyricResult> = synchronized(lock) {
        ensureMigrated()
        val entry = findImportedEntry(mediaInfo, queryTitle, queryArtist, readIndex()) ?: return emptyList()
        (listOf(entry) + entry.lyricifyCandidates).filter { it.hasLyricContent() }
            .map { it.toLyricResult() }.distinctBy(LyricifyDatabaseImporter::candidateKey)
    }

    /** Only imported entries participate in the shared lookup; existing player keys stay unchanged. */
    private fun findImportedEntry(
        mediaInfo: LyricRepository.MediaInfo, queryTitle: String, queryArtist: String,
        index: Map<String, IndexEntry>
    ): CacheEntry? {
        val imported = index.filterValues { it.lyricifyImported }
        if (imported.isEmpty()) return null
        val current = readEntryFile(buildEntryId(mediaInfo))
        val playerProvider = when (mediaInfo.packageName) {
            "com.tencent.qqmusic" -> OnlineLyricProvider.QQMusic
            "com.netease.cloudmusic" -> OnlineLyricProvider.Netease
            "com.kugou.android" -> OnlineLyricProvider.Kugou
            "com.luna.music" -> OnlineLyricProvider.SodaMusic
            "com.apple.android.music" -> OnlineLyricProvider.AppleMusic
            else -> null
        }
        val observedId = if (playerProvider == OnlineLyricProvider.AppleMusic) {
            AppleMusicMediaRef.extractSongId(mediaInfo.mediaId) ?: AppleMusicMediaRef.extractSongId(mediaInfo.mediaUri)
                ?: playerProvider.normalizeTrackId(mediaInfo.mediaId)
        } else playerProvider?.normalizeTrackId(mediaInfo.mediaId)
        val identity = LyricifyTrackIdentity(
            queryTitle, queryArtist, mediaInfo.album, mediaInfo.duration,
            current?.takeIf { it.hasLyricContent() }?.importIdentity()?.providerIds.orEmpty() +
                if (playerProvider != null) LyricifyDatabaseImporter.identityIds(playerProvider, observedId) else emptyMap()
        )
        val byMetadata = imported.filterValues {
            normalizeKey(it.title) == normalizeKey(queryTitle) && normalizeKey(it.artist) == normalizeKey(queryArtist)
        }.keys
        fun matching(ids: Collection<String>) = ids.mapNotNull { readEntryFile(it) }
            .filter { it.hasLyricContent() && LyricifyDatabaseImporter.sameTrack(identity, it.importIdentity()) }
        val matches = matching(byMetadata)
        if (matches.isNotEmpty()) return matches.singleOrNull()
        return if (identity.providerIds.isEmpty()) null else matching(imported.keys - byMetadata).singleOrNull()
    }

    fun hasImportedLyrics(): Boolean = synchronized(lock) {
        ensureMigrated()
        readIndex().values.any { it.lyricifyImported }
    }

    fun importLyricifyTracks(
        tracks: List<Pair<LyricifyTrackIdentity, LyricifyImportSelection>>, mode: LyricifyImportMode
    ): Pair<Int, Int> = importLyricifyTracks(tracks.asSequence(), mode, 0L) {}

    internal fun importLyricifyTracks(
        tracks: Sequence<Pair<LyricifyTrackIdentity, LyricifyImportSelection>>, mode: LyricifyImportMode,
        inputBytes: Long, checkActive: () -> Unit
    ): Pair<Int, Int> {
        check(storeDir.isDirectory || storeDir.mkdirs()) { "Cannot create the lyric cache directory" }
        val preparedDir = Files.createTempDirectory(appContext.cacheDir.toPath(), "lyricify-prepared-").toFile()
        var temporaryBytes = inputBytes
        fun reserve(bytes: Long) {
            // Leave room for a bounded atomic-copy buffer file during commit or recovery.
            LyricifyDatabaseImporter.checkSize(temporaryBytes + bytes + LyricifyDatabaseImporter.MAX_ENTRY_BYTES,
                LyricifyDatabaseImporter.MAX_TEMPORARY_BYTES, "Import temporary files")
            check(storeDir.usableSpace >= bytes + LyricifyDatabaseImporter.MAX_ENTRY_BYTES) { "Not enough free space to import lyrics" }
            temporaryBytes += bytes
        }
        fun stage(file: File, entry: CacheEntry) {
            val bytes = entry.toJson().toString().toByteArray(Charsets.UTF_8)
            LyricifyDatabaseImporter.checkSize(bytes.size.toLong(), LyricifyDatabaseImporter.MAX_ENTRY_BYTES, "Parsed song")
            reserve(bytes.size.toLong())
            file.writeBytes(bytes)
        }
        try {
            var count = 0
            for ((identity, selection) in tracks) {
                checkActive()
                require(++count <= LyricifyDatabaseImporter.MAX_TRACKS) { "Too many songs to import" }
                val base = CacheEntry(id = "", packageName = "Lyricify", title = identity.title, artist = identity.artist,
                    rawTitle = identity.title, rawArtist = identity.artist, duration = identity.durationMs,
                    queryTitle = identity.title, queryArtist = identity.artist)
                stage(File(preparedDir, "$count.json"), base.withImportedResult(selection.selected).copy(
                    lyricifyAliases = identity.providerIds, matchedAlbum = identity.album.ifBlank { selection.selected.matchedAlbum },
                    lyricifyCandidates = selection.candidates.map { base.withImportedResult(it) }
                ))
            }
            return synchronized(lock) {
                ensureMigrated()
                val index = readIndex(strict = true)
                val identities = mutableMapOf<String, LyricifyTrackIdentity>()
                val matchingKeys = mutableMapOf<String, MutableSet<String>>()
                fun keys(identity: LyricifyTrackIdentity) = listOf("text:${normalizeKey(identity.title)}|${normalizeKey(identity.artist)}") +
                    identity.providerIds.map { (platform, id) -> "id:$platform:${id.lowercase(java.util.Locale.ROOT)}" }
                fun rememberIdentity(id: String, identity: LyricifyTrackIdentity) {
                    identities[id]?.let { old -> keys(old).forEach { matchingKeys[it]?.remove(id) } }
                    identities[id] = identity
                    keys(identity).forEach { matchingKeys.getOrPut(it) { mutableSetOf() }.add(id) }
                }
                for ((id, header) in index) {
                    checkActive()
                    if (!header.hasLyricContent() && !header.instrumental) continue
                    require(id.matches(Regex("[0-9a-f]{64}"))) { "Invalid existing cache key" }
                    val json = readImportJson(entryFile(id))
                    require(json.getString("id") == id) { "Invalid existing cache identity" }
                    json.remove("parsedLines")
                    json.remove("lyricifyCandidates")
                    json.remove("lyrics")
                    json.remove("translationLyrics")
                    json.remove("romanLyrics")
                    rememberIdentity(id, json.toEntry().importIdentity())
                }
                check(importTransactionDir.mkdir()) { "Cannot create import transaction" }
                val stagedDir = File(importTransactionDir, "new").also { check(it.mkdir()) }
                val backupDir = File(importTransactionDir, "old").also { check(it.mkdir()) }
                val changedIds = linkedSetOf<String>()
                fun currentFile(id: String): File =
                    File(stagedDir, "$id.json").takeIf { it.exists() } ?: entryFile(id)
                fun currentEntry(id: String): CacheEntry = readImportJson(currentFile(id)).toEntry()
                var imported = 0
                var skipped = 0
                try {
                    for (position in 1..count) {
                        checkActive()
                        val incomingEntry = readImportJson(File(preparedDir, "$position.json")).toEntry()
                        val identity = incomingEntry.importIdentity()
                        val matchIds = keys(identity).flatMap { matchingKeys[it].orEmpty() }.distinct()
                            .filter { LyricifyDatabaseImporter.sameTrack(identity, identities.getValue(it)) }
                        if (mode == LyricifyImportMode.SKIP && matchIds.isNotEmpty()) { skipped++; continue }
                        LyricifyDatabaseImporter.checkSize(matchIds.sumOf { currentFile(it).length() },
                            LyricifyDatabaseImporter.MAX_ENTRY_BYTES, "Existing song cache")
                        val matches = matchIds.map(::currentEntry)
                        val shared = matches.firstOrNull { it.lyricifyImported }
                        val existingResults = matches.filter { it.hasLyricContent() }
                            .flatMap { listOf(it.toLyricResult()) + it.lyricifyCandidates.map { candidate -> candidate.toLyricResult() } }
                            .distinctBy(LyricifyDatabaseImporter::candidateKey)
                        val previous = existingResults.takeIf { it.isNotEmpty() }?.let {
                            LyricifyImportSelection(it, shared?.takeIf { it.hasLyricContent() }?.toLyricResult() ?: it.first())
                        }
                        val incoming = LyricifyImportSelection(incomingEntry.lyricifyCandidates.map { it.toLyricResult() }, incomingEntry.toLyricResult())
                        val selection = LyricifyDatabaseImporter.resolveSelection(previous, incoming, mode) ?: continue
                        var id = shared?.id ?: LyricifyDatabaseImporter.importedEntryId(identity)
                        if (shared == null) {
                            while (index.containsKey(id) || entryFile(id).exists()) id = sha256("$id|${UUID.randomUUID()}")
                        }
                        val oldAliases = matches.fold(emptyMap<String, String>()) { aliases, entry ->
                            LyricifyDatabaseImporter.mergeAliases(aliases, entry.importIdentity().providerIds)
                        }
                        val aliases = LyricifyDatabaseImporter.mergeAliases(oldAliases, identity.providerIds)
                        val base = incomingEntry.copy(id = id, lyricifyCandidates = emptyList())
                        val entry = base.withImportedResult(selection.selected).copy(
                            lyricifyImported = true, lyricifyAliases = aliases,
                            matchedAlbum = identity.album.ifBlank { selection.selected.matchedAlbum },
                            lyricifyCandidates = selection.candidates.map { base.withImportedResult(it).copy(lyricifyAliases = emptyMap()) }
                        )
                        val changed = if (mode == LyricifyImportMode.OVERWRITE) {
                            matches.filter { !it.lyricifyImported }.map { it.withImportedResult(selection.selected) }
                        } else emptyList()
                        for (updated in changed + entry) {
                            val file = File(stagedDir, "${updated.id}.json")
                            temporaryBytes -= file.length()
                            stage(file, updated)
                            changedIds.add(updated.id)
                            index[updated.id] = updated.toIndexEntry()
                            rememberIdentity(updated.id, updated.importIdentity())
                        }
                        imported++
                    }
                    if (changedIds.isEmpty()) return@synchronized imported to skipped
                    val stagedIndex = File(stagedDir, "index.json")
                    writeIndex(index, stagedIndex) { bytes ->
                        LyricifyDatabaseImporter.checkSize(bytes, LyricifyDatabaseImporter.MAX_ENTRY_BYTES, "Cache index")
                        reserve(bytes)
                    }
                    val existed = changedIds.filter { entryFile(it).exists() }
                    for (id in existed) {
                        checkActive()
                        val file = entryFile(id)
                        reserve(file.length())
                        file.copyTo(File(backupDir, "$id.json"))
                    }
                    if (indexFile.exists()) {
                        reserve(indexFile.length())
                        indexFile.copyTo(File(backupDir, "index.json"))
                    }
                    val manifest = JSONObject().put("ids", JSONArray(changedIds.toList()))
                        .put("existingIds", JSONArray(existed)).put("hadIndex", indexFile.exists())
                    val manifestBytes = manifest.toString().toByteArray(Charsets.UTF_8)
                    reserve(manifestBytes.size.toLong())
                    val preparedManifest = File(importTransactionDir, "manifest-prepared.json")
                    FileOutputStream(preparedManifest).use { output ->
                        output.write(manifestBytes); output.fd.sync()
                    }
                    Files.move(preparedManifest.toPath(), File(importTransactionDir, "manifest.json").toPath(), StandardCopyOption.ATOMIC_MOVE)
                    for (id in changedIds) {
                        checkActive()
                        atomicImportCopy(File(stagedDir, "$id.json"), entryFile(id), keepRecoverySpace = true)
                    }
                    checkActive()
                    atomicImportCopy(stagedIndex, indexFile, keepRecoverySpace = true)
                    checkActive()
                    val commitMarker = File(importTransactionDir, "commit-prepared")
                    FileOutputStream(commitMarker).use { it.fd.sync() }
                    Files.move(commitMarker.toPath(), File(importTransactionDir, "committed").toPath(), StandardCopyOption.ATOMIC_MOVE)
                    imported to skipped
                } catch (error: Throwable) {
                    try { recoverLyricifyImport() } catch (recoveryError: Throwable) { error.addSuppressed(recoveryError) }
                    throw error
                } finally {
                    if (File(importTransactionDir, "committed").exists() || !File(importTransactionDir, "manifest.json").exists()) {
                        importTransactionDir.deleteRecursively()
                    }
                }
            }
        } finally {
            preparedDir.deleteRecursively()
        }
    }

    private fun readImportJson(file: File): JSONObject {
        require(file.isFile) { "Missing existing cache file: ${file.name}" }
        LyricifyDatabaseImporter.checkSize(file.length(), LyricifyDatabaseImporter.MAX_ENTRY_BYTES, "Cache entry")
        return JSONObject(file.readText(Charsets.UTF_8))
    }

    private fun atomicImportCopy(source: File, destination: File, keepRecoverySpace: Boolean = false) {
        val parent = destination.parentFile!!
        check(parent.isDirectory || parent.mkdirs()) { "Cannot create cache entry directory" }
        val recoverySpace = if (keepRecoverySpace) LyricifyDatabaseImporter.MAX_ENTRY_BYTES else 0L
        check(storeDir.usableSpace >= source.length() + recoverySpace) { "Not enough free space to commit lyrics" }
        val temporary = File(parent, "${destination.name}.lyricify-tmp")
        try {
            FileOutputStream(temporary).use { output ->
                source.inputStream().use { it.copyTo(output) }
                output.fd.sync()
            }
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }

    /** Called under the existing store lock before any cache operation, including after a process restart. */
    private fun recoverLyricifyImport() {
        if (!importTransactionDir.exists()) return
        val manifestFile = File(importTransactionDir, "manifest.json")
        if (manifestFile.exists() && !File(importTransactionDir, "committed").exists()) {
            val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
            fun ids(key: String) = manifest.getJSONArray(key).let { array -> (0 until array.length()).map { array.getString(it) } }
            val ids = ids("ids")
            require(ids.all { it.matches(Regex("[0-9a-f]{64}")) }) { "Invalid import recovery record" }
            val existing = ids("existingIds").toSet()
            val backupDir = File(importTransactionDir, "old")
            fun restore(source: File, destination: File) {
                require(source.isFile) { "Missing import rollback backup" }
                if (!destination.isFile || fileDigest(source) != fileDigest(destination)) atomicImportCopy(source, destination)
            }
            fun removeTemporary(destination: File) {
                val temporary = File(destination.parentFile, "${destination.name}.lyricify-tmp")
                check(!temporary.exists() || temporary.delete()) { "Cannot remove an interrupted import copy" }
            }
            for (id in ids) {
                val file = entryFile(id)
                if (id in existing) restore(File(backupDir, "$id.json"), file)
                else check(!file.exists() || file.delete()) { "Cannot remove uncommitted lyric entry" }
                removeTemporary(file)
            }
            if (manifest.getBoolean("hadIndex")) restore(File(backupDir, "index.json"), indexFile)
            else check(!indexFile.exists() || indexFile.delete()) { "Cannot restore cache index" }
            removeTemporary(indexFile)
        }
        check(importTransactionDir.deleteRecursively()) { "Cannot clean up import recovery files" }
    }

    private fun fileDigest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun CacheEntry.importIdentity(): LyricifyTrackIdentity = LyricifyTrackIdentity(
        queryTitle ?: rawTitle.ifBlank { title }, queryArtist ?: rawArtist.ifBlank { artist },
        matchedAlbum.orEmpty(), duration.takeIf { it > 0 } ?: matchedDurationMs ?: 0L,
        lyricifyAliases + OnlineLyricProvider.fromId(providerId)?.let {
            LyricifyDatabaseImporter.identityIds(it, providerTrackId)
        }.orEmpty()
    )

    private fun CacheEntry.withImportedResult(result: OnlineLyricFetcher.LyricResult): CacheEntry = copy(
        instrumental = false, instrumentalUpdatedAt = null,
        api = result.api, providerId = result.provider.id, lyrics = result.lyrics, hasSyllable = result.hasSyllable,
        matchedTitle = result.matchedTitle, matchedArtist = result.matchedArtist, matchedAlbum = result.matchedAlbum,
        matchedDurationMs = result.matchedDurationMs, providerTrackId = result.providerTrackId, isrc = result.isrc,
        translationLyrics = result.translationLyrics, romanLyrics = result.romanLyrics,
        hasTranslation = !result.translationLyrics.isNullOrBlank(), hasRomanization = !result.romanLyrics.isNullOrBlank(),
        parsedLines = result.parsedLines.orEmpty(), cachedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
    )

    private fun CacheEntry.toLyricResult(): OnlineLyricFetcher.LyricResult = OnlineLyricFetcher.LyricResult(
        api = api ?: "Cache", lyrics = lyrics, parsedLines = parsedLines, hasSyllable = hasSyllable,
        provider = OnlineLyricProvider.fromId(providerId) ?: OnlineLyricProvider.LrcApi,
        matchedTitle = matchedTitle, matchedArtist = matchedArtist, matchedAlbum = matchedAlbum,
        matchedDurationMs = matchedDurationMs, providerTrackId = providerTrackId, isrc = isrc,
        translationLyrics = translationLyrics, romanLyrics = romanLyrics
    )

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
            instrumental = false,
            instrumentalUpdatedAt = null,
            queryTitle = queryTitle,
            queryArtist = queryArtist,
            api = result.api,
            providerId = result.provider.id,
            lyrics = result.lyrics,
            hasSyllable = result.hasSyllable,
            matchedTitle = result.matchedTitle,
            matchedArtist = result.matchedArtist,
            matchedAlbum = result.matchedAlbum,
            matchedDurationMs = result.matchedDurationMs,
            providerTrackId = result.providerTrackId,
            isrc = result.isrc,
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
            instrumental = false,
            instrumentalUpdatedAt = null,
            updatedAt = now
        )
        writeEntryFile(merged)
    }

    fun markInstrumental(mediaInfo: LyricRepository.MediaInfo) = synchronized(lock) {
        ensureMigrated()
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
            instrumental = true,
            instrumentalUpdatedAt = now,
            updatedAt = now
        )
        writeEntryFile(merged)
    }

    fun clearInstrumentalMarker(mediaInfo: LyricRepository.MediaInfo) {
        synchronized(lock) {
            ensureMigrated()
            val id = buildEntryId(mediaInfo)
            val existing = readEntryFile(id) ?: return
            val cleared = existing.copy(
                instrumental = false,
                instrumentalUpdatedAt = null,
                updatedAt = System.currentTimeMillis()
            )
            writeEntryFile(cleared)
        }
    }

    fun markAlbumInstrumental(mediaInfo: LyricRepository.MediaInfo) {
        synchronized(lock) {
            ensureMigrated()
            val album = mediaInfo.album.trim()
            val artist = mediaInfo.artist.ifBlank { mediaInfo.rawArtist }.trim()
            if (album.isBlank() || artist.isBlank()) return
            val marks = readAlbumMarks()
            marks[albumMarkKey(mediaInfo.packageName, album, artist)] = AlbumInstrumentalMark(
                album = album,
                artist = artist,
                updatedAt = System.currentTimeMillis()
            )
            writeAlbumMarks(marks)
        }
    }

    fun clearAlbumInstrumentalMarker(mediaInfo: LyricRepository.MediaInfo) {
        synchronized(lock) {
            ensureMigrated()
            val album = mediaInfo.album.trim()
            val artist = mediaInfo.artist.ifBlank { mediaInfo.rawArtist }.trim()
            if (album.isBlank() || artist.isBlank()) return
            val marks = readAlbumMarks()
            marks.remove(albumMarkKey(mediaInfo.packageName, album, artist))
            writeAlbumMarks(marks)
        }
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
                    ie.instrumental ||
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

    fun getLyricCacheDetail(entryId: String): LyricCacheDetail? = synchronized(lock) {
        ensureMigrated()
        val entry = readEntryFile(entryId) ?: return null
        val mainText = if (entry.parsedLines.isNotEmpty()) {
            val lyricLines = entry.parsedLines
                .map { it.text.trim() }
                .filter { it.isNotBlank() }
            val deduplicated = lyricLines.filterIndexed { index, text ->
                index == 0 || text != lyricLines[index - 1]
            }.joinToString("\n")
            deduplicated.ifBlank { cleanLyricsText(entry.lyrics) }
        } else {
            cleanLyricsText(entry.lyrics)
        }

        val translationText = if (entry.parsedLines.any { !it.translation.isNullOrBlank() }) {
            val lines = entry.parsedLines
                .mapNotNull { it.translation?.trim() }
                .filter { it.isNotBlank() }
            val deduplicated = lines.filterIndexed { index, text ->
                index == 0 || text != lines[index - 1]
            }.joinToString("\n")
            deduplicated.ifBlank { cleanLyricsText(entry.translationLyrics) }
        } else {
            cleanLyricsText(entry.translationLyrics)
        }

        val romanText = if (entry.parsedLines.any { !it.roma.isNullOrBlank() }) {
            val lines = entry.parsedLines
                .mapNotNull { it.roma?.trim() }
                .filter { it.isNotBlank() }
            val deduplicated = lines.filterIndexed { index, text ->
                index == 0 || text != lines[index - 1]
            }.joinToString("\n")
            deduplicated.ifBlank { cleanLyricsText(entry.romanLyrics) }
        } else {
            cleanLyricsText(entry.romanLyrics)
        }

        LyricCacheDetail(
            id = entry.id,
            packageName = entry.packageName,
            title = entry.title,
            artist = entry.artist,
            queryTitle = entry.queryTitle.orEmpty(),
            queryArtist = entry.queryArtist.orEmpty(),
            providerLabel = entry.api.orEmpty(),
            isInstrumental = entry.instrumental,
            lyrics = mainText,
            translationLyrics = translationText.takeIf { it.isNotBlank() },
            romanLyrics = romanText.takeIf { it.isNotBlank() },
            hasSyllable = entry.hasSyllable || entry.parsedLines.any { !it.syllables.isNullOrEmpty() },
            hasCustomMatch = !entry.overrideTitle.isNullOrBlank() || !entry.overrideArtist.isNullOrBlank(),
            cachedAt = entry.cachedAt,
            updatedAt = entry.updatedAt
        )
    }

    fun clearLyricCache() = synchronized(lock) {
        ensureMigrated()
        val index = readIndex()
        val idsToDelete = mutableListOf<String>()
        for ((id, ie) in index) {
            if (ie.overrideTitle.isNullOrBlank() && ie.overrideArtist.isNullOrBlank() && !ie.instrumental) {
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
        recoverLyricifyImport()
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

    // ── Album-level instrumental marks ───────────────────────────────

    private fun isAlbumInstrumental(mediaInfo: LyricRepository.MediaInfo): Boolean {
        val album = mediaInfo.album.trim()
        val artist = mediaInfo.artist.ifBlank { mediaInfo.rawArtist }.trim()
        if (album.isBlank() || artist.isBlank()) return false
        return readAlbumMarks().containsKey(albumMarkKey(mediaInfo.packageName, album, artist))
    }

    private fun readAlbumMarks(): MutableMap<String, AlbumInstrumentalMark> {
        if (!albumMarksFile.exists()) return mutableMapOf()
        return runCatching {
            val root = JSONObject(albumMarksFile.readText(Charsets.UTF_8))
            val marksJson = root.optJSONObject("marks") ?: return mutableMapOf()
            val map = mutableMapOf<String, AlbumInstrumentalMark>()
            for (key in marksJson.keys()) {
                val obj = marksJson.getJSONObject(key)
                map[key] = AlbumInstrumentalMark(
                    album = obj.optString("album"),
                    artist = obj.optString("artist"),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            }
            map
        }.getOrDefault(mutableMapOf())
    }

    private fun writeAlbumMarks(marks: Map<String, AlbumInstrumentalMark>) {
        storeDir.mkdirs()
        val marksJson = JSONObject()
        for ((key, mark) in marks) {
            marksJson.put(key, JSONObject().apply {
                put("album", mark.album)
                put("artist", mark.artist)
                put("updatedAt", mark.updatedAt)
            })
        }
        val root = JSONObject().apply {
            put("version", 1)
            put("marks", marksJson)
        }
        albumMarksFile.writeText(root.toString(), Charsets.UTF_8)
    }

    private fun albumMarkKey(packageName: String, album: String, artist: String): String {
        val payload = listOf(
            packageName.trim().lowercase(),
            normalizeKey(album),
            normalizeKey(artist)
        ).joinToString("|")
        return sha256(payload)
    }

    private fun readIndex(strict: Boolean = false): MutableMap<String, IndexEntry> {
        if (!indexFile.exists()) return mutableMapOf()
        return runCatching {
            if (strict) LyricifyDatabaseImporter.checkSize(indexFile.length(), LyricifyDatabaseImporter.MAX_ENTRY_BYTES, "Cache index")
            val root = JSONObject(indexFile.readText(Charsets.UTF_8))
            val entriesJson = root.optJSONObject("entries") ?: if (strict) error("Invalid cache index") else return mutableMapOf()
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
                    instrumental = obj.optBoolean("instrumental", false),
                    instrumentalUpdatedAt = obj.optNullableLong("instrumentalUpdatedAt"),
                    cachedAt = obj.optNullableLong("cachedAt"),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                    sizeBytes = obj.optLong("sizeBytes", 0L),
                    lyricifyImported = obj.optBoolean("lyricifyImported", false)
                )
            }
            map
        }.getOrElse { if (strict) throw it else mutableMapOf() }
    }

    private fun writeIndex(
        index: MutableMap<String, IndexEntry>, destination: File = indexFile, beforeWrite: (Long) -> Unit = {}
    ) {
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
                if (ie.instrumental) put("instrumental", true)
                ie.instrumentalUpdatedAt?.let { put("instrumentalUpdatedAt", it) }
                ie.cachedAt?.let { put("cachedAt", it) }
                put("updatedAt", ie.updatedAt)
                put("sizeBytes", ie.sizeBytes)
                if (ie.lyricifyImported) put("lyricifyImported", true)
            })
        }
        val root = JSONObject().apply {
            put("version", 2)
            put("entries", entriesJson)
        }
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        beforeWrite(bytes.size.toLong())
        destination.writeBytes(bytes)
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
            instrumental = instrumental,
            instrumentalUpdatedAt = instrumentalUpdatedAt,
            cachedAt = cachedAt,
            updatedAt = updatedAt,
            sizeBytes = estimateEntrySize(this),
            lyricifyImported = lyricifyImported
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
        put("instrumental", instrumental)
        put("instrumentalUpdatedAt", instrumentalUpdatedAt)
        put("queryTitle", queryTitle)
        put("queryArtist", queryArtist)
        put("api", api)
        put("providerId", providerId)
        put("lyrics", lyrics)
        put("hasSyllable", hasSyllable)
        put("matchedTitle", matchedTitle)
        put("matchedArtist", matchedArtist)
        put("matchedAlbum", matchedAlbum)
        put("matchedDurationMs", matchedDurationMs)
        put("providerTrackId", providerTrackId)
        put("isrc", isrc)
        put("translationLyrics", translationLyrics)
        put("romanLyrics", romanLyrics)
        put("hasTranslation", hasTranslation)
        put("hasRomanization", hasRomanization)
        put("cachedAt", cachedAt)
        put("updatedAt", updatedAt)
        if (lyricifyImported) put("lyricifyImported", true)
        if (lyricifyAliases.isNotEmpty()) put("lyricifyAliases", JSONObject(lyricifyAliases))
        if (lyricifyCandidates.isNotEmpty()) put("lyricifyCandidates", JSONArray(lyricifyCandidates.map { it.toJson() }))
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
            instrumental = optBoolean("instrumental", false),
            instrumentalUpdatedAt = optNullableLong("instrumentalUpdatedAt"),
            queryTitle = optNullableString("queryTitle"),
            queryArtist = optNullableString("queryArtist"),
            api = optNullableString("api"),
            providerId = optNullableString("providerId"),
            lyrics = optNullableString("lyrics"),
            hasSyllable = optBoolean("hasSyllable", false),
            matchedTitle = optNullableString("matchedTitle"),
            matchedArtist = optNullableString("matchedArtist"),
            matchedAlbum = optNullableString("matchedAlbum"),
            matchedDurationMs = optNullableLong("matchedDurationMs"),
            providerTrackId = optNullableString("providerTrackId"),
            isrc = optNullableString("isrc"),
            translationLyrics = translationLyrics,
            romanLyrics = romanLyrics,
            hasTranslation = optBoolean("hasTranslation", !translationLyrics.isNullOrBlank()),
            hasRomanization = optBoolean("hasRomanization", !romanLyrics.isNullOrBlank()),
            parsedLines = parsedLines,
            cachedAt = optNullableLong("cachedAt"),
            updatedAt = optLong("updatedAt", System.currentTimeMillis()),
            lyricifyImported = optBoolean("lyricifyImported", false),
            lyricifyAliases = optJSONObject("lyricifyAliases")?.let { aliases ->
                aliases.keys().asSequence().associateWith { aliases.getString(it) }
            }.orEmpty(),
            lyricifyCandidates = optJSONArray("lyricifyCandidates")?.let { candidates ->
                (0 until candidates.length()).map { candidates.getJSONObject(it).toEntry() }
            }.orEmpty()
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

    private fun cleanLyricsText(content: String?): String {
        if (content.isNullOrBlank()) return ""
        val lineTimestampRegex = Regex("""\[\d{1,2}:\d{2}(?:\.\d{1,3})?]""")
        val qrcHeaderRegex = Regex("""\[\d+,\d+]""")
        val wordTokenRegex = Regex("""(?:<|\()\d+,\d+(?:,\d+)?(?:>|\))""")
        val tagRegex = Regex("""^\[[a-zA-Z]+:.*]$""")
        val lines = content
            .lineSequence()
            .map { it.trim() }
            .filter { !tagRegex.matches(it) }
            .map { rawLine ->
                rawLine
                    .replace(lineTimestampRegex, "")
                    .replace(qrcHeaderRegex, "")
                    .replace(wordTokenRegex, "")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .toList()
        return lines
            .filterIndexed { index, text ->
                index == 0 || text != lines[index - 1]
            }
            .joinToString("\n")
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

