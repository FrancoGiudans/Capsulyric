package com.example.islandlyrics.lyrics.online.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppleMusicIsrcTest {

    @Test
    fun normalizesCompactAndHyphenatedForms() {
        assertEquals("TWK970500903", AppleMusicIsrc.normalize("tw-k97-05-00903"))
        assertEquals("TWK970200308", AppleMusicIsrc.normalize(" TWK970200308 "))
    }

    @Test
    fun rejectsMalformedValues() {
        assertNull(AppleMusicIsrc.normalize("TWK97050090"))
        assertNull(AppleMusicIsrc.normalize("TWK-9705-00903-extra"))
        assertNull(AppleMusicIsrc.normalize(""))
    }
}
