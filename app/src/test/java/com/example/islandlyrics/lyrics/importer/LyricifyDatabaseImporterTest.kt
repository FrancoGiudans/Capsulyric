package com.example.islandlyrics.lyrics.importer

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import com.example.islandlyrics.lyrics.source.sameLyricRequestTrack
import com.example.islandlyrics.lyrics.state.LyricRepository
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class LyricifyDatabaseImporterTest {
    private val original = result("Original")
    private val incoming = result("Imported")
    private val oldSelection = LyricifyImportSelection(listOf(original), original)
    private val newSelection = LyricifyImportSelection(listOf(incoming), incoming)

    private fun result(text: String) = OnlineLyricFetcher.LyricResult(
        api = "Test", lyrics = "[00:01.000]$text", hasSyllable = false, provider = OnlineLyricProvider.Netease,
        parsedLines = listOf(OnlineLyricFetcher.LyricLine(1_000L, 2_000L, text)), providerTrackId = "123"
    )

    @Test fun mapsForeignProviderNamesToExistingProviders() {
        assertEquals(OnlineLyricProvider.QQMusic, LyricifyDatabaseImporter.providerFor("QQMusic"))
        assertEquals(OnlineLyricProvider.Kugou, LyricifyDatabaseImporter.providerFor("KugouMusic"))
        assertEquals(OnlineLyricProvider.Netease, LyricifyDatabaseImporter.providerFor("NeteaseMusic"))
        assertEquals(OnlineLyricProvider.Lrclib, LyricifyDatabaseImporter.providerFor("LRCLIB"))
        assertNull(LyricifyDatabaseImporter.providerFor("UnknownProvider"))
    }

    @Test fun platformIdCanMatchLocalizedMetadata() {
        assertTrue(LyricifyDatabaseImporter.sameTrack(
            LyricifyTrackIdentity("标题", "歌手", providerIds = mapOf("netease" to "123")),
            LyricifyTrackIdentity("Title", "Artist", providerIds = mapOf("netease" to "123"))
        ))
    }

    @Test fun qqNumericIdsAndMidsHaveSeparateNamespaces() {
        val numeric = LyricifyDatabaseImporter.identityIds(OnlineLyricProvider.QQMusic, "462188")
        val mid = LyricifyDatabaseImporter.identityIds(OnlineLyricProvider.QQMusic, "0010BrWk2SucQr")
        assertEquals(mapOf("qq_music" to "462188"), numeric)
        assertEquals(mapOf("qq_music:mid" to "0010BrWk2SucQr"), mid)
        assertTrue(LyricifyDatabaseImporter.sameTrack(
            LyricifyTrackIdentity("标题", "歌手", providerIds = numeric + mid),
            LyricifyTrackIdentity("Title", "Artist", providerIds = mid)
        ))
    }

    @Test fun equalNumbersInDifferentPlatformsAreNotTheSameId() {
        assertFalse(LyricifyDatabaseImporter.sameTrack(
            LyricifyTrackIdentity("One", "A", providerIds = mapOf("netease" to "123")),
            LyricifyTrackIdentity("Two", "B", providerIds = mapOf("qq_music" to "123"))
        ))
    }

    @Test fun metadataCanMatchAcrossProvidersAndNearbyDurations() {
        assertTrue(LyricifyDatabaseImporter.sameTrack(
            LyricifyTrackIdentity(" Title ", "ARTIST", "Album", 180_000, mapOf("netease" to "123")),
            LyricifyTrackIdentity("title", "artist", "album", 182_000, mapOf("qq_music" to "456"))
        ))
    }

    @Test fun versionSuffixesAlbumsAndDurationsPreventFalseMerges() {
        val track = LyricifyTrackIdentity("Title", "Artist", "Album", 180_000)
        assertFalse(LyricifyDatabaseImporter.sameTrack(track, track.copy(title = "Title (Live)")))
        assertFalse(LyricifyDatabaseImporter.sameTrack(track, track.copy(album = "Live Album")))
        assertFalse(LyricifyDatabaseImporter.sameTrack(track, track.copy(durationMs = 240_000)))
    }

    @Test fun missingEvidenceDoesNotForceAMerge() {
        val track = LyricifyTrackIdentity("Title", "Artist")
        assertFalse(LyricifyDatabaseImporter.sameTrack(track, track))
    }

    @Test fun contradictoryPlatformIdsAreKeptSeparate() {
        val track = LyricifyTrackIdentity("Title", "Artist", "Album", 180_000, mapOf("netease" to "123"))
        assertFalse(LyricifyDatabaseImporter.sameTrack(track, track.copy(providerIds = mapOf("netease" to "456"))))
        assertFalse(LyricifyDatabaseImporter.sameTrack(
            track.copy(providerIds = mapOf("qq_music" to "123", "kugou" to "A")),
            track.copy(title = "Another song", providerIds = mapOf("qq_music" to "123", "kugou" to "B"))
        ))
    }

    @Test fun differentPlatformIdsProduceDifferentStorageKeys() {
        val first = LyricifyTrackIdentity("Title", "Artist", "Album", 180_000, mapOf("netease" to "123"))
        assertNotEquals(LyricifyDatabaseImporter.importedEntryId(first),
            LyricifyDatabaseImporter.importedEntryId(first.copy(providerIds = mapOf("netease" to "456"))))
    }

    @Test fun storageKeysIncludeTheIdNamespaceAndIgnoreMapOrder() {
        val first = LyricifyTrackIdentity("Title", "Artist", providerIds = linkedMapOf("netease" to "123", "qq_music" to "456"))
        assertEquals(LyricifyDatabaseImporter.importedEntryId(first), LyricifyDatabaseImporter.importedEntryId(
            first.copy(providerIds = linkedMapOf("qq_music" to "456", "netease" to "123"))))
        assertNotEquals(LyricifyDatabaseImporter.importedEntryId(first.copy(providerIds = mapOf("netease" to "123"))),
            LyricifyDatabaseImporter.importedEntryId(first.copy(providerIds = mapOf("qq_music" to "123"))))
    }

    @Test fun storageKeyFieldsCannotCollideThroughSeparators() {
        val first = LyricifyTrackIdentity("Title|Artist", "Name", "Album", 180_000)
        assertNotEquals(LyricifyDatabaseImporter.importedEntryId(first),
            LyricifyDatabaseImporter.importedEntryId(first.copy(title = "Title", artist = "Artist|Name")))
    }

    @Test fun mergingPartialAliasesRetainsTheOldQqId() {
        val old = mapOf("netease" to "123", "qq_music" to "456")
        assertEquals(old, LyricifyDatabaseImporter.mergeAliases(old, mapOf("netease" to "123")))
        assertEquals(old + ("qq_music:mid" to "001AbC"),
            LyricifyDatabaseImporter.mergeAliases(old, mapOf("qq_music:mid" to "001AbC")))
    }

    @Test fun contradictoryAliasesCannotSilentlyReplaceAnOldId() {
        val old = mapOf("netease" to "123", "qq_music" to "456")
        assertThrows(IllegalArgumentException::class.java) {
            LyricifyDatabaseImporter.mergeAliases(old, mapOf("netease" to "123", "qq_music" to "789"))
        }
        assertEquals("456", old["qq_music"])
    }

    @Test fun metadataEnrichmentDoesNotInvalidateTheLyricRequest() {
        val first = LyricRepository.MediaInfo("Title", "Artist", "player", 0L)
        assertTrue(sameLyricRequestTrack(first, first.copy(duration = 180_000L, album = "Album", mediaId = "123")))
        assertFalse(sameLyricRequestTrack(first, first.copy(title = "Another song")))
        assertFalse(sameLyricRequestTrack(first, first.copy(packageName = "another player")))
        assertFalse(sameLyricRequestTrack(first, null))
    }

    @Test fun databaseCopyStopsBeforeWritingBeyondTheLimit() {
        var remaining = LyricifyDatabaseImporter.MAX_DATABASE_BYTES + 1
        val input = object : InputStream() {
            override fun read(): Int = if (remaining-- > 0) 0 else -1
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (remaining <= 0) return -1
                val count = minOf(length.toLong(), remaining).toInt()
                remaining -= count
                return count
            }
        }
        var written = 0L
        val output = object : OutputStream() {
            override fun write(value: Int) { written++ }
            override fun write(buffer: ByteArray, offset: Int, length: Int) { written += length }
        }
        assertThrows(LyricifyDatabaseImporter.LimitExceeded::class.java) {
            LyricifyDatabaseImporter.copyDatabase(input, output)
        }
        assertEquals(LyricifyDatabaseImporter.MAX_DATABASE_BYTES, written)
    }

    @Test fun databaseCopyHonorsCancellation() {
        var checked = 0
        val input = object : InputStream() { override fun read(): Int = 0 }
        assertThrows(CancellationException::class.java) {
            LyricifyDatabaseImporter.copyDatabase(input, object : OutputStream() { override fun write(value: Int) {} }) {
                if (++checked == 3) throw CancellationException("cancelled")
            }
        }
        assertEquals(3, checked)
    }

    @Test fun oversizedAndDeeplyNestedPayloadsAreRejectedBeforeParsing() {
        assertThrows(LyricifyDatabaseImporter.LimitExceeded::class.java) {
            LyricifyDatabaseImporter.validatePayload("a".repeat(LyricifyDatabaseImporter.MAX_PAYLOAD_BYTES.toInt() + 1))
        }
        assertThrows(LyricifyDatabaseImporter.LimitExceeded::class.java) {
            LyricifyDatabaseImporter.validatePayload("[".repeat(65) + "]".repeat(65))
        }
        LyricifyDatabaseImporter.validatePayload("{\"text\":\"" + "[".repeat(100) + "\"}")
    }

    @Test fun excessiveTimingElementsAreRejectedBeforeTheParserRuns() {
        assertThrows(LyricifyDatabaseImporter.LimitExceeded::class.java) {
            LyricifyDatabaseImporter.parseDocument("Lrc", "[00:01]word\n".repeat(25_001))
        }
    }

    @Test fun payloadBudgetsAcceptTheBoundaryAndRejectTheNextByte() {
        for (limit in listOf(LyricifyDatabaseImporter.MAX_TRACK_PAYLOAD_BYTES,
            LyricifyDatabaseImporter.MAX_TOTAL_PAYLOAD_BYTES, LyricifyDatabaseImporter.MAX_TEMPORARY_BYTES)) {
            LyricifyDatabaseImporter.checkSize(limit, limit, "test")
            assertThrows(LyricifyDatabaseImporter.LimitExceeded::class.java) { LyricifyDatabaseImporter.checkSize(limit + 1, limit, "test") }
        }
    }

    @Test fun overwriteChangesTheSelectedLyrics() {
        val selection = LyricifyDatabaseImporter.resolveSelection(oldSelection, newSelection, LyricifyImportMode.OVERWRITE)!!
        assertEquals(incoming, selection.selected)
    }

    @Test fun mergeKeepsSelectionAndBothVersionsOfTheSameProviderAndId() {
        val selection = LyricifyDatabaseImporter.resolveSelection(oldSelection, newSelection, LyricifyImportMode.MERGE)!!
        assertEquals(original, selection.selected)
        assertEquals(listOf(original, incoming), selection.candidates)
    }

    @Test fun skipLeavesAConflictingSongUntouched() {
        assertNull(LyricifyDatabaseImporter.resolveSelection(oldSelection, newSelection, LyricifyImportMode.SKIP))
    }

    @Test fun allModesImportSongsWithoutAnExistingSelection() {
        for (mode in LyricifyImportMode.entries) {
            assertEquals(newSelection, LyricifyDatabaseImporter.resolveSelection(null, newSelection, mode))
        }
    }

    @Test fun repeatedMergeDoesNotDuplicateCandidates() {
        val first = LyricifyDatabaseImporter.resolveSelection(oldSelection, newSelection, LyricifyImportMode.MERGE)
        val second = LyricifyDatabaseImporter.resolveSelection(first, newSelection, LyricifyImportMode.MERGE)!!
        assertEquals(2, second.candidates.size)
        assertEquals(original, second.selected)
    }

    @Test fun importsLrcWithoutFractionalSeconds() {
        val lines = LyricifyDatabaseImporter.parseDocument("Lrc", "[0:01]Hello\n[00:02.5]World\n[00:03:10]Again")
        assertEquals(listOf(1_000L, 2_500L, 3_100L), lines.map { it.startTime })
        assertEquals(listOf("Hello", "World", "Again"), lines.map { it.text })
    }

    @Test fun reusesWordLevelParsers() {
        val documents = mapOf(
            "Qrc" to "[1000,1000]你(1000,500)好(1500,500)",
            "Krc" to "[1000,1000]<0,500,0>你<500,500,0>好",
            "Yrc" to "[1000,1000](1000,500,0)你(1500,500,0)好"
        )
        for ((format, text) in documents) {
            val line = LyricifyDatabaseImporter.parseDocument(format, text).single()
            assertEquals(format, "你好", line.text)
            assertEquals(format, listOf(1_000L, 1_500L), line.syllables!!.map { it.startTime })
        }
    }

    @Test fun shiftsLineAndWordTimingTogether() {
        val line = OnlineLyricFetcher.LyricLine(1_000, 2_000, "Word", listOf(OnlineLyricFetcher.SyllableInfo(1_000, 2_000, "Word")))
        val shifted = LyricifyDatabaseImporter.shiftLines(listOf(line), 1_500).single()
        assertEquals(2_500L, shifted.startTime)
        assertEquals(3_500L, shifted.endTime)
        assertEquals(2_500L, shifted.syllables!!.single().startTime)
        assertEquals(3_500L, shifted.syllables!!.single().endTime)
    }
}
