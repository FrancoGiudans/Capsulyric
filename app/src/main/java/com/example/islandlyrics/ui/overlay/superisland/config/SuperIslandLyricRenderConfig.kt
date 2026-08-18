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

package com.example.islandlyrics.ui.overlay.superisland.config
internal data class SuperIslandLyricRenderConfig(
    val lyricMode: String,
    val fullLyricShowLeftCover: Boolean,
    val standardLyricShowLeftCover: Boolean,
    val rightTextWeight: Int,
    val leftWithCoverTextWeight: Int,
    val leftNoCoverTextWeight: Int
)

internal fun SuperIslandPreferencesCache.toLyricRenderConfig(): SuperIslandLyricRenderConfig {
    return SuperIslandLyricRenderConfig(
        lyricMode = lyricMode,
        fullLyricShowLeftCover = fullLyricShowLeftCover,
        standardLyricShowLeftCover = standardLyricShowLeftCover,
        rightTextWeight = rightTextWeight,
        leftWithCoverTextWeight = leftWithCoverTextWeight,
        leftNoCoverTextWeight = leftNoCoverTextWeight
    )
}
