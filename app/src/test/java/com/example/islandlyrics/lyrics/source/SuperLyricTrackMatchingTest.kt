package com.example.islandlyrics.lyrics.source

import com.hchen.superlyricapi.LyricView
import com.hchen.superlyricapi.model.Line
import com.hchen.superlyricapi.model.Lyric
import org.junit.Assert.*
import org.junit.Test

class SuperLyricTrackMatchingTest {
    private val publisher = "com.apple.android.music"
    private val track = SuperLyricTrack(publisher, "Zankyosanka", "From THE FIRST TAKE", "1657309956")

    private fun update() = SuperLyricMapper.modern(LyricView(
        Lyric("1657309956", "Zankyosanka - From THE FIRST TAKE", "Aimer", "", 184952,
            listOf(
                16548L to 22155L, 22165L to 27534L, 27544L to 33263L, 33273L to 38301L,
                38311L to 44022L, 44032L to 50409L, 50419L to 55480L, 55490L to 61982L,
                61992L to 67788L, 67798L to 69977L, 69987L to 71350L, 82433L to 88088L,
                88098L to 93720L, 93730L to 99427L, 99437L to 104183L, 104493L to 113518L,
                127540L to 134356L, 137584L to 143915L, 143925L to 148909L,
                148919L to 155343L, 155353L to 161150L, 161260L to 163264L, 163274L to 164608L
            ).mapIndexed { i, (start, end) -> Line("line $i", start, end) }
        ), 13, 93731, 1f, null, publisher
    ))

    @Test fun reportedFullDocumentMatchesSameIdDespiteRuleSplitTitleAndArtist() {
        val update = update()
        assertTrue(update.hasFullTimeline)
        assertEquals(23, update.timelineLines().size)
        assertEquals("line 13", update.currentLine!!.text)
        assertEquals(93730L, update.currentLine.startTime)
        assertEquals(99427L, update.currentLine.endTime)
        assertTrue(SuperLyricMapper.matches(update, track))
    }

    @Test fun sameIdFromDifferentPublisherCannotOverrideSession() {
        assertFalse(SuperLyricMapper.matches(update().copy(publisher = "other.player"), track))
    }

    @Test fun matchingIdWithoutExplicitPublisherDoesNotOverrideConflictingMetadata() {
        assertFalse(SuperLyricMapper.matches(update().copy(publisher = null), track))
        assertFalse(SuperLyricMapper.matches(update().copy(publisher = ""), track))
    }

    @Test fun missingOrDifferentIdsDoNotBypassConflictingMetadata() {
        assertFalse(SuperLyricMapper.matches(update().copy(documentId = "different"), track))
        assertFalse(SuperLyricMapper.matches(update().copy(documentId = null), track))
        assertFalse(SuperLyricMapper.matches(update(), track.copy(mediaId = "")))
    }

    @Test fun differentIdentifierNamespacesStillPermitExistingMetadataMatch() {
        val update = update()
        assertTrue(SuperLyricMapper.matches(update, track.copy(
            title = update.title, artist = update.artist, mediaId = "different-namespace"
        )))
    }
}
