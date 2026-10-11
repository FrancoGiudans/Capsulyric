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
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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
    internal const val MAX_DATABASE_BYTES = 64L * 1024 * 1024
    internal const val MAX_PAYLOAD_BYTES = 1024L * 1024
    internal const val MAX_TRACK_PAYLOAD_BYTES = 4L * 1024 * 1024
    internal const val MAX_TOTAL_PAYLOAD_BYTES = 32L * 1024 * 1024
    internal const val MAX_TEMPORARY_BYTES = 128L * 1024 * 1024
    internal const val MAX_ENTRY_BYTES = 8L * 1024 * 1024
    internal const val MAX_TRACKS = 5_000
    private const val MAX_ASSETS = 20_000
    private const val MAX_PARSE_UNITS = 50_000

    internal class LimitExceeded(message: String) : IllegalArgumentException(message)
    data class Report(val imported: Int, val skipped: Int, val failed: Int)

    internal data class Asset(val id: Long, val verified: Boolean, val result: OnlineLyricFetcher.LyricResult, val providerIds: Map<String, String>)
    suspend fun importDatabase(context: Context, uri: Uri, mode: LyricifyImportMode): Report {
        check(LabFeatureManager.isLyricifyImportEnabled(context)) { "Import is disabled" }
        val jobContext = currentCoroutineContext()
        val checkActive = {
            jobContext.ensureActive()
            check(LabFeatureManager.isLyricifyImportEnabled(context)) { "Import is disabled" }
        }
        val copy = File.createTempFile("lyricify-import-", ".db", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                copy.outputStream().use { output -> copyDatabase(input, output) {
                    checkActive()
                    check(copy.parentFile!!.usableSpace >= 64 * 1024L) { "Not enough free space to copy the database" }
                } }
            } ?: error("Cannot read the selected file")
            return SQLiteDatabase.openDatabase(
                copy.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                validateDatabase(db)
                var failed = 0
                val tracks = readTracks(db, checkActive) { failed++ }
                val (imported, skipped) = OnlineLyricCacheStore(context).importLyricifyTracks(
                    tracks, mode, copy.length(), checkActive
                )
                Report(imported, skipped, failed)
            }
        } finally {
            copy.delete()
        }
    }

    internal fun checkSize(bytes: Long, limit: Long, label: String) {
        if (bytes < 0 || bytes > limit) throw LimitExceeded("$label exceeds the import limit ($limit bytes)")
    }

    internal fun copyDatabase(input: InputStream, output: OutputStream, checkActive: () -> Unit = {}) {
        val buffer = ByteArray(8 * 1024)
        var copied = 0L
        while (true) {
            checkActive()
            val count = input.read(buffer)
            if (count < 0) break
            copied += count
            checkSize(copied, MAX_DATABASE_BYTES, "Database")
            output.write(buffer, 0, count)
        }
    }

    internal fun importedEntryId(identity: LyricifyTrackIdentity): String {
        fun normalized(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        val parts = listOf("lyricify-import", normalized(identity.title), normalized(identity.artist),
            normalized(identity.album), identity.durationMs.toString()) +
            identity.providerIds.entries.sortedBy { it.key }.flatMap { listOf(it.key.lowercase(Locale.ROOT), it.value.lowercase(Locale.ROOT)) }
        val payload = parts.joinToString("") { "${it.length}:$it" }
        return MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    internal fun mergeAliases(existing: Map<String, String>, incoming: Map<String, String>): Map<String, String> {
        for ((namespace, id) in incoming) {
            require(existing[namespace]?.equals(id, ignoreCase = true) != false) { "Conflicting $namespace song IDs" }
        }
        return incoming + existing
    }

    private fun validateDatabase(db: SQLiteDatabase) {
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
        fun scalar(sql: String) = db.rawQuery(sql, null).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }
        require(scalar("SELECT COUNT(*) FROM catalog_tracks") <= MAX_TRACKS) { "Too many songs to import" }
        require(scalar("SELECT COUNT(*) FROM lyrics_assets") <= MAX_ASSETS) { "Too many lyric assets to import" }
        require(scalar("SELECT COUNT(*) FROM track_aliases") <= MAX_TRACKS * 10L) { "Too many song aliases to import" }
        checkSize(scalar("SELECT COALESCE(MAX(length(CAST(payload_json AS BLOB))), 0) FROM lyrics_assets"), MAX_PAYLOAD_BYTES, "Lyric payload")
        checkSize(scalar("SELECT COALESCE(SUM(length(CAST(payload_json AS BLOB))), 0) FROM lyrics_assets"), MAX_TOTAL_PAYLOAD_BYTES, "Total lyric payloads")
        checkSize(scalar("SELECT COALESCE(MAX(bytes), 0) FROM (SELECT SUM(length(CAST(payload_json AS BLOB))) AS bytes FROM lyrics_assets GROUP BY track_id)"), MAX_TRACK_PAYLOAD_BYTES, "Song payloads")
    }

    private fun readTracks(db: SQLiteDatabase, checkActive: () -> Unit, onFailed: () -> Unit) = sequence {
        db.rawQuery("SELECT id, title, artist, album, duration_ms, selected_asset_id FROM catalog_tracks ORDER BY id", null).use { tracks ->
            while (tracks.moveToNext()) {
                checkActive()
                val trackId = tracks.getLong(0).toString()
                val ids = mutableMapOf<String, String>()
                db.rawQuery("SELECT platform, external_id FROM track_aliases WHERE track_id = ?", arrayOf(trackId)).use { aliases ->
                    while (aliases.moveToNext()) {
                        val provider = providerFor(aliases.getString(0)) ?: continue
                        ids.putAll(identityIds(provider, aliases.getString(1)))
                    }
                }
                val candidates = mutableListOf<Asset>()
                var parsedUnits = 0L
                db.rawQuery("SELECT id, provider, local_name, payload_json, is_verified FROM lyrics_assets WHERE track_id = ? ORDER BY id", arrayOf(trackId)).use { assets ->
                    while (assets.moveToNext()) {
                        checkActive()
                        val payloadText = assets.getString(3) ?: continue
                        validatePayload(payloadText)
                        val asset = try {
                            val payload = JSONObject(payloadText)
                            val result = convertPayload(payload, assets.getString(1), assets.getString(2))
                            val lines = result.parsedLines.orEmpty()
                            parsedUnits += lines.size + lines.sumOf { it.syllables.orEmpty().size.toLong() }
                            if (parsedUnits > MAX_PARSE_UNITS) throw LimitExceeded("Too many parsed song elements")
                            Asset(assets.getLong(0), assets.getInt(4) != 0, result,
                                listOf("Identifier", "SecondaryIdentifier", "TertiaryIdentifier").flatMap { key ->
                                    identityIds(result.provider, payload.optJSONObject("providerTrack")?.optString(key)).entries.map { it.toPair() }
                                }.toMap())
                        } catch (error: Exception) {
                            if (error is CancellationException || error is LimitExceeded) throw error
                            continue
                        }
                        candidates.add(asset)
                    }
                }
                val selectedId = if (tracks.isNull(5)) null else tracks.getLong(5)
                val selected = selectAsset(candidates, selectedId)
                if (selected == null) { onFailed(); continue }
                candidates.forEach { ids.putAll(it.providerIds) }
                ids.putAll(selected.providerIds)
                yield(LyricifyTrackIdentity(tracks.getString(1), tracks.getString(2), tracks.getString(3).orEmpty(), tracks.getLong(4), ids) to
                    LyricifyImportSelection(candidates.map { it.result }, selected.result))
            }
        }
    }

    internal fun selectAsset(candidates: List<Asset>, selectedId: Long?): Asset? =
        candidates.firstOrNull { it.id == selectedId }
            ?: candidates.firstOrNull { it.verified }
            ?: candidates.firstOrNull { it.result.hasSyllable }
            ?: candidates.firstOrNull()

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
        if (sharedProviders.any { !first.providerIds[it].equals(second.providerIds[it], ignoreCase = true) }) return false
        if (sharedProviders.isNotEmpty()) return true
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
        validatePayload(payload)
        return convertPayload(JSONObject(payload), providerName, localName)
    }

    internal fun validatePayload(text: String) {
        checkSize(text.toByteArray(Charsets.UTF_8).size.toLong(), MAX_PAYLOAD_BYTES, "Lyric payload")
        // Bound nesting before JSONObject allocates a recursive object tree.
        var quoted = false
        var escaped = false
        var depth = 0
        for (char in text) {
            if (quoted) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> { depth++; if (depth > 64) throw LimitExceeded("Lyric JSON is nested too deeply") }
                '}', ']' -> depth--
            }
        }
    }

    private fun convertPayload(root: JSONObject, providerName: String?, localName: String?): OnlineLyricFetcher.LyricResult {
        require(root.optInt("version") == 2) { "Unsupported Lyricify payload version" }
        val provider = providerFor(providerName) ?: if (providerName.isNullOrBlank()) OnlineLyricProvider.LrcApi
            else error("Unsupported lyric provider: $providerName")
        val lyrics = root.getJSONObject("lyrics")
        val originalLines = parseDocument(lyrics.getString("format"), lyrics.getString("text"))
        require(hasDisplayableLyrics(originalLines)) { "No displayable lyrics" }
        val offset = root.optLong("offsetMs", 0L)
        val lines = shiftLines(originalLines, offset)
        val translations = root.optJSONObject("translations")
        val language = translations?.keys()?.asSequence()?.toList().orEmpty().let { keys ->
            keys.firstOrNull { it.startsWith("zh", ignoreCase = true) } ?: keys.firstOrNull()
        }
        val translation = language?.let { translations?.optJSONObject(it) }?.let { document ->
            val translated = if (document.optString("format").equals("Musixmatch", true)) {
                validatePayload(document.getString("text"))
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

    internal fun hasDisplayableLyrics(lines: List<OnlineLyricFetcher.LyricLine>): Boolean = lines.any { line ->
        line.text.isNotBlank() || line.syllables.orEmpty().any { it.text.isNotBlank() }
    }

    internal fun parseDocument(format: String, text: String): List<OnlineLyricFetcher.LyricLine> {
        validatePayload(text)
        if (text.count { it == '[' || it == '<' || it == '(' || it == '\n' } > MAX_PARSE_UNITS) {
            throw LimitExceeded("Too many lyric timing elements")
        }
        return when (format.lowercase(Locale.ROOT)) {
            "lrc" -> OnlineLyricParser.parseLrcLyrics(normalizeLrc(text))
            "qrc" -> OnlineLyricParser.parseQrcLyrics(text)
            "krc" -> OnlineLyricParser.parseKrcLyrics(text)
            "yrc" -> OnlineLyricParser.parseYrcLyrics(normalizeYrc(text))
            "musixmatch" -> {
                val calls = JSONObject(text).getJSONObject("message").getJSONObject("body").getJSONObject("macro_calls")
                fun body(key: String) = calls.optJSONObject(key)?.optJSONObject("message")?.optJSONObject("body")
                val richsync = body("track.richsync.get")?.optJSONObject("richsync")?.optString("richsync_body").orEmpty()
                validatePayload(richsync)
                OnlineLyricParser.parseMusixmatchRichsync(richsync).ifEmpty {
                    val subtitle = body("track.subtitles.get")?.optJSONArray("subtitle_list")?.optJSONObject(0)
                        ?.optJSONObject("subtitle")?.optString("subtitle_body").orEmpty()
                    OnlineLyricParser.parseLrcLyrics(normalizeLrc(subtitle))
                }
            }
            else -> error("Unsupported lyric format: $format")
        }.also { lines ->
            if (lines.size + lines.sumOf { it.syllables.orEmpty().size.toLong() } > MAX_PARSE_UNITS) {
                throw LimitExceeded("Too many parsed lyric elements")
            }
        }
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
