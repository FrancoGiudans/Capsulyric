@file:Suppress("DEPRECATION")

package com.example.islandlyrics.lyrics.source

import com.example.islandlyrics.lyrics.state.LyricSidecarStore
import com.hchen.superlyricapi.LyricView
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricLine
import com.hchen.superlyricapi.model.Line
import com.hchen.superlyricapi.model.Lyric
import com.hchen.superlyricapi.model.Word
import org.junit.Assert.*
import org.junit.Test

class SuperLyricSidecarPolicyTest {
    @Test fun switchesFilterLegacyAndV4LineAndFullDocumentWithoutChangingTiming() {
        val legacy = SuperLyricMapper.legacy("player", SuperLyricData()
            .setLyric(SuperLyricLine("text", 1000, 2000).setTranslation("translation").setSecondary("roma")))
        val modern = SuperLyricMapper.modern(LyricView(Lyric("doc", "title", "artist", "album", 5000,
            listOf(Line("text", 1000, 2000, listOf(Word("text", 1000, 2000)), "translation", "roma"))),
            0, 1500, 1f, null, "player"))
        for (update in listOf(legacy, modern)) {
            for (translation in listOf(false, true)) for (romanization in listOf(false, true)) {
                val result = update.withSidecarPolicy(translation, romanization)
                assertEquals(if (translation) "translation" else null, result.translation)
                assertEquals(if (romanization) "roma" else null, result.roma)
                val row = result.timelineLines().single()
                assertEquals(result.translation, row.translation)
                assertEquals(result.roma, row.roma)
                assertEquals(1000L, row.startTime)
                assertEquals(2000L, row.endTime)
                assertEquals(update.currentLine!!.syllables, row.syllables)
                assertEquals(update.hasFullTimeline, result.hasFullTimeline)
            }
            assertEquals("translation", update.currentLine!!.translation)
            assertEquals("roma", update.currentLine.roma)
        }
    }

    @Test fun disabledFieldCannotReturnFromSidecarCacheButOtherFieldStillWorks() {
        val store = LyricSidecarStore()
        store.put("SuperLyric", "text", "translation", "roma")
        val translationOff = store.find("SuperLyric", "text", receiveTranslation = false)!!
        assertNull(translationOff.translation)
        assertEquals("roma", translationOff.roma)
        val romaOff = store.find("SuperLyric", "text", receiveRomanization = false)!!
        assertEquals("translation", romaOff.translation)
        assertNull(romaOff.roma)
        val bothOff = store.find("SuperLyric", "text", false, false)!!
        assertNull(bothOff.translation)
        assertNull(bothOff.roma)
        assertEquals("translation", store.find("SuperLyric", "text")!!.translation)
    }
}
