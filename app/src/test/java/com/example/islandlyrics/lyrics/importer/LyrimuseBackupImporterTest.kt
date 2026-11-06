package com.example.islandlyrics.lyrics.importer

import com.example.islandlyrics.integration.lastfm.LastFmCredentials
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import com.example.islandlyrics.lyrics.online.parser.OnlineLyricSidecarMerger
import com.example.islandlyrics.rules.ParserRule
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

class LyrimuseBackupImporterTest {
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
