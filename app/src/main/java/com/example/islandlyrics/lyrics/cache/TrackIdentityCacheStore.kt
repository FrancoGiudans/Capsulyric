package com.example.islandlyrics.lyrics.cache

import android.content.Context
import com.example.islandlyrics.lyrics.online.provider.AppleMusicCatalogAlias
import com.example.islandlyrics.lyrics.online.provider.AppleMusicIsrc
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
        val canonicalIsrc: String,
        val aliases: List<AppleMusicCatalogAlias>,
        val updatedAt: Long
    )

    private data class IdentityEntry(
        val canonicalIsrc: String,
        val aliases: List<AppleMusicCatalogAlias>,
        val updatedAt: Long
    )

    private data class Store(
        val entries: Map<String, Entry>,
        val identities: Map<String, IdentityEntry>
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
        val store = readStore()
        val entry = store.entries[buildObservedKey(
            packageName,
            title,
            artist,
            album,
            durationMs,
            mediaId,
            mediaUri
        )]
        val aliases = entry?.canonicalIsrc
            ?.let { store.identities[identityKey(it)]?.aliases }
            ?: entry?.aliases
        if (entry == null || now - entry.updatedAt !in 0..IDENTITY_TTL_MS) {
            emptyList()
        } else {
            aliases.orEmpty()
        }
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
        val normalizedIsrcs = aliases.mapNotNull { AppleMusicIsrc.normalize(it.isrc) }.toSet()
        val canonicalIsrc = normalizedIsrcs.singleOrNull() ?: return
        val verifiedAliases = aliases
            .filter { AppleMusicIsrc.normalize(it.isrc) == canonicalIsrc }
            .filter { it.title.isNotBlank() && it.artist.isNotBlank() }
            .distinctBy {
                listOf(
                    it.title.trim().lowercase(),
                    it.artist.trim().lowercase(),
                    AppleMusicIsrc.normalize(it.isrc).orEmpty()
                ).joinToString("|")
            }
            .take(MAX_ALIASES_PER_ENTRY)
        if (verifiedAliases.isEmpty()) return
        synchronized(lock) {
            val store = readStore()
            val entries = store.entries.toMutableMap()
            val key = buildObservedKey(packageName, title, artist, album, durationMs, mediaId, mediaUri)
            entries[key] = Entry(
                packageName = packageName,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                mediaId = mediaId,
                mediaUri = mediaUri,
                canonicalIsrc = canonicalIsrc,
                aliases = verifiedAliases,
                updatedAt = now
            )
            val trimmed = entries.values
                .sortedByDescending { it.updatedAt }
                .take(MAX_ENTRIES)
                .associateBy { buildObservedKey(it.packageName, it.title, it.artist, it.album, it.durationMs, it.mediaId, it.mediaUri) }
            val identityKey = identityKey(canonicalIsrc)
            val mergedAliases = (store.identities[identityKey]?.aliases.orEmpty() + verifiedAliases)
                .filter { AppleMusicIsrc.normalize(it.isrc) == canonicalIsrc }
                .distinctBy {
                    listOf(
                        it.title.trim().lowercase(),
                        it.artist.trim().lowercase(),
                        AppleMusicIsrc.normalize(it.isrc).orEmpty()
                    ).joinToString("|")
                }
                .take(MAX_ALIASES_PER_ENTRY)
            val identities = store.identities.toMutableMap()
            identities[identityKey] = IdentityEntry(canonicalIsrc, mergedAliases, now)
            writeStore(Store(trimmed, identities))
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

    private fun readStore(): Store {
        if (!file.exists()) return Store(emptyMap(), emptyMap())
        return runCatching {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            if (root.optInt("version", -1) != CACHE_SCHEMA_VERSION) {
                // v1 did not carry an anchor ISRC/provenance boundary; discard
                // automatic entries rather than reusing potentially polluted aliases.
                return@runCatching Store(emptyMap(), emptyMap())
            }
            val entriesJson = root.optJSONObject("entries") ?: JSONObject()
            val identitiesJson = root.optJSONObject("identities") ?: JSONObject()
            val identities = buildMap {
                for (key in identitiesJson.keys()) {
                    val obj = identitiesJson.optJSONObject(key) ?: continue
                    val canonical = AppleMusicIsrc.normalize(obj.optString("canonicalIsrc")) ?: continue
                    val aliases = parseAliases(obj.optJSONArray("aliases"))
                        .filter { AppleMusicIsrc.normalize(it.isrc) == canonical }
                    if (aliases.isNotEmpty()) {
                        put(key, IdentityEntry(canonical, aliases, obj.optLong("updatedAt", 0L)))
                    }
                }
            }
            val entries = buildMap {
                for (key in entriesJson.keys()) {
                    val obj = entriesJson.optJSONObject(key) ?: continue
                    val canonical = AppleMusicIsrc.normalize(obj.optString("canonicalIsrc")) ?: continue
                    val aliases = parseAliases(obj.optJSONArray("aliases"))
                        .filter { AppleMusicIsrc.normalize(it.isrc) == canonical }
                    if (aliases.isEmpty()) continue
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
                            canonicalIsrc = canonical,
                            aliases = aliases,
                            updatedAt = obj.optLong("updatedAt", 0L)
                        )
                    )
                }
            }
            Store(entries, identities)
        }.getOrDefault(Store(emptyMap(), emptyMap()))
    }

    private fun writeStore(store: Store) {
        file.parentFile?.mkdirs()
        val entriesJson = JSONObject()
        store.entries.forEach { (key, entry) ->
            entriesJson.put(key, JSONObject().apply {
                put("packageName", entry.packageName)
                put("title", entry.title)
                put("artist", entry.artist)
                put("album", entry.album)
                put("durationMs", entry.durationMs)
                put("mediaId", entry.mediaId)
                put("mediaUri", entry.mediaUri)
                put("canonicalIsrc", entry.canonicalIsrc)
                put("updatedAt", entry.updatedAt)
                put("aliases", aliasesToJson(entry.aliases))
            })
        }
        val identitiesJson = JSONObject()
        store.identities.forEach { (key, identity) ->
            identitiesJson.put(key, JSONObject().apply {
                put("canonicalIsrc", identity.canonicalIsrc)
                put("updatedAt", identity.updatedAt)
                put("aliases", aliasesToJson(identity.aliases))
            })
        }
        file.writeText(
            JSONObject().apply {
                put("version", CACHE_SCHEMA_VERSION)
                put("entries", entriesJson)
                put("identities", identitiesJson)
            }.toString(),
            Charsets.UTF_8
        )
    }

    private fun parseAliases(aliasesJson: JSONArray?): List<AppleMusicCatalogAlias> = buildList {
        val json = aliasesJson ?: return@buildList
        for (index in 0 until json.length()) {
            val alias = json.optJSONObject(index) ?: continue
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

    private fun aliasesToJson(aliases: List<AppleMusicCatalogAlias>): JSONArray =
        JSONArray(aliases.map { alias ->
            JSONObject().apply {
                put("title", alias.title)
                put("artist", alias.artist)
                put("album", alias.album)
                put("durationMs", alias.durationMs)
                put("providerTrackId", alias.providerTrackId)
                put("isrc", alias.isrc)
                put("storefront", alias.storefront)
            }
        })

    private fun identityKey(canonicalIsrc: String): String = "isrc:$canonicalIsrc"

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)

    private companion object {
        private const val CACHE_SCHEMA_VERSION = 2
        private const val IDENTITY_TTL_MS = 30L * 24 * 60 * 60 * 1000
        private const val MAX_ENTRIES = 256
        private const val MAX_ALIASES_PER_ENTRY = 12
    }
}
