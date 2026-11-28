package com.example.islandlyrics.ui.overlay.superisland.render

import org.junit.Assert.assertEquals
import org.junit.Test

class SuperIslandProgressSanitizerTest {

    @Test
    fun negativeAndOutOfRangeProgressIsClampedForHyperOsFocusProtocol() {
        assertEquals(0, sanitizeSuperIslandProgress(-1))
        assertEquals(0, sanitizeSuperIslandProgress(-100))
        assertEquals(0, sanitizeSuperIslandProgress(0))
        assertEquals(50, sanitizeSuperIslandProgress(50))
        assertEquals(100, sanitizeSuperIslandProgress(100))
        assertEquals(100, sanitizeSuperIslandProgress(120))
    }
}
