/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.example.islandlyrics.ui.adaptive

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppWindowLayoutTest {

    @Test
    fun phonePortrait_isCompactPortrait() {
        val info = classifyAppWindowLayout(widthDp = 412.dp, heightDp = 915.dp)

        assertEquals(WindowSizeTier.CompactPortrait, info.tier)
        assertFalse(info.isLandscape)
        assertFalse(info.useNavigationRail)
        assertFalse(info.useCompactTopBar)
        assertFalse(info.useMasterDetail)
        assertFalse(info.useTwoPanePreview)
        assertEquals(Dp.Unspecified, info.maxReadableWidth)
    }

    @Test
    fun phoneLandscape_isCompactLandscapeEvenWhenWidthExceeds840Dp() {
        val cases = listOf(
            915.dp to 412.dp,
            780.dp to 360.dp,
            640.dp to 360.dp,
        )

        cases.forEach { (width, height) ->
            val info = classifyAppWindowLayout(widthDp = width, heightDp = height)
            assertEquals(WindowSizeTier.CompactLandscape, info.tier)
            assertTrue(info.isLandscape)
            assertTrue(info.isShortHeight)
            assertTrue(info.useNavigationRail)
            assertTrue(info.useCompactTopBar)
            assertFalse(info.useMasterDetail)
            assertTrue(info.useTwoPanePreview)
        }
    }

    @Test
    fun foldableUnfolded_isMedium() {
        val info = classifyAppWindowLayout(widthDp = 700.dp, heightDp = 840.dp)

        assertEquals(WindowSizeTier.Medium, info.tier)
        assertFalse(info.isLandscape)
        assertTrue(info.useNavigationRail)
        assertFalse(info.useCompactTopBar)
        assertFalse(info.useMasterDetail)
        assertFalse(info.useTwoPanePreview)
        assertTrue(info.showNavigationRailToggle)
        assertFalse(info.expandNavigationRailByDefault)
        assertEquals(680.dp, info.maxReadableWidth)
    }

    @Test
    fun foldableSquare720Dp_enablesTwoPanePreviewWithoutMasterDetail() {
        val info = classifyAppWindowLayout(widthDp = 720.dp, heightDp = 760.dp)

        assertEquals(WindowSizeTier.Medium, info.tier)
        assertTrue(info.useTwoPanePreview)
        assertFalse(info.useMasterDetail)
    }

    @Test
    fun tabletLandscape_isExpanded() {
        val cases = listOf(
            1024.dp to 640.dp,
            840.dp to 540.dp,
        )

        cases.forEach { (width, height) ->
            val info = classifyAppWindowLayout(widthDp = width, heightDp = height)
            assertEquals(WindowSizeTier.Expanded, info.tier)
            assertTrue(info.useNavigationRail)
            assertFalse(info.useCompactTopBar)
            assertTrue(info.useMasterDetail)
            assertTrue(info.useTwoPanePreview)
            assertTrue(info.showNavigationRailToggle)
            assertFalse(info.expandNavigationRailByDefault)
        }
    }

    @Test
    fun largeTabletOrDesktop_isExtraLargeWithExpandedRail() {
        val info = classifyAppWindowLayout(widthDp = 1280.dp, heightDp = 800.dp)

        assertEquals(WindowSizeTier.ExtraLarge, info.tier)
        assertTrue(info.useNavigationRail)
        assertTrue(info.useMasterDetail)
        assertTrue(info.useTwoPanePreview)
        assertTrue(info.showNavigationRailToggle)
        assertTrue(info.expandNavigationRailByDefault)
        assertEquals(760.dp, info.maxReadableWidth)
    }

    @Test
    fun narrowSplitScreen_isCompactPortrait() {
        val info = classifyAppWindowLayout(widthDp = 450.dp, heightDp = 800.dp)

        assertEquals(WindowSizeTier.CompactPortrait, info.tier)
        assertFalse(info.useNavigationRail)
        assertFalse(info.useMasterDetail)
    }
}
