package com.example.islandlyrics.lyrics.importer

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.lyrics.cache.OnlineLyricCacheStore
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.parser.OnlineLyricParser
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.abs

enum class LyricifyImportMode { OVERWRITE, MERGE, SKIP }

data class LyricifyTrackIdentity(
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val providerIds: Map<String, String> = emptyMap()
)

data class LyricifyImportSelection(
    val candidates: List<OnlineLyricFetcher.LyricResult>,
    val selected: OnlineLyricFetcher.LyricResult
)

/** Compatibility is limited to the inspected Lyricify catalog and payload v2. */
object LyricifyDatabaseImporter {
    data class Report(val imported: Int, val skipped: Int, val failed: Int)

    private data class Asset(val id: Long, val verified: Boolean, val result: OnlineLyricFetcher.LyricResult, val providerIds: Map<String, String>)
    private data class Track(val identity: LyricifyTrackIdentity, val selection: LyricifyImportSelection)

    fun importDatabase(context: Context, uri: Uri, mode: LyricifyImportMode): Report {
        check(LabFeatureManager.isLyricifyImportEnabled(context)) { "Import is disabled" }
        val copy = File.createTempFile("lyricify-import-", ".db", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                copy.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Cannot read the selected file")
            val (tracks, failed) = SQLiteDatabase.openDatabase(
                copy.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            ).use { readTracks(it) }
            check(LabFeatureManager.isLyricifyImportEnabled(context)) { "Import is disabled" }
            val cache = OnlineLyricCacheStore(context)
            val (imported, skipped) = cache.importLyricifyTracks(tracks.map { it.identity to it.selection }, mode)
            return Report(imported, skipped, failed)
        } finally {
            copy.delete()
        }
    }

    private fun readTracks(db: SQLiteDatabase): Pair<List<Track>, Int> {
        val requiredColumns = mapOf(
            "catalog_tracks" to setOf("id", "title", "artist", "album", "duration_ms", "selected_asset_id"),
            "lyrics_assets" to setOf("id", "track_id", "provider", "local_name", "payload_json", "is_verified"),
            "track_aliases" to setOf("track_id", "platform", "external_id")
        )
        for ((table, required) in requiredColumns) {
            val columns = db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
            require(columns.containsAll(required)) { "Unsupported Lyricify database structure" }
        }
        val aliases = mutableMapOf<Long, MutableMap<String, String>>()
        db.rawQuery("SELECT track_id, platform, external_id FROM track_aliases", null).use { cursor ->
            while (cursor.moveToNext()) {
                val provider = providerFor(cursor.getString(1)) ?: continue
                val id = provider.normalizeTrackId(cursor.getString(2)) ?: continue
                aliases.getOrPut(cursor.getLong(0)) { mutableMapOf() }.putAll(identityIds(provider, id))
            }
        }
        val assets = mutableMapOf<Long, MutableList<Asset>>()
        db.rawQuery("SELECT id, track_id, provider, local_name, payload_json, is_verified FROM lyrics_assets ORDER BY id", null).use { cursor ->
            while (cursor.moveToNext()) {
                val payload = runCatching { JSONObject(cursor.getString(4)) }.getOrNull() ?: continue
                val result = runCatching {
                    convertPayload(payload, cursor.getString(2), cursor.getString(3))
                }.getOrNull() ?: continue
                assets.getOrPut(cursor.getLong(1)) { mutableListOf() }.add(
                    Asset(cursor.getLong(0), cursor.getInt(5) != 0, result,
                        listOf("Identifier", "SecondaryIdentifier", "TertiaryIdentifier").flatMap { key ->
                            identityIds(result.provider, payload.optJSONObject("providerTrack")?.optString(key)).entries.map { it.toPair() }
                        }.toMap())
                )
            }
        }
        var failed = 0
        val tracks = buildList {
            db.rawQuery("SELECT id, title, artist, album, duration_ms, selected_asset_id FROM catalog_tracks ORDER BY id", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val candidates = assets[cursor.getLong(0)].orEmpty()
                    if (candidates.isEmpty()) { failed++; continue }
                    val selectedId = if (cursor.isNull(5)) null else cursor.getLong(5)
                    val selected = candidates.firstOrNull { it.id == selectedId }
                        ?: candidates.firstOrNull { it.verified }
                        ?: candidates.firstOrNull { it.result.hasSyllable }
                        ?: candidates.first()
                    val ids = aliases[cursor.getLong(0)].orEmpty() + candidates.flatMap { it.providerIds.entries.map { entry -> entry.toPair() } }.toMap()
                    add(Track(
                        LyricifyTrackIdentity(cursor.getString(1), cursor.getString(2), cursor.getString(3).orEmpty(), cursor.getLong(4), ids),
                        LyricifyImportSelection(candidates.map { it.result }, selected.result)
                    ))
                }
            }
        }
        return tracks to failed
    }

    internal fun providerFor(name: String?): OnlineLyricProvider? = when (name?.lowercase(Locale.ROOT)?.replace("_", "")?.replace("-", "")) {
        "qqmusic" -> OnlineLyricProvider.QQMusic
        "kugou", "kugoumusic" -> OnlineLyricProvider.Kugou
        "netease", "neteasemusic" -> OnlineLyricProvider.Netease
        "soda", "sodamusic" -> OnlineLyricProvider.SodaMusic
        "lrclib" -> OnlineLyricProvider.Lrclib
        "applemusic" -> OnlineLyricProvider.AppleMusic
        "musixmatch" -> OnlineLyricProvider.Musixmatch
        "lrcapi" -> OnlineLyricProvider.LrcApi
        else -> null
    }

    internal fun sameTrack(first: LyricifyTrackIdentity, second: LyricifyTrackIdentity): Boolean {
        val sharedProviders = first.providerIds.keys.intersect(second.providerIds.keys)
        if (sharedProviders.any { first.providerIds[it].equals(second.providerIds[it], ignoreCase = true) }) return true
        if (sharedProviders.isNotEmpty()) return false
        fun normalized(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        if (first.title.isBlank() || first.artist.isBlank() ||
            normalized(first.title) != normalized(second.title) || normalized(first.artist) != normalized(second.artist)) return false
        val hasAlbums = first.album.isNotBlank() && second.album.isNotBlank()
        if (hasAlbums && normalized(first.album) != normalized(second.album)) return false
        val hasDurations = first.durationMs > 0 && second.durationMs > 0
        if (hasDurations && abs(first.durationMs - second.durationMs) > 5_000L) return false
        return hasAlbums || hasDurations
    }

    internal fun identityIds(provider: OnlineLyricProvider, id: String?): Map<String, String> {
        val normalized = id?.let(provider::normalizeTrackId) ?: return emptyMap()
        val namespace = if (provider == OnlineLyricProvider.QQMusic && normalized.any { it.isLetter() }) "${provider.id}:mid" else provider.id
        return mapOf(namespace to normalized)
    }

    internal fun resolveSelection(
        existing: LyricifyImportSelection?, incoming: LyricifyImportSelection, mode: LyricifyImportMode
    ): LyricifyImportSelection? {
        if (existing != null && mode == LyricifyImportMode.SKIP) return null
        return LyricifyImportSelection(
            (existing?.candidates.orEmpty() + incoming.candidates).distinctBy(::candidateKey),
            if (mode == LyricifyImportMode.MERGE && existing != null) existing.selected else incoming.selected
        )
    }

    internal fun candidateKey(result: OnlineLyricFetcher.LyricResult): List<Any?> = listOf(
        result.provider, result.providerTrackId, result.lyrics, result.translationLyrics, result.romanLyrics, result.parsedLines
    )

    internal fun convertPayload(payload: String, providerName: String?, localName: String? = null): OnlineLyricFetcher.LyricResult {
        return convertPayload(JSONObject(payload), providerName, localName)
    }

    private fun convertPayload(root: JSONObject, providerName: String?, localName: String?): OnlineLyricFetcher.LyricResult {
        require(root.optInt("version") == 2) { "Unsupported Lyricify payload version" }
        val provider = providerFor(providerName) ?: if (providerName.isNullOrBlank()) OnlineLyricProvider.LrcApi
            else error("Unsupported lyric provider: $providerName")
        val lyrics = root.getJSONObject("lyrics")
        val originalLines = parseDocument(lyrics.getString("format"), lyrics.getString("text"))
        require(originalLines.isNotEmpty()) { "No timed lyrics" }
        val offset = root.optLong("offsetMs", 0L)
        val lines = shiftLines(originalLines, offset)
        val translations = root.optJSONObject("translations")
        val language = translations?.keys()?.asSequence()?.toList().orEmpty().let { keys ->
            keys.firstOrNull { it.startsWith("zh", ignoreCase = true) } ?: keys.firstOrNull()
        }
        val translation = language?.let { translations?.optJSONObject(it) }?.let { document ->
            val translated = if (document.optString("format").equals("Musixmatch", true)) {
                parseMusixmatchTranslation(document.getString("text"), originalLines)
            } else parseDocument(document.getString("format"), document.getString("text"))
            toLrc(shiftLines(translated, offset)).takeIf { it.isNotBlank() }
        }
        val track = root.optJSONObject("providerTrack")
        val trackId = listOf("Identifier", "SecondaryIdentifier", "TertiaryIdentifier").firstNotNullOfOrNull { key ->
            track?.optString(key)?.let(provider::normalizeTrackId)
        }
        return OnlineLyricFetcher.LyricResult(
            api = "Lyricify · ${providerName ?: localName ?: "Local"}",
            lyrics = if (lyrics.getString("format").equals("Musixmatch", true)) toLrc(lines) else lyrics.getString("text"),
            parsedLines = lines,
            hasSyllable = lines.any { !it.syllables.isNullOrEmpty() },
            provider = provider,
            matchedTitle = track?.optString("Title")?.takeIf { it.isNotBlank() },
            matchedArtist = track?.optJSONArray("Artists")?.let { array ->
                (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }.joinToString(" / ")
            },
            matchedAlbum = track?.optString("Album")?.takeIf { it.isNotBlank() },
            matchedDurationMs = track?.optLong("DurationMs")?.takeIf { it > 0 },
            providerTrackId = trackId,
            translationLyrics = translation
        )
    }

    internal fun parseDocument(format: String, text: String): List<OnlineLyricFetcher.LyricLine> = when (format.lowercase(Locale.ROOT)) {
        "lrc" -> OnlineLyricParser.parseLrcLyrics(normalizeLrc(text))
        "qrc" -> OnlineLyricParser.parseQrcLyrics(text)
        "krc" -> OnlineLyricParser.parseKrcLyrics(text)
        "yrc" -> OnlineLyricParser.parseYrcLyrics(normalizeYrc(text))
        "musixmatch" -> {
            val calls = JSONObject(text).getJSONObject("message").getJSONObject("body").getJSONObject("macro_calls")
            fun body(key: String) = calls.optJSONObject(key)?.optJSONObject("message")?.optJSONObject("body")
            val richsync = body("track.richsync.get")?.optJSONObject("richsync")?.optString("richsync_body").orEmpty()
            OnlineLyricParser.parseMusixmatchRichsync(richsync).ifEmpty {
                val subtitle = body("track.subtitles.get")?.optJSONArray("subtitle_list")?.optJSONObject(0)
                    ?.optJSONObject("subtitle")?.optString("subtitle_body").orEmpty()
                OnlineLyricParser.parseLrcLyrics(normalizeLrc(subtitle))
            }
        }
        else -> error("Unsupported lyric format: $format")
    }

    private fun parseMusixmatchTranslation(text: String, lines: List<OnlineLyricFetcher.LyricLine>): List<OnlineLyricFetcher.LyricLine> {
        val translations = JSONObject(text).getJSONObject("message").getJSONObject("body").optJSONArray("translations_list")
            ?: return emptyList()
        val byText = buildMap {
            for (i in 0 until translations.length()) {
                val item = translations.optJSONObject(i)?.optJSONObject("translation") ?: continue
                val matched = item.optString("matched_line").ifBlank { item.optString("subtitle_matched_line") }
                val translated = item.optString("description")
                if (matched.isNotBlank() && translated.isNotBlank()) put(matched.trim(), translated)
            }
        }
        return lines.mapNotNull { line -> byText[line.text.trim()]?.let { line.copy(text = it, syllables = null) } }
    }

    internal fun shiftLines(lines: List<OnlineLyricFetcher.LyricLine>, offsetMs: Long): List<OnlineLyricFetcher.LyricLine> =
        lines.map { line -> line.copy(
            startTime = (line.startTime + offsetMs).coerceAtLeast(0L),
            endTime = (line.endTime + offsetMs).coerceAtLeast(0L),
            syllables = line.syllables?.map { it.copy(startTime = (it.startTime + offsetMs).coerceAtLeast(0L), endTime = (it.endTime + offsetMs).coerceAtLeast(0L)) }
        ) }

    private fun normalizeLrc(text: String): String = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]").replace(text) { match ->
        "[%02d:%02d.%03d]".format(Locale.ROOT, match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].padEnd(3, '0').toInt())
    }

    // Lyricify stores native YRC absolute (start,duration,flag) tokens; the existing parser takes relative <start,duration> tokens.
    private fun normalizeYrc(text: String): String = text.lineSequence().joinToString("\n") { line ->
        val start = Regex("^\\[(\\d+),\\d+]").find(line.trim())?.groupValues?.get(1)?.toLongOrNull()
        if (start == null) line else Regex("\\((\\d+),(\\d+),\\d+\\)").replace(line) { word ->
            "<${(word.groupValues[1].toLong() - start).coerceAtLeast(0L)},${word.groupValues[2]}>"
        }
    }

    private fun toLrc(lines: List<OnlineLyricFetcher.LyricLine>): String = lines.joinToString("\n") { line ->
        "[%02d:%02d.%03d]%s".format(Locale.ROOT, line.startTime / 60_000, line.startTime / 1_000 % 60, line.startTime % 1_000, line.text)
    }
}
