package com.example.islandlyrics.lyrics.importer

import android.content.Context
import android.net.Uri
import com.example.islandlyrics.integration.lastfm.LastFmCredentials
import com.example.islandlyrics.integration.lastfm.LastFmSecureStore
import com.example.islandlyrics.lyrics.cache.OnlineLyricCacheStore
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Base64
import java.util.Locale
import java.util.zip.Inflater

/** Lyrimuse text-library v2 and Config v1. App preferences are deliberately never imported. */
internal object LyrimuseBackupImporter {
    internal const val MAX_INPUT_BYTES = 64 * 1024 * 1024
    internal const val MAX_DECODED_BYTES = 32 * 1024 * 1024

    data class Prepared(
        val tracks: List<Pair<LyricifyTrackIdentity, LyricifyImportSelection>>,
        val skipped: Int,
        val failed: Int,
        val hasConfig: Boolean,
        val credentials: LastFmCredentials?,
    )

    data class Report(
        val imported: Int,
        val skipped: Int,
        val failed: Int,
        val lastFmImported: Boolean,
        val lastFmFailed: Boolean,
    )

    fun prepare(context: Context, uris: List<Uri>, checkActive: () -> Unit): Prepared {
        require(uris.size in 1..2) { "Select one Lyrics file and/or one Config file" }
        return prepareBytes(uris.map { uri ->
            checkActive()
            context.contentResolver.openInputStream(uri)?.use { readBounded(it, checkActive) }
                ?: error("Cannot read the selected file")
        }, checkActive)
    }

    /** Byte-oriented boundary also used by JVM tests; no Android storage is touched here. */
    internal fun prepareBytes(inputs: List<ByteArray>, checkActive: () -> Unit = {}): Prepared {
        require(inputs.size in 1..2) { "Select one Lyrics file and/or one Config file" }
        var archive: JSONObject? = null
        var config: JSONObject? = null
        for (input in inputs) {
            checkActive()
            val root = JSONObject(decode(input, checkActive))
            when {
                root.has("files") -> {
                    require(archive == null && root.optInt("v") == 2 && root.optJSONObject("files") != null) {
                        "Unsupported or duplicate Lyrimuse Lyrics file"
                    }
                    archive = root
                }
                root.optJSONObject("config") != null && root.optInt("version") == 1 -> {
                    require(config == null) { "Select only one Config file" }
                    config = root.getJSONObject("config")
                }
                else -> error("Not a supported Lyrimuse backup")
            }
        }
        val library = archive?.let { parseLibrary(it, checkActive) }
        return Prepared(library?.tracks.orEmpty(), library?.skipped ?: 0, library?.failed ?: 0,
            config != null, config?.let(::parseCredentials))
    }

    fun importPrepared(
        context: Context,
        prepared: Prepared,
        includeLyrics: Boolean,
        includeLastFm: Boolean,
        mode: LyricifyImportMode,
        checkActive: () -> Unit,
        onProgress: (LyricifyImportProgress) -> Unit,
    ): Report {
        require(includeLyrics || includeLastFm) { "Select data to import" }
        require(!includeLyrics || prepared.tracks.isNotEmpty()) { "No timed lyrics are available" }
        val credentials = if (includeLastFm) requireNotNull(prepared.credentials) {
            "Last.fm credentials are incomplete"
        } else null
        checkActive()
        val (imported, skipped) = if (includeLyrics) {
            OnlineLyricCacheStore(context).importLyricifyTracks(
                prepared.tracks.asSequence(), mode, 0L, checkActive, onProgress, sourceName = "Lyrimuse"
            )
        } else 0 to 0

        // The cache has committed at this point. Report an account-write failure separately.
        var accountFailed = false
        if (credentials != null) {
            onProgress(LyricifyImportProgress(LyricifyImportStage.COMMITTING))
            try {
                LastFmSecureStore(context).restoreFromBackup(credentials)
            } catch (_: Exception) {
                accountFailed = true
            }
        }
        return Report(imported, skipped + if (includeLyrics) prepared.skipped else 0,
            if (includeLyrics) prepared.failed else 0, credentials != null && !accountFailed, accountFailed)
    }

    internal fun readBounded(input: InputStream, checkActive: () -> Unit = {}): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            checkActive()
            val size = input.read(buffer)
            if (size < 0) break
            require(out.size().toLong() + size <= MAX_INPUT_BYTES) { "Lyrimuse backup is too large" }
            out.write(buffer, 0, size)
        }
        return out.toByteArray()
    }

    internal fun decode(input: ByteArray, checkActive: () -> Unit = {}): String {
        require(input.size <= MAX_INPUT_BYTES) { "Lyrimuse backup is too large" }
        val first = input.indexOfFirst { it.toInt().toChar() !in " \t\r\n" }
        if (first >= 0 && input[first] == '{'.code.toByte()) {
            require(input.size <= MAX_DECODED_BYTES) { "Decoded Lyrimuse backup is too large" }
            return input.toString(Charsets.UTF_8)
        }
        val inflater = Inflater(true) // Apple's Compression ZLIB emits raw DEFLATE.
        try {
            inflater.setInput(input)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                checkActive()
                val size = inflater.inflate(buffer)
                require(out.size().toLong() + size <= MAX_DECODED_BYTES) { "Decoded Lyrimuse backup is too large" }
                if (size == 0) require(inflater.finished()) { "Incomplete Lyrimuse backup" }
                out.write(buffer, 0, size)
            }
            require(inflater.remaining == 0) { "Unexpected data after the Lyrimuse backup" }
            return out.toString(Charsets.UTF_8.name())
        } finally {
            inflater.end()
        }
    }

    private data class Song(val artist: String, val title: String, val album: String) {
        val key: String get() = "$artist|$title|$album"
    }
    private data class Document(val song: Song, val source: String, val body: String)
    private data class Library(
        val tracks: List<Pair<LyricifyTrackIdentity, LyricifyImportSelection>>,
        val skipped: Int,
        val failed: Int,
    )
    private val suffixes = listOf(".tr.lrc", ".roma.lrc", ".lrc", ".yrc")

    private fun parseLibrary(root: JSONObject, checkActive: () -> Unit): Library {
        val files = root.getJSONObject("files")
        require(files.length() <= LyricifyDatabaseImporter.MAX_TRACKS * 4) { "Too many lyric files" }
        val meta = root.optString("meta").takeIf { it.isNotEmpty() }?.let {
            val bytes = Base64.getDecoder().decode(it)
            require(bytes.size <= MAX_DECODED_BYTES) { "Lyrics metadata is too large" }
            JSONObject(bytes.toString(Charsets.UTF_8))
        } ?: JSONObject()
        val groups = linkedMapOf<Song, MutableMap<String, Document>>()
        val unsupported = linkedSetOf<String>()
        val brokenGroups = mutableSetOf<Song>()
        var failed = 0
        for (name in files.keys()) {
            checkActive()
            val text = files.opt(name) as? String
            val document = text?.let { parseHeader(it) }
            val suffix = suffixes.firstOrNull(name::endsWith)
            if (suffix == null) {
                unsupported.add(document?.song?.key ?: name)
                continue
            }
            if (document == null) { failed++; continue }
            val group = groups.getOrPut(document.song) { linkedMapOf() }
            if (group[suffix]?.body?.let { it != document.body } == true) brokenGroups.add(document.song)
            else group[suffix] = document
        }
        require(groups.size <= LyricifyDatabaseImporter.MAX_TRACKS) { "Too many songs" }
        val tracks = mutableListOf<Pair<LyricifyTrackIdentity, LyricifyImportSelection>>()
        for ((song, docs) in groups) {
            checkActive()
            if (song in brokenGroups) { failed++; continue }
            val entry = meta.optJSONObject(song.key) ?: JSONObject()
            try {
                tracks.add(convert(song, docs, entry))
            } catch (_: IllegalArgumentException) {
                failed++
            }
        }
        val groupKeys = groups.keys.mapTo(mutableSetOf()) { it.key }
        val skippedKeys = meta.keys().asSequence().filter { it !in groupKeys }.toMutableSet()
        skippedKeys.addAll(unsupported)
        val binaryCount = root.optJSONObject("bin")?.length() ?: 0
        return Library(tracks, skippedKeys.size + binaryCount, failed)
    }

    private fun parseHeader(text: String): Document? {
        val lines = text.removePrefix("\uFEFF").split('\n')
        if (lines.size < 4) return null
        fun tag(index: Int, key: String): String? {
            val line = lines.getOrNull(index)?.trimEnd('\r') ?: return null
            return line.takeIf { it.startsWith("[$key:") && it.endsWith(']') }?.substring(key.length + 2, line.length - 1)
        }
        val artist = tag(0, "ar") ?: return null
        val title = tag(1, "ti")?.takeIf { it.isNotBlank() } ?: return null
        val album = tag(2, "al") ?: return null
        var index = 3
        val source = tag(index, "source")?.also { index++ }.orEmpty()
        if (lines.getOrNull(index)?.trimEnd('\r') == "[manual:1]") index++
        if (lines.getOrNull(index)?.trimEnd('\r') != "") return null
        return Document(Song(artist, title, album), source, lines.drop(index + 1).joinToString("\n"))
    }

    private fun convert(song: Song, docs: Map<String, Document>, meta: JSONObject): Pair<LyricifyTrackIdentity, LyricifyImportSelection> {
        val main = docs[".yrc"]?.takeIf { LyricifyDatabaseImporter.hasDisplayableLyrics(parse(it, "yrc")) }
            ?: docs[".lrc"] ?: throw IllegalArgumentException("No timed lyrics")
        val original = parse(main, if (main === docs[".yrc"]) "yrc" else "lrc")
        require(LyricifyDatabaseImporter.hasDisplayableLyrics(original)) { "No timed lyrics" }
        val translations = docs[".tr.lrc"]?.let { parse(it, "lrc") }.orEmpty()
        val roman = docs[".roma.lrc"]?.let { parse(it, "lrc") }.orEmpty()
        val durationSecs = meta.optDouble("duration_secs", 0.0)
        require(durationSecs.isFinite() && durationSecs >= 0 && durationSecs <= Long.MAX_VALUE / 1000.0)
        val aliases = buildMap {
            val appleId = meta.optJSONObject("player_song_ids")?.optString("com.apple.Music")
            putAll(LyricifyDatabaseImporter.identityIds(OnlineLyricProvider.AppleMusic, appleId))
        }
        val provider = when (main.source.lowercase(Locale.ROOT)) {
            "qq" -> OnlineLyricProvider.QQMusic
            "netease" -> OnlineLyricProvider.Netease
            "kugou" -> OnlineLyricProvider.Kugou
            "soda" -> OnlineLyricProvider.SodaMusic
            "applemusic" -> OnlineLyricProvider.AppleMusic
            "lrclib" -> OnlineLyricProvider.Lrclib
            "musixmatch" -> OnlineLyricProvider.Musixmatch
            else -> OnlineLyricProvider.LrcApi
        }
        val result = OnlineLyricFetcher.LyricResult(
            api = "Lyrimuse · ${main.source.ifBlank { "Local" }}", lyrics = main.body, parsedLines = original,
            hasSyllable = original.any { !it.syllables.isNullOrEmpty() }, provider = provider,
            matchedTitle = song.title, matchedArtist = song.artist, matchedAlbum = song.album,
            matchedDurationMs = (durationSecs * 1000).toLong().takeIf { it > 0 },
            providerTrackId = aliases[provider.id], isrc = meta.optJSONArray("isrcs")?.optString(0)?.takeIf { it.isNotBlank() },
            translationLyrics = translations.takeIf { it.isNotEmpty() }?.let(::toLrc),
            romanLyrics = roman.takeIf { it.isNotEmpty() }?.let(::toLrc),
        )
        val identity = LyricifyTrackIdentity(song.title, song.artist, song.album, (durationSecs * 1000).toLong(), aliases)
        require(LyricifyDatabaseImporter.hasMatchableIdentity(identity, result)) { "Insufficient track identity" }
        return identity to LyricifyImportSelection(listOf(result), result)
    }

    private fun parse(document: Document, format: String): List<OnlineLyricFetcher.LyricLine> {
        val offset = Regex("(?im)^\\[offset:([+-]?\\d+)][ \\t]*\\r?$").find(document.body)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        require(offset in -60_000L..60_000L) { "Invalid lyric offset" }
        return LyricifyDatabaseImporter.shiftLines(LyricifyDatabaseImporter.parseDocument(format, document.body), -offset)
    }

    private fun toLrc(lines: List<OnlineLyricFetcher.LyricLine>): String = lines.joinToString("\n") {
        "[%02d:%02d.%03d]%s".format(Locale.ROOT, it.startTime / 60_000, it.startTime / 1000 % 60, it.startTime % 1000, it.text)
    }

    private fun parseCredentials(config: JSONObject): LastFmCredentials? {
        fun value(key: String, maxLength: Int): String? = (config.opt(key) as? String)?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= maxLength }
        val key = value("lastfm_scrobble_api_key", 32)?.takeIf { it.matches(Regex("[0-9a-fA-F]{32}")) } ?: return null
        val secret = value("lastfm_scrobble_secret", 4096) ?: return null
        val session = value("lastfm_scrobble_session_key", 8192) ?: return null
        val username = value("lastfm_scrobble_username", 1024)
        return LastFmCredentials(key, secret, session, username)
    }
}
