package com.example.islandlyrics.lyrics.cache

import android.content.Context
import com.example.islandlyrics.lyrics.online.provider.AppleMusicCatalogAlias
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal object TrackIdentityCacheKey {
    fun build(
        packageName: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        mediaId: String,
        mediaUri: String
    ): String {
        val stableReference = when {
            mediaId.isNotBlank() -> "media-id:${normalize(mediaId)}"
            mediaUri.isNotBlank() -> "media-uri:${normalize(mediaUri)}"
            else -> "text:${normalize(title)}|${normalize(artist)}|${normalize(album)}|${durationBucket(durationMs)}"
        }
        return sha256("${normalize(packageName)}|$stableReference")
    }

    private fun durationBucket(durationMs: Long): Long =
        if (durationMs > 0L) durationMs / 5_000L else 0L

    private fun normalize(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

/**
 * Small device-local cache for evidence-backed track aliases.
 *
 * This cache is deliberately separate from lyric content. An entry is keyed by
 * the observed player/package identity and stores only catalog metadata needed
 * to repeat a safe cross-language lookup. It never stores Apple credentials or
 * request URLs.
 */
class TrackIdentityCacheStore(context: Context) {

    private data class Entry(
        val packageName: String,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val mediaId: String,
        val mediaUri: String,
        val aliases: List<AppleMusicCatalogAlias>,
        val updatedAt: Long
    )

    private val file = File(context.applicationContext.filesDir, "cache_store/identity_index.json")
    private val lock = Any()

    fun getAliases(
        packageName: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        mediaId: String,
        mediaUri: String,
        now: Long = System.currentTimeMillis()
    ): List<AppleMusicCatalogAlias> = synchronized(lock) {
        val entry = readEntries()[buildObservedKey(
            packageName,
            title,
            artist,
            album,
            durationMs,
            mediaId,
            mediaUri
        )]
        entry?.takeIf { now - it.updatedAt in 0..IDENTITY_TTL_MS }?.aliases.orEmpty()
    }

    fun saveAliases(
        packageName: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        mediaId: String,
        mediaUri: String,
        aliases: List<AppleMusicCatalogAlias>,
        now: Long = System.currentTimeMillis()
    ) {
        if (packageName.isBlank() || aliases.isEmpty()) return
        synchronized(lock) {
            val entries = readEntries().toMutableMap()
            val key = buildObservedKey(packageName, title, artist, album, durationMs, mediaId, mediaUri)
            entries[key] = Entry(
                packageName = packageName,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                mediaId = mediaId,
                mediaUri = mediaUri,
                aliases = aliases
                    .filter { it.title.isNotBlank() && it.artist.isNotBlank() }
                    .distinctBy { listOf(it.title, it.artist, it.album.orEmpty(), it.isrc.orEmpty()).joinToString("|") }
                    .take(MAX_ALIASES_PER_ENTRY),
                updatedAt = now
            )
            val trimmed = entries.values
                .sortedByDescending { it.updatedAt }
                .take(MAX_ENTRIES)
                .associateBy { buildObservedKey(it.packageName, it.title, it.artist, it.album, it.durationMs, it.mediaId, it.mediaUri) }
            writeEntries(trimmed)
        }
    }

    internal fun buildObservedKey(
        packageName: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        mediaId: String,
        mediaUri: String
    ): String = TrackIdentityCacheKey.build(
            packageName,
            title,
            artist,
            album,
            durationMs,
            mediaId,
            mediaUri
        )

    private fun readEntries(): Map<String, Entry> {
        if (!file.exists()) return emptyMap()
        return runCatching {
            val entriesJson = JSONObject(file.readText(Charsets.UTF_8)).optJSONObject("entries") ?: return emptyMap()
            buildMap {
                for (key in entriesJson.keys()) {
                    val obj = entriesJson.optJSONObject(key) ?: continue
                    val aliasesJson = obj.optJSONArray("aliases") ?: JSONArray()
                    val aliases = buildList {
                        for (index in 0 until aliasesJson.length()) {
                            val alias = aliasesJson.optJSONObject(index) ?: continue
                            add(
                                AppleMusicCatalogAlias(
                                    title = alias.optString("title"),
                                    artist = alias.optString("artist"),
                                    album = alias.optNullableString("album"),
                                    durationMs = alias.optNullableLong("durationMs"),
                                    providerTrackId = alias.optString("providerTrackId"),
                                    isrc = alias.optNullableString("isrc"),
                                    storefront = alias.optString("storefront")
                                )
                            )
                        }
                    }
                    put(
                        key,
                        Entry(
                            packageName = obj.optString("packageName"),
                            title = obj.optString("title"),
                            artist = obj.optString("artist"),
                            album = obj.optString("album"),
                            durationMs = obj.optLong("durationMs"),
                            mediaId = obj.optString("mediaId"),
                            mediaUri = obj.optString("mediaUri"),
                            aliases = aliases,
                            updatedAt = obj.optLong("updatedAt", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun writeEntries(entries: Map<String, Entry>) {
        file.parentFile?.mkdirs()
        val entriesJson = JSONObject()
        entries.forEach { (key, entry) ->
            entriesJson.put(key, JSONObject().apply {
                put("packageName", entry.packageName)
                put("title", entry.title)
                put("artist", entry.artist)
                put("album", entry.album)
                put("durationMs", entry.durationMs)
                put("mediaId", entry.mediaId)
                put("mediaUri", entry.mediaUri)
                put("updatedAt", entry.updatedAt)
                put("aliases", JSONArray(entry.aliases.map { alias ->
                    JSONObject().apply {
                        put("title", alias.title)
                        put("artist", alias.artist)
                        put("album", alias.album)
                        put("durationMs", alias.durationMs)
                        put("providerTrackId", alias.providerTrackId)
                        put("isrc", alias.isrc)
                        put("storefront", alias.storefront)
                    }
                }))
            })
        }
        file.writeText(
            JSONObject().apply {
                put("version", 1)
                put("entries", entriesJson)
            }.toString(),
            Charsets.UTF_8
        )
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)

    private companion object {
        private const val IDENTITY_TTL_MS = 30L * 24 * 60 * 60 * 1000
        private const val MAX_ENTRIES = 256
        private const val MAX_ALIASES_PER_ENTRY = 12
    }
}
