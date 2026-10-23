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

package com.example.islandlyrics.ui.overlay.superisland.render

import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandLyricLayout
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandLyricRenderConfig
import com.xzakota.hyper.notification.island.model.BigIslandArea
import com.xzakota.hyper.notification.island.model.TextInfo

internal object SuperIslandBigAreaRenderer {
    fun BigIslandArea.applyDualLineLyrics(
        config: SuperIslandLyricRenderConfig,
        dualLineText: SuperIslandDualLineText,
        showHighlightColor: Boolean
    ) {
        val leftText = SuperIslandLyricLayout.takeByWeight(
            dualLineText.primary,
            config.leftNoCoverTextWeight
        ).ifEmpty { "♪" }
        val rightText = SuperIslandLyricLayout.takeByWeight(
            dualLineText.secondary ?: dualLineText.primary,
            config.rightTextWeight
        ).ifEmpty { "♪" }

        applyTwoColumnText(
            showLeftCover = false,
            islandKey = null,
            leftText = leftText,
            rightText = rightText,
            showHighlightColor = showHighlightColor,
            narrowLeftFont = false
        )
    }

    fun BigIslandArea.applyLyrics(
        config: SuperIslandLyricRenderConfig,
        preferMetadataLayout: Boolean,
        isTimingGapPlaceholder: Boolean,
        fullLyric: String,
        displayLyric: String,
        titleWithArtist: String,
        islandKey: String?,
        showHighlightColor: Boolean,
        title: String = "",
        artist: String = "",
        fullLyricUsesDedicatedRightColumn: Boolean = false,
        rightProgressRingEnabled: Boolean = false,
        progressPercent: Int = 0,
        progressReachColor: String = "#757575"
    ) {
        if (config.lyricMode == "full") {
            applyFullMode(
                config = config,
                preferMetadataLayout = preferMetadataLayout,
                isTimingGapPlaceholder = isTimingGapPlaceholder,
                fullLyric = fullLyric,
                displayLyric = displayLyric,
                islandKey = islandKey,
                showHighlightColor = showHighlightColor,
                title = title,
                artist = artist,
                fullLyricUsesDedicatedRightColumn = fullLyricUsesDedicatedRightColumn,
                rightProgressRingEnabled = rightProgressRingEnabled,
                progressPercent = progressPercent,
                progressReachColor = progressReachColor
            )
            return
        }

        val showLeftCover = config.standardLyricShowLeftCover && islandKey != null
        if (preferMetadataLayout) {
            applyMetadataLayout(
                config = config,
                showLeftCover = showLeftCover,
                islandKey = islandKey,
                showHighlightColor = showHighlightColor,
                title = title,
                artist = artist,
                rightProgressRingEnabled = rightProgressRingEnabled,
                progressPercent = progressPercent,
                progressReachColor = progressReachColor
            )
            return
        }

        val leftTitle = SuperIslandLyricLayout.takeByWeight(
            titleWithArtist.ifBlank { "♪" },
            if (showLeftCover) config.leftWithCoverTextWeight else config.leftNoCoverTextWeight
        ).ifEmpty { "♪" }
        val rightLyric = SuperIslandLyricLayout.takeByWeight(
            displayLyric.ifBlank { "♪" },
            config.rightTextWeight
        ).ifEmpty { "♪" }

        applyTwoColumnText(
            showLeftCover = showLeftCover,
            islandKey = islandKey,
            leftText = leftTitle,
            rightText = rightLyric,
            showHighlightColor = showHighlightColor,
            narrowLeftFont = null,
            rightProgressRingEnabled = rightProgressRingEnabled,
            progressPercent = progressPercent,
            progressReachColor = progressReachColor
        )
    }

    private fun BigIslandArea.applyFullMode(
        config: SuperIslandLyricRenderConfig,
        preferMetadataLayout: Boolean,
        isTimingGapPlaceholder: Boolean,
        fullLyric: String,
        displayLyric: String,
        islandKey: String?,
        showHighlightColor: Boolean,
        title: String,
        artist: String,
        fullLyricUsesDedicatedRightColumn: Boolean,
        rightProgressRingEnabled: Boolean,
        progressPercent: Int,
        progressReachColor: String
    ) {
        val resolvedLyric = sequenceOf(displayLyric, fullLyric)
            .firstOrNull { !SuperIslandTextResolver.isPlaceholder(it) }
            .orEmpty()
        val hasLyric = resolvedLyric.isNotBlank()
        val showLeftCover = config.fullLyricShowLeftCover && islandKey != null

        val shouldShowMetadataFallback = preferMetadataLayout || (!hasLyric && !isTimingGapPlaceholder)
        if (shouldShowMetadataFallback) {
            applyMetadataLayout(
                config = config,
                showLeftCover = showLeftCover,
                islandKey = islandKey,
                showHighlightColor = showHighlightColor,
                title = title,
                artist = artist,
                rightProgressRingEnabled = rightProgressRingEnabled,
                progressPercent = progressPercent,
                progressReachColor = progressReachColor
            )
            return
        }

        val split = SuperIslandLyricLayout.splitFullLyric(
            text = resolvedLyric,
            showLeftCover = showLeftCover,
            leftMaxWeight = if (showLeftCover) {
                config.leftWithCoverTextWeight
            } else {
                config.leftNoCoverTextWeight
            },
            rightMaxWeight = config.rightTextWeight
        )

        applyTwoColumnText(
            showLeftCover = showLeftCover,
            islandKey = islandKey,
            leftText = split.left.ifEmpty { "♪" },
            rightText = split.right.ifEmpty { "♪" },
            showHighlightColor = showHighlightColor,
            narrowLeftFont = false,
            useMatchingRightColumn = fullLyricUsesDedicatedRightColumn,
            rightProgressRingEnabled = rightProgressRingEnabled,
            progressPercent = progressPercent,
            progressReachColor = progressReachColor
        )
    }

    private fun BigIslandArea.applyMetadataLayout(
        config: SuperIslandLyricRenderConfig,
        showLeftCover: Boolean,
        islandKey: String?,
        showHighlightColor: Boolean,
        title: String,
        artist: String,
        rightProgressRingEnabled: Boolean,
        progressPercent: Int,
        progressReachColor: String
    ) {
        val leftText = SuperIslandLyricLayout.takeByWeight(
            title.ifBlank { "♪" },
            if (showLeftCover) config.leftWithCoverTextWeight else config.leftNoCoverTextWeight
        ).ifEmpty { "♪" }
        val rightText = SuperIslandLyricLayout.takeByWeight(
            artist.ifBlank { "♪" },
            config.rightTextWeight
        ).ifEmpty { "♪" }

        applyTwoColumnText(
            showLeftCover = showLeftCover,
            islandKey = islandKey,
            leftText = leftText,
            rightText = rightText,
            showHighlightColor = showHighlightColor,
            narrowLeftFont = false,
            rightProgressRingEnabled = rightProgressRingEnabled,
            progressPercent = progressPercent,
            progressReachColor = progressReachColor
        )
    }

    private fun BigIslandArea.applyTwoColumnText(
        showLeftCover: Boolean,
        islandKey: String?,
        leftText: String,
        rightText: String,
        showHighlightColor: Boolean,
        narrowLeftFont: Boolean?,
        useMatchingRightColumn: Boolean = false,
        rightProgressRingEnabled: Boolean = false,
        progressPercent: Int = 0,
        progressReachColor: String = "#757575"
    ) {
        imageTextInfoLeft {
            type = 1
            if (showLeftCover) {
                picInfo {
                    type = 1
                    pic = islandKey
                }
            }
            textInfo {
                this.title = leftText
                this.showHighlightColor = showHighlightColor
                if (narrowLeftFont != null) {
                    narrowFont = narrowLeftFont
                }
            }
        }
        if (rightProgressRingEnabled) {
            progressTextInfo {
                textInfo {
                    title = rightText
                    this.showHighlightColor = showHighlightColor
                    narrowFont = false
                }
                progressInfo {
                    isCCW = true
                    progress = progressPercent.coerceIn(0, 100)
                    colorReach = progressReachColor
                    colorUnReach = "#333333"
                }
            }
        } else if (useMatchingRightColumn) {
            imageTextInfoRight {
                type = 2
                textInfo {
                    this.title = rightText
                    this.showHighlightColor = showHighlightColor
                    narrowFont = false
                }
            }
        } else {
            this.textInfo = TextInfo().apply {
                this.title = rightText
                this.showHighlightColor = showHighlightColor
                narrowFont = false
            }
        }
    }
}
