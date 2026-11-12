package com.example.islandlyrics.lyrics.importer

import android.content.Context
import android.content.ContextWrapper
import com.example.islandlyrics.integration.lastfm.LastFmCredentials
import com.example.islandlyrics.lyrics.cache.OnlineLyricCacheStore
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import com.example.islandlyrics.lyrics.online.parser.OnlineLyricSidecarMerger
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.rules.ParserRule
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

class LyrimuseBackupImporterTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun document(body: String, source: String = "netease") =
        "[ar:Artist]\n[ti:Title]\n[al:Album]\n[source:$source]\n\n$body"

    private fun archive(files: Map<String, String>, meta: JSONObject = JSONObject()): ByteArray = JSONObject()
        .put("v", 2).put("files", JSONObject(files))
        .put("meta", Base64.getEncoder().encodeToString(meta.toString().toByteArray()))
        .toString().toByteArray()

    private fun config(key: String = "a".repeat(32), session: String? = "session"): ByteArray = JSONObject()
        .put("version", 1).put("config", JSONObject()
            .put("lastfm_scrobble_api_key", key).put("lastfm_scrobble_secret", "secret")
            .put("lastfm_scrobble_session_key", session).put("lastfm_scrobble_username", "user")
            .put("unrelated_setting", "must not be imported"))
        .put("appSettings", JSONObject().put("np:lyricsOffsetsByTrackJSON", "{\"Artist|Title\":9999}"))
        .toString().toByteArray()

    private fun compress(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        try { DeflaterOutputStream(out, deflater).use { it.write(bytes) } } finally { deflater.end() }
        return out.toByteArray()
    }

    @Test fun acceptsRawDeflateAndPlainJsonWithoutChangingTheDocument() {
        val raw = archive(mapOf("song.lrc" to document("[00:01.00]Text")))
        assertEquals(raw.toString(Charsets.UTF_8), LyrimuseBackupImporter.decode(compress(raw)))
        assertEquals(raw.toString(Charsets.UTF_8), LyrimuseBackupImporter.decode(raw))
    }

    @Test fun rejectsTruncatedStreamsAndTrailingData() {
        val raw = compress(archive(emptyMap()))
        assertThrows(IllegalArgumentException::class.java) { LyrimuseBackupImporter.decode(raw.copyOf(raw.size - 1)) }
        assertThrows(IllegalArgumentException::class.java) { LyrimuseBackupImporter.decode(raw + byteArrayOf(1)) }
    }

    @Test fun optionalMetadataAcceptsMissingNullAndEmptyValues() {
        for (value in listOf(null, JSONObject.NULL, "")) {
            val input = JSONObject(String(archive(mapOf("a.lrc" to document("[00:01]Text")))))
            if (value == null) input.remove("meta") else input.put("meta", value)
            val prepared = LyrimuseBackupImporter.prepareBytes(listOf(input.toString().toByteArray()))
            assertEquals(1, prepared.tracks.size)
            assertEquals(0, prepared.failed)
        }
    }

    @Test fun invalidMetadataTypesAndMalformedStringsAreStillRejected() {
        val input = JSONObject(String(archive(mapOf("a.lrc" to document("[00:01]Text")))))
        for (value in listOf(123, true, JSONObject(), JSONArray(), "!")) {
            assertThrows(IllegalArgumentException::class.java) {
                LyrimuseBackupImporter.prepareBytes(listOf(input.put("meta", value).toString().toByteArray()))
            }
        }
        for (value in listOf("null", Base64.getEncoder().encodeToString("[]".toByteArray()))) {
            assertThrows(JSONException::class.java) {
                LyrimuseBackupImporter.prepareBytes(listOf(input.put("meta", value).toString().toByteArray()))
            }
        }
    }

    @Test fun optionalIdsAndIsrcOnlyAcceptStrings() {
        val files = mapOf("a.lrc" to document("[00:01]Text", "applemusic"))
        for (value in listOf(JSONObject.NULL, 123, true, JSONObject(), JSONArray())) {
            val entry = JSONObject().put("player_song_ids", JSONObject().put("com.apple.Music", value))
                .put("isrcs", JSONArray().put(value))
            val prepared = LyrimuseBackupImporter.prepareBytes(listOf(archive(files, JSONObject().put("Artist|Title|Album", entry))))
            val (identity, selection) = prepared.tracks.single()
            assertTrue(identity.providerIds.isEmpty())
            assertNull(selection.selected.providerTrackId)
            assertNull(selection.selected.isrc)
        }
    }

    @Test fun realOptionalIdsAndIsrcArePreserved() {
        val entry = JSONObject().put("player_song_ids", JSONObject().put("com.apple.Music", "123"))
            .put("isrcs", JSONArray().put("USABC1234567"))
        val prepared = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf("a.lrc" to document("[00:01]Text", "applemusic")),
            JSONObject().put("Artist|Title|Album", entry))))
        val (identity, selection) = prepared.tracks.single()
        assertEquals(mapOf("apple_music" to "123"), identity.providerIds)
        assertEquals("123", selection.selected.providerTrackId)
        assertEquals("USABC1234567", selection.selected.isrc)
    }

    @Test fun limitsDecodedBytesWhileInflating() {
        val compressed = compress(ByteArray(LyrimuseBackupImporter.MAX_DECODED_BYTES + 1) { 'x'.code.toByte() })
        assertThrows(IllegalArgumentException::class.java) { LyrimuseBackupImporter.decode(compressed) }
    }

    @Test fun combinesFilesByTheirAuthoritativeHeaderInsteadOfTheFilenameOrProviderTags() {
        val meta = JSONObject().put("Artist|Title|Album", JSONObject()
            .put("duration_secs", 180.25).put("player_song_ids", JSONObject().put("com.apple.Music", "123")))
        val prepared = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf(
            "unrelated name.lrc" to document("[ti:Wrong provider title]\n[ar:Wrong artist]\n[00:01]AB\n[00:02]CD"),
            "another.yrc" to document("[1000,1000](1000,500,0)A(1500,500,0)B\n[2000,1000](2000,500,0)C(2500,500,0)D"),
            "translation.tr.lrc" to document("[00:01:00]译文\n[00:02.00]第二句"),
            "reading.roma.lrc" to document("[00:01.000]roma"),
        ), meta)))
        val (identity, selection) = prepared.tracks.single()
        assertEquals(LyricifyTrackIdentity("Title", "Artist", "Album", 180_250L, mapOf("apple_music" to "123")), identity)
        val result = selection.selected
        assertTrue(result.hasSyllable)
        assertEquals(listOf(1_000L, 1_500L), result.parsedLines!!.first().syllables!!.map { it.startTime })
        val displayed = OnlineLyricSidecarMerger.withSidecars(result, ParserRule("test", receiveOnlineTranslation = true, receiveOnlineRomanization = true))
        assertEquals("译文", displayed.first().translation)
        assertEquals("roma", displayed.first().roma)
        val withoutSidecars = OnlineLyricSidecarMerger.withSidecars(result, ParserRule("test", receiveOnlineTranslation = false, receiveOnlineRomanization = false))
        assertNull(withoutSidecars.first().translation)
        assertNull(withoutSidecars.first().roma)
        assertEquals("Title", result.matchedTitle)
        assertEquals("Artist", result.matchedArtist)
        assertEquals(0, prepared.skipped)
        assertEquals(0, prepared.failed)
    }

    @Test fun lrcFallbackRetainsLineTimingAndDoesNotInventWordTiming() {
        val prepared = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf(
            "a.lrc" to document("[00:01.00]Text"), "a.yrc" to document(""),
        ))))
        val result = prepared.tracks.single().second.selected
        assertFalse(result.hasSyllable)
        assertEquals(1_000L, result.parsedLines!!.single().startTime)
    }

    @Test fun allMainFormatCombinationsSelectTheConfirmedPriority() {
        val formats = listOf(
            ".yrc" to "[1000,1000](1000,1000,0)YRC",
            ".enhanced.lrc" to "[00:01.000]<00:01.000>Enhanced<00:02.000>",
            ".lrc" to "[00:01.000]Plain",
        )
        for (mask in 1..7) {
            val included = formats.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
            val prepared = LyrimuseBackupImporter.prepareBytes(listOf(archive(
                included.associate { (suffix, body) -> "song$suffix" to document(body, suffix) }
            )))
            assertEquals("combination $mask", 1, prepared.tracks.size)
            assertEquals(0, prepared.failed)
            assertEquals(0, prepared.skipped)
            val selected = prepared.tracks.single().second.selected
            // The branch owns file selection; enhanced text/word parsing is supplied by dev.
            assertEquals(included.first().second, selected.lyrics)
            assertEquals("Lyrimuse · ${included.first().first}", selected.api)
        }
    }

    @Test fun unusableMainFormatsFallBackButReadableEnhancedLyricsRemainPreferred() {
        val enhanced = "[00:01.000]<00:00.500>Readable<00:02.000>"
        for ((yrc, enhancedBody, expected) in listOf(
            Triple("", enhanced, enhanced),
            Triple("", "[00:01.000]", "[00:01.000]Plain"),
            Triple("[offset:60001]\n[1000,1000](1000,1000,0)YRC", enhanced, enhanced),
            Triple("", "[offset:60001]\n$enhanced", "[00:01.000]Plain"),
        )) {
            val prepared = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf(
                "a.yrc" to document(yrc), "a.enhanced.lrc" to document(enhancedBody),
                "a.lrc" to document("[00:01.000]Plain"),
            ))))
            assertEquals(0, prepared.failed)
            assertEquals(expected, prepared.tracks.single().second.selected.lyrics)
        }
    }

    @Test fun enhancedOffsetsAndSidecarsSurviveFileSelection() {
        val enhanced = "[offset:100]\n[00:01.000]<00:01.000>Enhanced<00:02.000>"
        val prepared = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf(
            "a.enhanced.lrc" to document(enhanced), "a.lrc" to document("[00:01.000]Plain"),
            "a.tr.lrc" to document("[offset:100]\n[00:01.000]译文"),
            "a.roma.lrc" to document("[offset:100]\n[00:01.000]roma"),
        ))))
        val selected = prepared.tracks.single().second.selected
        assertEquals(enhanced, selected.lyrics)
        assertEquals(900L, selected.parsedLines!!.first().startTime)
        assertEquals("[00:00.900]译文", selected.translationLyrics)
        assertEquals("[00:00.900]roma", selected.romanLyrics)
    }

    @Test fun boundedHeaderReadingPreservesBomCrLfAndOptionalSourceAndManualTags() {
        val body = "[offset:100]\r\n[00:01.000]Text\r\n"
        for (withSource in listOf(false, true)) {
            val header = "\uFEFF[ar:Artist]\r\n[ti:Title]\r\n[al:Album]\r\n" +
                (if (withSource) "[source:netease]\r\n" else "") + "[manual:1]\r\n\r\n"
            val prepared = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf("a.lrc" to header + body))))
            val selected = prepared.tracks.single().second.selected
            assertEquals(body, selected.lyrics)
            assertEquals(if (withSource) "Lyrimuse · netease" else "Lyrimuse · Local", selected.api)
            assertEquals(900L, selected.parsedLines!!.single().startTime)
        }
    }

    @Test fun contradictoryEnhancedFilesStillFailTheSong() {
        val prepared = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf(
            "a.enhanced.lrc" to document("[00:01.000]First"),
            "b.enhanced.lrc" to document("[00:01.000]Second"),
            "a.yrc" to document("[1000,1000](1000,1000,0)YRC"),
        ))))
        assertTrue(prepared.tracks.isEmpty())
        assertEquals(1, prepared.failed)
    }

    @Test fun rootAndMetadataNestingAreBoundedBeforeJsonConstruction() {
        fun nested(depth: Int) = "[".repeat(depth) + "0" + "]".repeat(depth)
        for (metadata in listOf(false, true)) {
            fun input(depth: Int): ByteArray {
                val extra = "{\"extra\":${nested(depth)}}"
                return if (metadata) JSONObject(String(archive(emptyMap())))
                    .put("meta", Base64.getEncoder().encodeToString(extra.toByteArray())).toString().toByteArray()
                else "{\"v\":2,\"files\":{},\"extra\":${nested(depth)}}".toByteArray()
            }
            assertTrue(LyrimuseBackupImporter.prepareBytes(listOf(input(LyrimuseBackupImporter.MAX_JSON_DEPTH - 1))).tracks.isEmpty())
            for (depth in listOf(LyrimuseBackupImporter.MAX_JSON_DEPTH, 20_000)) {
                assertThrows(LyrimuseBackupImporter.LimitExceeded::class.java) {
                    LyrimuseBackupImporter.prepareBytes(listOf(input(depth)))
                }
            }
        }
    }

    @Test fun bracketsAndEscapedQuotesInJsonStringsDoNotCountAsNesting() {
        val root = JSONObject(String(archive(mapOf("a.lrc" to document("[00:01]Text")))))
            .put("note", "\\\"quoted\\\"" + "{[,:;=]}".repeat(LyrimuseBackupImporter.MAX_JSON_STRUCTURES / 8 + 1))
        assertEquals(1, LyrimuseBackupImporter.prepareBytes(listOf(root.toString().toByteArray())).tracks.size)
    }

    @Test fun jsonStructureBudgetAcceptsBoundaryAndRejectsTheNextArrayElement() {
        for (metadata in listOf(false, true)) {
            // Count containers and separators in the root and, when present, decoded metadata.
            val count = LyrimuseBackupImporter.MAX_JSON_STRUCTURES - if (metadata) 9 else 7
            fun input(elements: Int): ByteArray {
                val extra = "[" + "0,".repeat(elements - 1) + "0]"
                return if (metadata) {
                    val meta = Base64.getEncoder().encodeToString("{\"extra\":$extra}".toByteArray())
                    "{\"v\":2,\"files\":{},\"meta\":\"$meta\"}".toByteArray()
                } else "{\"v\":2,\"files\":{},\"extra\":$extra}".toByteArray()
            }
            assertTrue(LyrimuseBackupImporter.prepareBytes(listOf(input(count))).tracks.isEmpty())
            assertThrows(LyrimuseBackupImporter.LimitExceeded::class.java) {
                LyrimuseBackupImporter.prepareBytes(listOf(input(count + 1)))
            }
        }
    }

    @Test fun shallowContainersAndObjectMembersAreBoundedBeforeJsonConstruction() {
        val count = LyrimuseBackupImporter.MAX_JSON_STRUCTURES / 2 + 1
        for (extra in listOf(
            "[" + "{},".repeat(count - 1) + "{}]",
            "{" + (0 until count).joinToString(",") { "\"key$it\":0" } + "}",
        )) {
            for (metadata in listOf(false, true)) {
                val input = if (metadata) {
                    val meta = Base64.getEncoder().encodeToString("{\"extra\":$extra}".toByteArray())
                    "{\"v\":2,\"files\":{},\"meta\":\"$meta\"}"
                } else "{\"v\":2,\"files\":{},\"extra\":$extra}"
                assertThrows(LyrimuseBackupImporter.LimitExceeded::class.java) {
                    LyrimuseBackupImporter.prepareBytes(listOf(input.toByteArray()))
                }
            }
        }
    }

    @Test fun jsonStructureBudgetIsSharedByFilesAndDecodedMetadata() {
        val extra = "[" + "0,".repeat(LyrimuseBackupImporter.MAX_JSON_STRUCTURES / 2 - 1) + "0]"
        val lyrics = "{\"v\":2,\"files\":{},\"extra\":$extra}".toByteArray()
        val config = "{\"version\":1,\"config\":{},\"extra\":$extra}".toByteArray()
        val meta = Base64.getEncoder().encodeToString("{\"extra\":$extra}".toByteArray())
        val metadataOnly = "{\"v\":2,\"files\":{},\"meta\":\"$meta\"}".toByteArray()
        for (input in listOf(lyrics, config, metadataOnly)) {
            assertTrue(LyrimuseBackupImporter.prepareBytes(listOf(input)).tracks.isEmpty())
        }
        assertThrows(LyrimuseBackupImporter.LimitExceeded::class.java) {
            LyrimuseBackupImporter.prepareBytes(listOf(lyrics, config))
        }
        assertThrows(LyrimuseBackupImporter.LimitExceeded::class.java) {
            LyrimuseBackupImporter.prepareBytes(listOf("{\"v\":2,\"files\":{},\"meta\":\"$meta\",\"extra\":$extra}".toByteArray()))
        }
    }

    @Test fun androidJsonMissingArrayValuesCannotBypassStructureBudget() {
        for (separator in listOf(',', ';')) {
            val extra = "[" + separator.toString().repeat(LyrimuseBackupImporter.MAX_JSON_STRUCTURES) + "]"
            assertThrows(LyrimuseBackupImporter.LimitExceeded::class.java) {
                LyrimuseBackupImporter.prepareBytes(listOf("{\"v\":2,\"files\":{},\"extra\":$extra}".toByteArray()))
            }
        }
    }

    @Test fun androidJsonStringsAndCommentsCannotHideDeepNesting() {
        val deep = "[".repeat(100) + "0" + "]".repeat(100)
        for (prefix in listOf("'${"]".repeat(100)}'", "0/*${"]".repeat(100)}'*/", "0//${"]".repeat(100)}'\n", "0#${"]".repeat(100)}'\n", "unquoted'quote", "unquoted\"quote")) {
            val input = "{\"v\":2,\"files\":{},\"note\":$prefix,\"deep\":$deep}"
            assertThrows(LyrimuseBackupImporter.LimitExceeded::class.java) {
                LyrimuseBackupImporter.prepareBytes(listOf(input.toByteArray()))
            }
        }
    }

    @Test fun cumulativeInputBudgetAcceptsItsBoundaryAndRejectsTheNextMarker() {
        for ((extra, exceeds) in listOf(0 to false, 1 to true)) {
            val input = archive(mapOf("a.lrc" to document("(".repeat(LyrimuseBackupImporter.MAX_INPUT_MARKERS.toInt() + extra))))
            if (exceeds) assertThrows(LyrimuseBackupImporter.LimitExceeded::class.java) {
                LyrimuseBackupImporter.prepareBytes(listOf(input))
            } else assertEquals(1, LyrimuseBackupImporter.prepareBytes(listOf(input)).failed)
        }
    }

    @Test fun unusedFallbackVersionsDoNotConsumeTheParseBudget() {
        val yrc = "[1000,1000](1000,1000,0)YRC"
        val prepared = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf(
            "a.yrc" to document(yrc),
            "a.enhanced.lrc" to document("(".repeat(LyrimuseBackupImporter.MAX_INPUT_MARKERS.toInt() + 1)),
            "a.lrc" to document("[00:01.000]Unused fallback"),
        ))))
        assertEquals(yrc, prepared.tracks.single().second.selected.lyrics)
        assertEquals(0, prepared.failed)
    }

    @Test fun cumulativeBudgetRejectsIndividuallyValidSongsAndIncludesSidecars() {
        val body = List(10_000) { "[00:01.000]Text" }.joinToString("\n")
        // Each document is within the existing single-document limits.
        assertTrue(LyricifyDatabaseImporter.parseDocument("lrc", body).isNotEmpty())
        for (sidecars in listOf(false, true)) {
            val files = buildMap {
                repeat(if (sidecars) 10 else 26) { song ->
                    for (suffix in if (sidecars) listOf(".lrc", ".tr.lrc", ".roma.lrc") else listOf(".lrc")) {
                        put("$song$suffix", document(body).replace("[ti:Title]", "[ti:Title$song]"))
                    }
                }
            }
            assertThrows(LyrimuseBackupImporter.LimitExceeded::class.java) {
                LyrimuseBackupImporter.prepareBytes(listOf(archive(files), config()))
            }
        }
    }

    @Test fun cancellationDuringJsonScanningPropagatesAfterWorkHasStarted() {
        val input = JSONObject(String(archive(emptyMap()))).put("note", "x".repeat(20_000)).toString().toByteArray()
        var checks = 0
        assertThrows(CancellationException::class.java) {
            LyrimuseBackupImporter.prepareBytes(listOf(input)) {
                if (++checks == 4) throw CancellationException("cancel during scan")
            }
        }
        assertEquals(4, checks)
    }

    private fun cacheContext(): Context {
        val directory = temporaryFolder.newFolder()
        val files = File(directory, "files").also { it.mkdir() }
        val cache = File(directory, "cache").also { it.mkdir() }
        return object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = files
            override fun getCacheDir(): File = cache
        }
    }

    private fun snapshot(context: Context): Map<String, List<Byte>> {
        val directory = File(context.filesDir, "cache_store")
        return directory.walkTopDown().filter { it.isFile }.associate { it.relativeTo(directory).path to it.readBytes().toList() }
    }

    private fun cacheFile(context: Context, title: String): File {
        val entries = JSONObject(File(context.filesDir, "cache_store/index.json").readText()).getJSONObject("entries")
        val id = entries.keys().asSequence().single { entries.getJSONObject(it).getString("title") == title }
        return File(context.filesDir, "cache_store/lyrics/${id.take(2)}/$id.json")
    }

    private fun mediaInfo(album: String = "Album") = LyricRepository.MediaInfo(
        title = "Title", artist = "Artist", album = album, duration = 180_000L, packageName = "test.player",
    )

    @Test fun importedLyricsCanBeReadFromDiskAndSkippedWithoutChangingCacheBytes() {
        val context = cacheContext()
        val prepared = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf(
            "a.yrc" to document("[1000,1000](1000,500,0)A(1500,500,0)B"),
            "a.tr.lrc" to document("[00:01.000]译文"), "a.roma.lrc" to document("[00:01.000]roma"),
        ))))
        val first = LyrimuseBackupImporter.importPrepared(context, prepared, true, false, LyricifyImportMode.OVERWRITE, {}, {})
        assertEquals(LyrimuseBackupImporter.Report(1, 0, 0, false, false), first)
        val store = OnlineLyricCacheStore(context)
        val hit = store.getCachedLyric(mediaInfo(), "Title", "Artist")!!
        assertEquals("AB", hit.result.parsedLines!!.single().text)
        assertEquals(listOf(1_000L, 1_500L), hit.result.parsedLines.single().syllables!!.map { it.startTime })
        assertEquals("[00:01.000]译文", hit.result.translationLyrics)
        assertEquals("[00:01.000]roma", hit.result.romanLyrics)
        // getCachedLyric updates access time; compare bytes only after that read.
        val before = snapshot(context)
        val second = LyrimuseBackupImporter.importPrepared(context, prepared, true, false, LyricifyImportMode.SKIP, {}, {})
        assertEquals(LyrimuseBackupImporter.Report(0, 1, 0, false, false), second)
        assertEquals(before, snapshot(context))
        assertEquals(1, OnlineLyricCacheStore(context).getLyricCacheStats().entryCount)
        assertFalse(File(context.filesDir, "cache_store/lyricify-import-transaction").exists())
        assertTrue(context.cacheDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun emptyExistingCacheDoesNotBlockImportOrChangeValidSelections() {
        for (mode in LyricifyImportMode.entries) {
            val context = cacheContext()
            fun prepared(text: String) = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf(
                "a.lrc" to document("[00:01.000]$text"),
                "b.lrc" to document("[00:01.000]$text broken").replace("[ti:Title]", "[ti:Broken]"),
            ))))
            LyrimuseBackupImporter.importPrepared(context, prepared("Old"), true, false, LyricifyImportMode.OVERWRITE, {}, {})
            val emptyFile = cacheFile(context, "Broken").also { it.writeBytes(byteArrayOf()) }
            val emptyId = emptyFile.nameWithoutExtension
            val indexFile = File(context.filesDir, "cache_store/index.json")
            val emptyHeader = JSONObject(indexFile.readText()).getJSONObject("entries").getJSONObject(emptyId).toString()
            val validFile = cacheFile(context, "Title")
            val validBytes = validFile.readBytes().toList()

            val incoming = prepared("New")
            val report = LyrimuseBackupImporter.importPrepared(context, incoming, true, false, mode, {}, {})
            assertEquals(if (mode == LyricifyImportMode.SKIP) 1 else 2, report.imported)
            assertEquals(if (mode == LyricifyImportMode.SKIP) 1 else 0, report.skipped)
            assertEquals(0, report.failed)
            assertTrue(emptyFile.isFile)
            assertEquals(0L, emptyFile.length())
            assertEquals(emptyHeader, JSONObject(indexFile.readText()).getJSONObject("entries").getJSONObject(emptyId).toString())
            if (mode == LyricifyImportMode.SKIP) assertEquals(validBytes, validFile.readBytes().toList())

            val store = OnlineLyricCacheStore(context)
            assertEquals(if (mode == LyricifyImportMode.OVERWRITE) "New" else "Old",
                store.getCachedLyric(mediaInfo(), "Title", "Artist")!!.result.parsedLines!!.single().text)
            assertEquals("New broken", store.getCachedLyric(mediaInfo().copy(title = "Broken"), "Broken", "Artist")!!
                .result.parsedLines!!.single().text)
            val beforeSkip = snapshot(context)
            assertEquals(LyrimuseBackupImporter.Report(0, 2, 0, false, false),
                LyrimuseBackupImporter.importPrepared(context, incoming, true, false, LyricifyImportMode.SKIP, {}, {}))
            assertEquals(beforeSkip, snapshot(context))
            assertFalse(File(context.filesDir, "cache_store/lyricify-import-transaction").exists())
            assertTrue(context.cacheDir.listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun nonEmptyInvalidCacheStillStopsImportWithoutChangingExistingFiles() {
        val context = cacheContext()
        val prepared = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf("a.lrc" to document("[00:01.000]Text")))))
        LyrimuseBackupImporter.importPrepared(context, prepared, true, false, LyricifyImportMode.OVERWRITE, {}, {})
        cacheFile(context, "Title").writeText("{broken")
        val before = snapshot(context)
        assertThrows(JSONException::class.java) {
            LyrimuseBackupImporter.importPrepared(context, prepared, true, false, LyricifyImportMode.OVERWRITE, {}, {})
        }
        assertEquals(before, snapshot(context))
        assertFalse(File(context.filesDir, "cache_store/lyricify-import-transaction").exists())
        assertTrue(context.cacheDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun mergeRetainsFallbackDurationForPlaybackAndRepeatedSkip() {
        val context = cacheContext()
        val store = OnlineLyricCacheStore(context)
        fun lyric(text: String, duration: Long? = null) = OnlineLyricFetcher.LyricResult(
            api = "test", lyrics = "[00:01.000]$text", parsedLines = LyricifyDatabaseImporter.parseDocument("lrc", "[00:01.000]$text"),
            hasSyllable = false, provider = OnlineLyricProvider.LrcApi, matchedDurationMs = duration,
        )
        val old = lyric("Old")
        val incoming = lyric("New", 180_000L)
        val identity = LyricifyTrackIdentity("Title", "Artist")
        assertEquals(1 to 0, store.importLyricifyTracks(listOf(identity.copy(durationMs = 180_000L) to LyricifyImportSelection(listOf(old), old)), LyricifyImportMode.OVERWRITE))
        val tracks = listOf(identity to LyricifyImportSelection(listOf(incoming), incoming))
        assertEquals(1 to 0, store.importLyricifyTracks(tracks, LyricifyImportMode.MERGE))
        assertEquals("Old", OnlineLyricCacheStore(context).getCachedLyric(mediaInfo(album = ""), "Title", "Artist")!!.result.parsedLines!!.single().text)
        val before = snapshot(context)
        assertEquals(0 to 1, OnlineLyricCacheStore(context).importLyricifyTracks(tracks, LyricifyImportMode.SKIP))
        assertEquals(before, snapshot(context))
        assertEquals(1, OnlineLyricCacheStore(context).getLyricCacheStats().entryCount)
    }

    @Test fun cancellationDuringCacheWritesRestoresThePreviousFiles() {
        val context = cacheContext()
        fun prepared(text: String) = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf("a.lrc" to document("[00:01.000]$text")))))
        LyrimuseBackupImporter.importPrepared(context, prepared("Old"), true, false, LyricifyImportMode.OVERWRITE, {}, {})
        val emptyPrepared = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf(
            "empty.lrc" to document("[00:01.000]Empty").replace("[ti:Title]", "[ti:Empty]"),
        ))))
        LyrimuseBackupImporter.importPrepared(context, emptyPrepared, true, false, LyricifyImportMode.OVERWRITE, {}, {})
        cacheFile(context, "Empty").writeBytes(byteArrayOf())
        val before = snapshot(context)
        var cancel = false
        var rolledBack = false
        assertThrows(CancellationException::class.java) {
            LyrimuseBackupImporter.importPrepared(context, prepared("New"), true, false, LyricifyImportMode.OVERWRITE,
                checkActive = { if (cancel) throw CancellationException("cancel during write") },
                onProgress = {
                    if (it.stage == LyricifyImportStage.WRITING && it.processed > 0) cancel = true
                    if (it.stage == LyricifyImportStage.ROLLING_BACK) rolledBack = true
                },
            )
        }
        assertTrue(rolledBack)
        assertEquals(before, snapshot(context))
        assertEquals("Old", OnlineLyricCacheStore(context).getCachedLyric(mediaInfo(), "Title", "Artist")!!.result.parsedLines!!.single().text)
        assertFalse(File(context.filesDir, "cache_store/lyricify-import-transaction").exists())
        assertTrue(context.cacheDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun lyricOffsetsShiftOriginalTranslationAndWordTimingEarlier() {
        val prepared = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf(
            "a.yrc" to document("[offset:100]\n[1000,1000](1000,500,0)A(1500,500,0)B"),
            "a.tr.lrc" to document("[offset:100]\n[00:01.000]译文"),
        ))))
        val result = prepared.tracks.single().second.selected
        assertEquals(900L, result.parsedLines!!.single().startTime)
        assertEquals(listOf(900L, 1400L), result.parsedLines.single().syllables!!.map { it.startTime })
        assertEquals("译文", OnlineLyricSidecarMerger.withSidecars(result, ParserRule("test", receiveOnlineTranslation = true)).single().translation)
        assertEquals("[00:00.900]译文", result.translationLyrics)
    }

    @Test fun distantSidecarsAreNotAttachedToTheWrongLine() {
        val prepared = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf(
            "a.lrc" to document("[00:01.000]Text"),
            "a.tr.lrc" to document("[00:10.000]Unrelated time"),
        ))))
        val displayed = OnlineLyricSidecarMerger.withSidecars(prepared.tracks.single().second.selected, ParserRule("test", receiveOnlineTranslation = true))
        assertNull(displayed.single().translation)
    }

    @Test fun unknownSourceKeepsItsOriginWithoutAddingANetworkProvider() {
        val prepared = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf("a.lrc" to document("[00:01]Text", "amll")))))
        val result = prepared.tracks.single().second.selected
        assertEquals("Lyrimuse · amll", result.api)
        assertEquals(OnlineLyricProvider.LrcApi, result.provider)
        assertNull(result.providerTrackId)
    }

    @Test fun metadataWithoutTimedFilesIsReportedAsSkipped() {
        val meta = JSONObject().put("Artist|Title|Album", JSONObject())
            .put("Other|Plain|Album", JSONObject().put("plain_lyrics", "Plain text"))
            .put("Other|Instrumental|Album", JSONObject().put("instrumental", true))
        val prepared = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf("a.lrc" to document("[00:01]Text")), meta)))
        assertEquals(1, prepared.tracks.size)
        assertEquals(2, prepared.skipped)
    }

    @Test fun malformedAndAmbiguousGroupsAreNotImported() {
        val malformed = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf("a.lrc" to "[00:01]Missing identity"))))
        assertTrue(malformed.tracks.isEmpty())
        assertEquals(1, malformed.failed)
        val ambiguous = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf(
            "a.lrc" to document("[00:01]First"), "b.lrc" to document("[00:01]Second"),
        ))))
        assertTrue(ambiguous.tracks.isEmpty())
        assertEquals(1, ambiguous.failed)
    }

    @Test fun timestampOnlyLyricsAreNotReportedAsSuccessfullyConverted() {
        val prepared = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf("a.lrc" to document("[00:01]\n[00:02]")))))
        assertTrue(prepared.tracks.isEmpty())
        assertEquals(1, prepared.failed)
    }

    @Test fun weakIdentitiesAreReportedAsFailed() {
        val text = document("[00:01]Text").replace("[al:Album]", "[al:]")
        for (entry in listOf(JSONObject(), JSONObject().put("duration_secs", 0.0001),
            JSONObject().put("player_song_ids", JSONObject().put("com.apple.Music", "invalid")))) {
            val prepared = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf("a.lrc" to text),
                JSONObject().put("Artist|Title|", entry))))
            assertTrue(prepared.tracks.isEmpty())
            assertEquals(1, prepared.failed)
            assertEquals(0, prepared.skipped)
        }
        val noArtist = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf("a.lrc" to
            document("[00:01]Text").replace("[ar:Artist]", "[ar:]")))))
        assertTrue(noArtist.tracks.isEmpty())
        assertEquals(1, noArtist.failed)
    }

    @Test fun albumDurationOrPlatformIdEachProvideEnoughIdentityEvidence() {
        val cases = listOf("Album" to JSONObject(), "" to JSONObject().put("duration_secs", 180.0),
            "" to JSONObject().put("player_song_ids", JSONObject().put("com.apple.Music", "123")))
        for ((album, entry) in cases) {
            val prepared = LyrimuseBackupImporter.prepareBytes(listOf(archive(mapOf("a.lrc" to
                document("[00:01]Text").replace("[al:Album]", "[al:$album]")),
                JSONObject().put("Artist|Title|$album", entry))))
            assertEquals(1, prepared.tracks.size)
            assertEquals(0, prepared.failed)
        }
    }

    @Test fun weakGroupsDoNotBlockValidLyricsOrCredentials() {
        for (includeValid in listOf(false, true)) {
            val files = buildMap {
                put("weak.lrc", document("[00:01]Weak").replace("[al:Album]", "[al:]"))
                if (includeValid) put("valid.lrc", document("[00:01]Valid"))
            }
            val prepared = LyrimuseBackupImporter.prepareBytes(listOf(archive(files), config()))
            assertEquals(if (includeValid) 1 else 0, prepared.tracks.size)
            assertEquals(1, prepared.failed)
            assertTrue(prepared.hasConfig)
            assertEquals(LastFmCredentials("a".repeat(32), "secret", "session", "user"), prepared.credentials)
        }
    }

    @Test fun readsTheWholeCredentialTupleAndIgnoresOtherPreferences() {
        val prepared = LyrimuseBackupImporter.prepareBytes(listOf(config()))
        assertTrue(prepared.hasConfig)
        assertEquals(LastFmCredentials("a".repeat(32), "secret", "session", "user"), prepared.credentials)
        assertTrue(prepared.tracks.isEmpty())
        val together = LyrimuseBackupImporter.prepareBytes(listOf(config(), archive(mapOf("a.lrc" to document("[00:01]Text")))))
        assertEquals(1_000L, together.tracks.single().second.selected.parsedLines!!.single().startTime)
    }

    @Test fun incompleteCredentialsDoNotPreventPreparingLyrics() {
        val prepared = LyrimuseBackupImporter.prepareBytes(listOf(config(key = "xxx"), archive(mapOf("a.lrc" to document("[00:01]Text")))))
        assertNull(prepared.credentials)
        assertTrue(prepared.hasConfig)
        assertEquals(1, prepared.tracks.size)
        assertNull(LyrimuseBackupImporter.prepareBytes(listOf(config(session = null))).credentials)
    }

    @Test fun rejectsUnsupportedVersionsDuplicateKindsAndUnrelatedJson() {
        val lyrics = archive(emptyMap())
        assertThrows(IllegalArgumentException::class.java) { LyrimuseBackupImporter.prepareBytes(listOf(lyrics, lyrics)) }
        assertThrows(IllegalArgumentException::class.java) { LyrimuseBackupImporter.prepareBytes(listOf(config(), config())) }
        assertThrows(IllegalArgumentException::class.java) { LyrimuseBackupImporter.prepareBytes(listOf("{\"v\":3,\"files\":{}}".toByteArray())) }
        assertThrows(IllegalStateException::class.java) { LyrimuseBackupImporter.prepareBytes(listOf("{\"categories\":{}}".toByteArray())) }
    }

    @Test fun preparationHonorsCancellation() {
        assertThrows(CancellationException::class.java) {
            LyrimuseBackupImporter.prepareBytes(listOf(archive(emptyMap()))) { throw CancellationException("cancel") }
        }
        assertThrows(CancellationException::class.java) {
            LyrimuseBackupImporter.readBounded(byteArrayOf(1).inputStream()) { throw CancellationException("cancel") }
        }
    }

    @Test fun externalSampleMatchesInventoryWhenProvided() {
        val directory = System.getProperty("lyrimuse.sample.dir")?.let(::File)
        assumeTrue("External sample not provided", directory?.isDirectory == true)
        val files = directory!!.listFiles()!!.filter { it.name.endsWith(".json.z") || it.name.contains("-Config-") }
        val prepared = LyrimuseBackupImporter.prepareBytes(files.map { it.readBytes() })
        assertEquals(192, prepared.tracks.size)
        assertEquals(18, prepared.skipped)
        assertEquals(0, prepared.failed)
        val results = prepared.tracks.map { it.second.selected }
        assertEquals(185, results.count { it.hasSyllable })
        assertEquals(157, results.count { !it.translationLyrics.isNullOrBlank() })
        assertEquals(171, results.count { !it.romanLyrics.isNullOrBlank() })
        assertNull(prepared.credentials)
    }
}
